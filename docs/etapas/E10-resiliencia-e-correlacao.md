# E10 - Resiliencia das dependencias e correlacao

## Entrega

Limites HTTP explicitos, circuit breaker na fronteira remota real da consulta e correlacao desde entrada HTTP ate comandos/eventos/invalidacao, sem alterar idempotencia, fila, retry ou contratos de negocio. Decisao e alternativas: [ADR-013](../adr/ADR-013-resiliencia-e-correlacao.md).

## Inventario de limites e tentativas

| Fronteira | Limites vigentes | Tentativas e resposta a falha |
|---|---|---|
| Consulta -> registro HTTP | Conexao 2 s, leitura 3 s; sem redirect | Uma chamada logica por miss; 404 permite fallback; outros erros/circuito aberto -> 503 |
| Consulta -> emissor HTTP | Conexao 2 s, leitura 3 s; sem redirect | Uma chamada logica somente depois de 404 do registro; ausencia -> 404; falha -> 503 |
| Outbox de comandos -> Artemis | URL padrao `callTimeout=2000`, `blockOnDurableSend=true`, `initialConnectAttempts=1`, `reconnectAttempts=0` | Tres tentativas totais da outbox, minimos 1 s/5 s; esgotamento no H2 |
| Outbox de eventos -> Artemis | Mesma URL/politica JMS; commit persistente | Tres tentativas totais da outbox, minimos 1 s/5 s; esgotamento no H2 |
| Artemis -> registro / consulta | Sessao transacional; conexao recuperada a cada 2 s se indisponivel | Tres entregas por mensagem, minimos 1 s/5 s; erro permanente/esgotamento -> quarentena |
| Registro -> registradora simulada | Chamada Java/ledger H2, nao HTTP | Sem retry aninhado; indisponibilidade reverte entrega JMS; rejeicao confirma negocio |
| Adaptador HTTP antigo de comandos (inativo) | Conexao 2 s, leitura 5 s historicos | Sem bean/fallback; continua apenas em testes de regressao |

Limites de conexao/leitura sao por operacao de socket, **nao deadline global** incluindo DNS, travas ou resposta que entrega dados continuamente. Demonstracao e local. O maximo de fontes por miss e duas, sequenciais e apenas quando registro retorna 404. Nenhuma anotacao de retry, sleep ou tentativa adicional de aplicacao foi adicionada. Nao somar retry do produtor ao processamento: confirmacao JMS e registro de negocio sao fronteiras diferentes.

Timeout de 5 s de registradora externa continua premissa da Sessao 4; nao alegar implementacao de HTTP onde existe uma simulacao local. `BROKER_URL` customizada substitui a URL inteira: preservar seus limites ao mudar porta.

## Circuit breaker

[Cliente REST](../../boleto-consulta/src/main/java/br/com/caixa/cobranca/consulta/clients/RestBoletoFonteClient.java) usa Resilience4j 2.2.0 diretamente. Dois circuitos independentes, um por fonte/instancia:

1. CLOSED: janela das ultimas tres chamadas, minimo tres, limiar 100% de falhas tecnicas.
2. OPEN: nenhuma chamada a fonte durante 10 s; API responde 503 e nao aciona fallback indevido.
3. HALF_OPEN: primeira chamada depois da espera obtem uma unica permissao de sonda. Outras sao rejeitadas enquanto a sonda executa.
4. Sucesso, inclusive 404 valido, fecha; erro tecnico reabre por outros 10 s.

404 ainda e ausencia, nao boleto existente. `REJEITADO` em 200 e resultado de negocio, nao indisponibilidade. Resposta vazia/malformada/inconsistente e erro tecnico contabilizado, nao sucesso ficticio. Cache hit valido nao depende do circuito; erros nunca preenchem cache.

Circuito e memoria local, nao persistido. Logs mostram transicoes; nao ha novo endpoint administrativo. Este mecanismo nao garante disponibilidade da fonte nem substitui acesso/auditoria E11.

## Correlacao

O [modulo infraestrutura-http](../../infraestrutura-http/) e usado pelos tres servicos, sem JPA/negocio. `shared-contracts` permanece independente de Spring.

- Header opcional `X-Correlation-ID`: ausente gera UUID v4; informado exige unico UUID v4/variante 2 canonico, minusculo, sem espacos/lista. Invalido -> 400 antes do dominio e id seguro gerado; nao refletir valor invalido.
- Resposta inclui o id validado/gerado em sucesso, ausencia, erro e cache hit. Corpos de negocio anteriores preservados. Erros de correlacao e erros da consulta contem tambem `correlation_id`.
- Contexto por thread/MDC com escopo fechado em erro/sucesso; thread de outro pedido nao herda o valor.
- Emissao persiste correlacao em comando + evento. Scheduler usa o valor persistido, nao contexto HTTP encerrado.
- Header JMS do comando corresponde ao JSON; registro reabre contexto, persiste a mesma correlacao no evento final; publicador de eventos a repassa ao JMS; consumidor de cache reabre contexto para invalidar.
- Header JMS divergente e erro permanente/quarentena. Header ausente e tolerado para legado, mas correlacao do envelope deve ser valida.
- Consulta encaminha id do pedido atual aos GETs internos. Cache guarda boleto, nao correlacao do pedido que o preencheu.

Exemplo de requisicao PowerShell, com servicos iniciados pelo [README](../../README.md):

