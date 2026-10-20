# E9 - Eventos confiaveis e invalidacao do cache

## Objetivo e entrega

Completar US-05/09 e Sessao 4 sem Docker, Kafka, notificacao efetiva ou novos servicos de negocio. O mesmo Artemis da E8 transporta comandos e eventos, com topologias separadas. Decisao: [ADR-012](../adr/ADR-012-eventos-e-invalidacao.md).

## O que foi implementado

- `EmissaoAceita`, `BoletoRegistrado` e `RegistroRejeitado` usam o [envelope compartilhado](../../shared-contracts/src/main/java/br/com/caixa/cobranca/contracts/EventoBoleto.java). JSON tem exatamente `id_evento`, `tipo`, `identificador_boleto`, `convenio`, `status`, `data_hora`, `correlation_id`. Sem nome, documento, endereco, valor ou solicitacao completa; tipo e estado precisam ser consistentes.
- O [modulo tecnico eventos-outbox](../../eventos-outbox/) fornece a mesma infraestrutura aos dois produtores, sem dependencia entre dominios nem banco compartilhado. `shared-contracts` permanece sem Spring/JPA.
- Emissor confirma aceite + comando + intencao `EmissaoAceita` numa transacao local antes do 202. Registro confirma resultado + historico + intencao final noutra transacao local. Falha da intencao reverte o estado local; o ledger externo simulado continua independente, como na E7.
- Cada produtor tem sua propria tabela `evento_outbox`: UUID de evento, unicidade boleto/tipo, envelope, tentativas, horarios e erro classificado. Reenvio idempotente nao cria outra intencao.
- Scheduler publica depois do commit de negocio, com JMS persistente/transacional. Marca `publicado_em` somente apos commit JMS. Tres tentativas totais, minimos 1 s/5 s, isolamento persistido em esgotamento ou envelope corrompido. Lotes de 20 excluem isolados e itens ainda em backoff.
- Queda depois de publicar e antes de confirmar localmente pode repetir o **mesmo UUID do evento**. Nao ha XA nem exactly-once. Consumidores precisam tolerar duplicacao.

### Topologia

| Destino | Tipo | Funcao |
|---|---|---|
| `registro.comandos` / `registro.quarentena` | ANYCAST duravel | Comandos E8, preservados |
| `boletos.eventos` | MULTICAST | Difundir fatos aos assinantes |
| `boletos.eventos.consulta` | Assinatura/fila duravel | Copia exclusiva para invalidacao da consulta |
| `boletos.eventos.quarentena` | ANYCAST duravel | Eventos invalidos ou entregas esgotadas |

Filas preprovisionadas, sem criacao/exclusao automatica. Publicador verifica a assinatura da consulta antes de confirmar envio; ausencia de binding tem encaminhamento a quarentena pelo broker. Assinante adicional precisa de **outra fila duravel no mesmo endereco**, nao de consumidor concorrente na fila da consulta. BI e somente um segundo assinante de teste, sem servico opcional em producao.

## Politica do cache

Consulta continua cache-aside Caffeine: 30 s em `AGUARDANDO_REGISTRO`, 10 min em finais; registro primeiro, fallback ao emissor somente apos 404; erros/ausencia nao sao cacheados.

O [listener](../../boleto-consulta/src/main/java/br/com/caixa/cobranca/consulta/ConsultaEventoListener.java) consome a assinatura pela FQQN. Eventos finais removem a entrada; a proxima consulta busca a resposta completa na fonte. `EmissaoAceita` e confirmado sem preencher/rebaixar estado. Evento nao e projecao de dados de pagamento.

O [servico](../../boleto-consulta/src/main/java/br/com/caixa/cobranca/consulta/ConsultaBoletoService.java) coordena get/busca/put e invalidacao pelas mesmas 64 travas por faixa de UUID. Se uma leitura estiver preenchendo o cache, o evento espera, remove o preenchimento e so depois confirma JMS. A resposta ja em voo pode ser transitoria; **depois do ack ela nao permanece como cache hit**. Duplicatas/fora de ordem apenas repetem a remocao.

Falha de invalidacao reverte JMS; broker faz tres entregas com minimos 1 s/5 s, depois quarentena. JSON/envelope/corpo invalidos sao transferidos na mesma transacao JMS do ack original. Falha dessa transferencia nao confirma o original.

**Restricao: uma instancia de consulta por demonstracao**, alem de uma instancia de cada produtor. Escalar consulta com cache local exige assinatura duravel exclusiva por instancia e gestao do ciclo de vida dessas assinaturas. Dividir a mesma fila nao invalida todos os caches. Latencia/p95 e expiracao sob carga continuam E14; nao sao demonstrados por anotacoes ou estes testes.

