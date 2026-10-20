# E8 — Broker JVM, comandos e quarentena

## Entrega e limites

O transporte de comandos agora usa **Apache ActiveMQ Artemis 2.33.0**, Jakarta JMS e journal NIO, em processo Java 17 separado. Sem Docker, RabbitMQ ou fila em memoria isolada dentro de cada servico. Decisao: [ADR-011](../adr/ADR-011-broker-jvm-artemis.md).

- `registro.comandos`: endereco/fila ANYCAST duravel.
- `registro.quarentena`: endereco/fila ANYCAST duravel, sem consumidor/replay automatico.
- Mensagens persistentes e transacoes JMS locais; sincronizacao de journal habilitada.
- Destinos provisionados pelo broker, sem criacao/exclusao automatica.
- Emissor e registro usam o mesmo `BROKER_URL`; consulta ainda usa apenas HTTP para ler suas fontes.
- HTTP de comandos desativado por padrao. O adaptador HTTP deixou de ser bean; testes E4/E5 ficam como regressao historica, nao fallback de producao.
- Eventos de dominio e invalidacao do cache **continuam E9**. O teste de duas assinaturas MULTICAST duraveis valida a capacidade do broker, nao entrega esses eventos.

## O que uma confirmacao significa

1. `POST /boletos` confirma aceite + outbox no H2 e retorna 202, sem esperar o broker ou a registradora.
2. O dispatcher publica JSON persistente em transacao JMS. Verifica a existencia da fila, envia e aguarda commit JMS antes de marcar `entregue_em` e limpar o payload da outbox.
3. `entregue_em` agora significa **publicacao confirmada no broker**, nao boleto registrado.
4. O listener chama o servico de registro. O commit JPA do resultado/historico termina antes de retornar ao container, que so entao confirma a transacao JMS.
5. Nao ha XA: queda depois do commit JPA e antes do ack JMS pode repetir a entrega. UUID, comparacao do comando e ledger E7 impedem repetir o efeito de negocio.

Endereco/fila inexistente impede concluir a outbox. Se uma rota de comandos desaparece depois da verificacao do cliente, o broker encaminha a mensagem sem binding para a quarentena em vez de descartar silenciosamente. Confirmar armazenamento no broker, inclusive quarentena, nunca significa processamento de negocio concluido.

### Orcamentos separados

| Fronteira | Politica | Onde fica a falha final |
|---|---|---|
| Publicacao da outbox | Tres tentativas totais contabilizadas; esperas minimas 1 s/5 s; serializacao invalida e permanente | H2 do emissor, com payload preservado |
| Processamento no registro | Tres entregas do broker; rollback por falha tecnica; redelivery minimo 1 s/5 s | `registro.quarentena` |
| JSON/envelope/conflito invalido | Transferencia imediata, na mesma transacao JMS do recebimento | `registro.quarentena` |
| Rejeicao de negocio | Persiste `REJEITADO`, confirma consumo; sem retry tecnico | H2 do registro, nao DLQ |

Nao sao nove chamadas obrigatorias da registradora: o produtor retenta **publicacao**, nao processamento. Confirmacao perdida pode produzir duplicatas, absorvidas pela idempotencia. Como na E5, queda antes de persistir o contador da outbox pode acrescentar um envio; nao prometemos limite global absoluto de chamadas nem exactly-once.

O broker persiste o contador antes da entrega para nao reiniciar seu orcamento apos queda. Reconexao do listener recupera o canal, sem chamar o negocio fora das entregas do broker. A URL padrao limita tentativas de conexao do cliente e espera de chamadas a 2 s; nao ha loop oculto de republicacao JMS.

## Quarentena e dados

Erros permanentes preservam o corpo original, inclusive mensagem nao textual, e acrescentam `tipo_erro`, `fila_origem`, `mensagem_original`, `entregas`, `falha_em` e correlacao quando existente. Envio para quarentena e ack original usam a **mesma Session transacional**. Se o destino estiver ausente/sem fila ou o envio falhar, o recebimento e revertido.

No esgotamento tecnico e na ausencia de rota, o Artemis acrescenta seus metadados de origem, como `_AMQ_ORIG_ADDRESS`, `_AMQ_ORIG_QUEUE` e `_AMQ_ORIG_MESSAGE_ID`. Eles nao equivalem a uma trilha de auditoria completa nem armazenam o texto da excecao da registradora. Logs da aplicacao registram identificador/tipo/entrega, sem corpo ou mensagem livre da excecao. Os loggers `org.apache.activemq.audit` ficam desativados para nao expor o comando por logging generico do broker.