```powershell
$correlation = [guid]::NewGuid().ToString()
curl.exe -i "http://127.0.0.1:8083/boletos/UUID-DO-BOLETO" -H "X-Correlation-ID: $correlation"
```

Substituir `UUID-DO-BOLETO` por identificador real. Header de resposta deve coincidir. Cada consulta pode ter novo id; isso nao altera o id persistido no fluxo original de emissao.

## Idempotencia e migracao

Comando acrescenta `correlation_id`, sem alterar identificador, aceite ou solicitacao. JSON legado sem campo usa UUID do boleto; nao ha migracao de banco nem regravacao de outboxes antigas. Publicadores/consumidores devem ser atualizados juntos para a demonstracao; nao prometer compatibilidade de correlacao ponta a ponta com binarios anteriores.

Fingerprint convenio/controle/conteudo da emissao nao mudou. Registro e ledger comparam negocio excluindo somente `correlation_id`, inclusive com JSON legado. JSON persistido conserva metadado original. Repeticao HTTP responde com correlacao do pedido atual, mas nao cria outro comando/evento nem substitui correlacao da intencao existente.

Finais legados/eventos ja publicados permanecem intactos. Sem backfill, reinicio de isolados ou replay automatico. O cache e local; permanece a restricao de uma instancia de cada servico da E9.

## Evidencias

Na raiz:

```powershell
mvn -B clean verify
```

Resultado: **BUILD SUCCESS, 265 testes aprovados**, zero falhas/erros/ignorados: 19 infraestrutura-http, 12 broker, 17 eventos-outbox, 97 emissor, 69 registro, 50 consulta Surefire e 1 Failsafe. PACT e regressoes E1-E9 preservados.

| Teste | O que verifica |
|---|---|
| [CorrelacaoFilterTest](../../infraestrutura-http/src/test/java/br/com/caixa/cobranca/http/CorrelacaoFilterTest.java) | UUID gerado/validado, statuses 200/202/400/404/409/503, entradas invalidas/duplicadas, limpeza em excecao, escopo aninhado e isolamento entre threads |
| [ResilienciaFonteTest](../../boleto-consulta/src/test/java/br/com/caixa/cobranca/consulta/clients/ResilienciaFonteTest.java) | Limiar exato, ausencia de chamadas OPEN, meia-abertura/sonda unica e recuperacao com clock controlado, falha de sonda reabre, 404/rejeicao normais, erros de contrato/4xx/5xx sem retry, timeout real e redirect nao seguido, fallback/headers e cache hit com circuito aberto |
| [OutboxEntregaJmsTest](../../boleto-emissor/src/test/java/br/com/caixa/cobranca/emissor/integration/OutboxEntregaJmsTest.java) | POST real via MVC -> intencoes JPA -> comando JMS com mesma correlacao; reenvio com outro header preserva identidade/intencao |
| [EventosRegistroTest](../../boleto-registro/src/test/java/br/com/caixa/cobranca/registro/integration/EventosRegistroTest.java) | Correlacao distinta nao duplica efeito/evento; comando local e ledger legados sem campo continuam idempotentes |
| [RegistroEntregaJmsTest](../../boleto-registro/src/test/java/br/com/caixa/cobranca/registro/integration/RegistroEntregaJmsTest.java) | Comando JMS -> contexto do registro -> resultado/intencao -> evento JMS com mesmo id; limpeza do contexto; divergencia de header em quarentena |
| [ConsultaEventosTest](../../boleto-consulta/src/test/java/br/com/caixa/cobranca/consulta/integration/ConsultaEventosTest.java) | Evento -> contexto da invalidacao e limpeza apos entrega; divergencia de header nao invalida cache |
| [ConsultaBoletoControllerTest](../../boleto-consulta/src/test/java/br/com/caixa/cobranca/consulta/integration/ConsultaBoletoControllerTest.java) | Header atual inclusive cache hit e 503 com mesmo id no corpo; entrada invalida recusada antes de fontes |

Testes usam HTTP local real para timeout/redirect, mocks HTTP verificaveis para falhas exatas, Artemis/H2 reais para fronteiras persistentes. O clock do circuito e controlado apenas no teste com a implementacao Resilience4j fixada; producao usa API publica e tempo normal. Nao se declara essa matriz como nova demonstracao de quatro JVMs ou medicao de carga. PACT E6/E7 deve continuar preservado.

## Uso critico de IA e continuidade

IA auxiliou implementacao e testes. Pontos revisados: nao adicionar retries aos controles persistidos, nao transformar circuito aberto em 404/fallback, nao contar rejeicao como indisponibilidade, nao deixar 404 impedir recuperacao da sonda e nao incluir metadado de correlacao na identidade de negocio. Um modulo tecnico evita duplicacao do filtro e dependencias Spring no contrato.

Na revalidacao, o teste HTTP revelou uma corrida de observacao: o cliente podia expirar antes de a thread do servidor atualizar o contador de chamadas. Foi adicionada sincronizacao por latch da recepcao, mantendo a verificacao de `SocketTimeoutException`, tempo limitado e exatamente uma chamada; sem aumentar timeout de producao ou esconder retry.

Regressao limpa aprovada; E10 deve ter commit proprio, sem push, conforme orientacao do usuario para cada etapa. Proxima E11: identidade/autorizacao em todos os canais e auditoria; E12: operacao/replay/retencao. Jornadas finais E14 e fechamento E15 continuam pendentes.