## Evidencias automatizadas

Executar na raiz:

```powershell
mvn -B clean verify
```

Os testes usam Artemis real com journal temporario e H2 real, sem broker externo:

Resultado: **BUILD SUCCESS, 214 testes aprovados**, zero falhas/erros/ignorados: 12 broker, 17 eventos-outbox, 96 emissor, 65 registro, 23 consulta Surefire e 1 Failsafe. PACT/regressoes anteriores preservados.

| Suite | Provas principais |
|---|---|
| [EventoOutboxTest](../../eventos-outbox/src/test/java/br/com/caixa/cobranca/eventos/EventoOutboxTest.java) | Tres envelopes/campos obrigatorios; transacao obrigatoria/rollback; commit com broker offline; retry/backoff/esgotamento e isolamento sem bloquear bom; confirmacao perdida duplica mesmo id; falta da assinatura; dois assinantes independentes e reinicio preservando pendencia offline; JSON corrompido |
| [EventosEmissaoTest](../../boleto-emissor/src/test/java/br/com/caixa/cobranca/emissor/EventosEmissaoTest.java) | Aceite/comando/evento atomicos sem broker; falha depois de flush reverte tudo; repeticao/conflito/invalidacao da entrada nao criam novo evento |
| [EventosRegistroTest](../../boleto-registro/src/test/java/br/com/caixa/cobranca/registro/EventosRegistroTest.java) | Aprovacao/rejeicao; final/historico/evento unicos; falha de intencao e rollback externo; recuperacao pelo ledger independente; final legado nao inventa evento |
| [ConsultaEventosTest](../../boleto-consulta/src/test/java/br/com/caixa/cobranca/consulta/ConsultaEventosTest.java) | Invalidacao antes de TTL; duplicata/ordem; outro boleto; corrida deterministica com latches; falha antes de ack/reentrega; invalido e nao textual preservados na quarentena; esgotamento de invalidacao sem bloquear bom |

Fontes HTTP da consulta sao mocks nesses testes de eventos; o cliente/provedor REST real continua coberto pelo PACT E6/E7. Nao declarar esses testes como nova prova de quatro JVMs: essa execucao esta documentada na E8 para comandos. Jornada integrada completa e reproducao por outro integrante continuam E14.

## Operacao e migracao

- Usar os mesmos quatro JARs/terminais do [README](../../README.md). Agora `BROKER_URL` tambem precisa ser configurado no terminal da **consulta**, se mudar a porta do broker.
- Broker deve ser reiniciado com a versao E9 para provisionar a topologia de eventos; preservar journal anterior. H2 com `ddl-auto=update` cria `evento_outbox` em cada produtor; nao compartilha tabelas/bancos.
- Nao ha backfill de aceites/resultados anteriores a E9. Finais legados permanecem intactos, sem evento retroativo inventado. Pendentes de comando preservam UUID/contadores; novos resultados apos a atualizacao passam a registrar intencao final.
- `eventos.outbox.enabled=false` desliga somente o dispatcher, nao a gravacao transacional. `consulta.eventos.enabled=false` desliga consumo e deixa apenas TTL; flags utilizadas em testes isolados, nao no perfil demonstrado padrao.
- `publicado_em` significa confirmado pelo broker, nao consumido por todos. Eventos isolados ficam em `evento_outbox`; a API antiga `/interno/outbox/falhas` continua sendo **de comandos**, nao lista eventos nem DLQ.
- Envelope minimo e metadados permanecem na outbox, inclusive depois do sucesso. Acesso/replay/retencao e trilha completa ainda E11/E12; sem limpeza/replay automatico ou promessa de entrega eventual apos isolamento.
- Correlacao de eventos e inicialmente o UUID do boleto. Header HTTP validado/propagado ponta a ponta continua E10.

## Uso critico de IA e proximos passos

IA auxiliou contratos, infraestrutura, integracao e testes. A regressao completa identificou que importar diretamente a configuracao JPA no main contaminava `@JsonTest`; a correcao foi descobrir a configuracao pelo component scan delimitado, preservando os filtros dos testes de slice. Testes especificos de JSON e transacoes foram reexecutados antes da regressao final.

O projeto nao esta fechado. Proxima E10: limites de chamadas remotas, circuit breaker e correlacao ponta a ponta; depois E11/E12, aceite E14 e fechamento E15.