Fila, quarentena, H2 e ledger contem dados pessoais do comando. **Perfil local didatico, nao conformidade de producao:** broker somente em `127.0.0.1`, sem autenticacao/TLS nesta etapa; arquivos exigem acesso restrito. Protecao e auditoria sao E11; replay autorizado/auditado e politica de retencao de negocio sao E12. Nao abrir a porta para a rede nem editar os arquivos de journal.

## Execucao reproduzivel

Requer JDK 17, Maven e dependencias acessiveis na rede configurada. Na raiz do projeto:

```powershell
mvn -B clean verify
```

O build gera dois artefatos do broker: JAR normal, usado como dependencia de teste, e `cobranca-broker-0.0.1-SNAPSHOT-exec.jar`, executavel com dependencias. Nenhuma instalacao separada do Artemis e necessaria. Os tres servicos tambem geram seus JARs executaveis.

Abra quatro terminais **inicialmente na raiz do projeto**, seguindo [README](../../README.md):

1. Broker: entre em `cobranca-broker` e execute `java -jar .\target\cobranca-broker-0.0.1-SNAPSHOT-exec.jar`.
2. Registro: entre em `boleto-registro` e execute `java -jar .\target\boleto-registro-0.0.1-SNAPSHOT.jar --server.address=127.0.0.1`.
3. Emissor: entre em `boleto-emissor` e execute `java -jar .\target\boleto-emissor-0.0.1-SNAPSHOT.jar --server.address=127.0.0.1`.
4. Consulta: entre em `boleto-consulta` e execute `java -jar .\target\boleto-consulta-0.0.1-SNAPSHOT.jar --server.address=127.0.0.1`.

### Parametros, prontidao e parada

- Broker: porta padrao **61616**; dados relativos ao diretorio atual em `data\broker`. `--port=61617` e `--data=C:\caminho\broker` permitem mudar. Tambem aceita propriedades JVM `-Dbroker.port=...` / `-Dbroker.data=...`, antes de `-jar`. Argumentos normais prevalecem; porta/diretorio invalidos falham explicitamente.
- Emissor/registro: `BROKER_URL` padrao `tcp://127.0.0.1:61616?blockOnDurableSend=true&callTimeout=2000&initialConnectAttempts=1&reconnectAttempts=0`.
- Para porta alternativa, configure em **ambos os terminais**, antes de iniciar o servico:

```powershell
$env:BROKER_URL='tcp://127.0.0.1:61617?blockOnDurableSend=true&callTimeout=2000&initialConnectAttempts=1&reconnectAttempts=0'
```

- Emissor/registro mantem os H2 em `data` do proprio modulo se iniciados como acima. Nao executar de outro diretorio inadvertidamente: pode parecer que os dados sumiram quando o caminho relativo mudou.
- O broker so registra **“Broker pronto”** depois da ativacao; falha de ativacao encerra com erro, nao deixa um processo anunciado como pronto. Console e `data\broker\broker.log` guardam diagnostico. Rotacao do log tecnico: 5 MB/arquivo, historico de 7 dias e limite total de 50 MB; isso **nao** define retencao dos boletos/journal.
- `Get-NetTCPConnection -LocalPort 61616` ajuda a diagnosticar ocupacao, inclusive estado **Bound**, nao apenas Listen. Nao encerrar processos alheios para liberar porta; selecionar outra. Na validacao desta maquina foi necessario **61617**.
- Verifique a mensagem de prontidao e a porta, depois `curl.exe -i http://127.0.0.1:8081/actuator/health` e portas 8082/8083: HTTP 200 / `UP`. Health confirma dependencias, **nao** prova que uma mensagem ja terminou.
- Pare com **Ctrl+C** nos terminais dos servicos e, por ultimo, do broker. O shutdown hook encerra o servidor e libera o journal. Reinicie usando o **mesmo diretorio de dados**. Nao iniciar dois brokers no mesmo journal nem apagar dados para “corrigir” uma falha.

Avisos de ausencia de expiry address/DLQ da propria quarentena sao esperados: o produtor nao define expiracao e nao ha consumidor automatico da quarentena. Nao ha descarte por TTL configurado para os comandos.

## Migracao de E4/E5 sem apagar bancos

| Registro existente na outbox | Comportamento E8 |
|---|---|
| Pendente elegivel | Publicado via JMS com mesmo UUID/conteudo; preserva criacao, contadores e backoff acumulados |
| Em backoff | Aguarda o horario ja persistido |
| Isolado | Permanece isolado; nenhum reset/replay automatico |
| Ja entregue por HTTP | Nao republica; payload ja havia sido limpo |
| Pendente legado no limite | Politica existente isola; nao ganha mais tres tentativas ao trocar o transporte |

Nenhuma alteracao de schema/reset da outbox foi necessaria. `entregue_em` antigo permanece com seu significado historico de confirmacao HTTP do registro; novos valores representam confirmacao do broker. Nao preencher fatos retroativos nem alterar resultados finais.

`POST /interno/comandos/registro` retorna 404 por padrao; `registro.http-comandos.enabled=true` serve apenas para compatibilidade/testes historicos. Nao habilitar como fallback operacional. A leitura interna GET usada pelo PACT permanece HTTP.

## Evidencias executadas — 20/10/2026

### Automatizadas

`mvn -B clean verify`: **177 testes**, sem falhas, erros ou ignorados.

| Modulo/fase | Quantidade |
|---|---:|
| Broker / Surefire | 12 |
| Emissor / Surefire | 93 |
| Registro / Surefire | 60 |
| Consulta / Surefire | 11 |
| Consulta / Failsafe | 1 |

- [BrokerPersistenceTest](../../cobranca-broker/src/test/java/br/com/caixa/cobranca/broker/BrokerPersistenceTest.java): TCP real, parametros invalidos, commit/rollback, restart com comando e quarentena, contador persistido, destino ausente/sem rota e duas assinaturas duraveis independentes, uma offline.
- [OutboxEntregaJmsTest](../../boleto-emissor/src/test/java/br/com/caixa/cobranca/emissor/integration/OutboxEntregaJmsTest.java): confirmacao com consumidor offline, restart, falta de destino/fila, broker indisponivel, confirmacao perdida/duplicata e migracao de pendente/isolado/entregue HTTP.
- [RegistroEntregaJmsTest](../../boleto-registro/src/test/java/br/com/caixa/cobranca/registro/integration/RegistroEntregaJmsTest.java): listener real/JPA/Artemis, idempotencia, JSON/envelope invalido, corpo nao textual, conflito, tres entregas e continuidade do bom, recuperacao temporaria, rejeicao de negocio, rollback JPA, falha entre commit local e ack e falha atomica de transferencia para quarentena.
- Regressao E1–E7 e PACT consumidor/provedor/comparacao preservados, sem Docker ou broker pre-iniciado.

### Processos separados

Executados broker + emissor + registro + consulta em **quatro JVMs**, com dados de teste isolados e porta 61617:

1. Com registro desligado, emissao sintetica retornou **202 / AGUARDANDO_REGISTRO**.
2. Log confirmou publicacao no broker, tentativa 1, antes de iniciar o registro.
3. Broker foi encerrado e reiniciado com mesmo journal, ainda sem consumidor.
4. Registro foi iniciado; consumiu e persistiu o mesmo UUID `971c4d03-1cf9-4fbf-bb08-75adc36b7cf7`.
5. Leitura interna e consulta publica retornaram **REGISTRADO**, com marcador de linha nao pagavel.
6. Health dos tres servicos: **HTTP 200 / UP**. POST HTTP legado: **404**.
7. Todos os processos usados na verificacao foram encerrados; nenhum banco de demonstracao anterior foi apagado.

Isso comprova o transporte da E8, nao substitui as jornadas/carga/autorizacao e reproducao por outro integrante da E14.

## Uso critico de IA e proximos passos

A primeira versao usava fabricas `.of` ausentes no Artemis gerenciado; corrigidas para a API 2.33.0, sem subir versao arbitrariamente. A execucao empacotada revelou conflito de porta nao visivel ao filtrar apenas sockets Listen. Adicionadas verificacao real de ativacao, logs e teste TCP; investigacao de outro empacotamento foi descartada quando a causa foi identificada. O artefato final continua no padrao executavel Spring Boot.

A ferramenta de debug reutilizava seu terminal/sessao ao trocar de alvo. Para a prova simultanea foram usados processos exclusivos; retorno “debugger anexado” nao foi aceito como prova de prontidao. Da mesma forma, health UP nao foi confundido com processamento concluido: o estado final foi consultado depois do consumo.

Proxima etapa: **E9**, eventos com outbox e invalidacao do cache. Correlacao/resiliencia E10, acesso/auditoria E11 e operacao/retencao E12 seguem pendentes. Nenhum fechamento antecipado: E15 continua dependente do aceite integrado E14.
