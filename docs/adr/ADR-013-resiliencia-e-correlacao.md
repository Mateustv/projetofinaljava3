# ADR-013 - Resiliencia HTTP e correlacao ponta a ponta

## Contexto

E8/E9 ja separam retry de publicacao de redelivery de processamento, mas a consulta fazia chamadas HTTP sem timeouts explicitos ou circuit breaker. Correlacao de eventos era o UUID do boleto; a consulta criava outro id somente no erro 503. E10 precisa limitar a espera por dependencias e acompanhar uma requisicao pelas fronteiras sem alterar a identidade de negocio.

## Decisao

### Fronteira HTTP da consulta

- Usar `resilience4j-circuitbreaker` 2.2.0 diretamente, sem AOP, anotacoes, starter ou dependencias de retry/time limiter. Java 17, sem Docker.
- Um circuito por fonte em cada instancia de consulta: registro e emissor nao compartilham estado.
- Janela de contagem de tres chamadas, minimo tres, limiar 100%: abre quando as ultimas tres chamadas contabilizadas falham tecnicamente. Espera 10 s aberta; a primeira chamada posterior pode obter a unica permissao HALF_OPEN. Sucesso fecha, falha tecnica reabre por mais 10 s. Nao ha sondagem em segundo plano.
- 404 e resposta valida da fonte, nao falha de disponibilidade: continua lancando ausencia ao chamador, permite o fallback C e conta como disponibilidade inclusive para recuperar HALF_OPEN. `REJEITADO` retornado em 200 tambem e disponibilidade, nao erro.
- Demais HTTP nao-2xx, redirecionamento, falha de transporte, JSON/corpo/identidade/estado inconsistentes sao falhas tecnicas. Nao transformar em 404 nem inventar resposta de negocio. 4xx diferente de 404 falha sem retry; o circuito nao corrige credencial/contrato.
- Configurar explicitamente conexao 2000 ms e leitura 3000 ms nos dois clientes, com valores positivos obrigatorios. Redirecionamento automatico desligado.
- Uma invocacao HTTP logica por fonte, sem retry de aplicacao. Fallback somente em 404: no maximo uma chamada ao registro e uma ao emissor. Circuito aberto rejeita antes da chamada remota; retorna 503 na API, nao fallback/sucesso. Cache hit continua funcionando sem tocar o circuito.
- Limites de socket nao sao deadline global: DNS, escalonamento, espera por trava e leitura de resposta que continuamente entrega dados nao sao um SLA absoluto de cinco segundos. Demonstracao usa enderecos locais; metas p95/carga e eventual deadline global devem ser avaliadas na E14.

### Correlacao

- Novo modulo tecnico `infraestrutura-http`, reutilizado pelos tres servicos, sem JPA nem regras de dominio. Nao adicionar Spring ao `shared-contracts` nem arrastar outbox/JPA para a consulta.
- Filtro comum em todas as entradas HTTP: `X-Correlation-ID` opcional. Ausente gera UUID v4; informado deve ser um unico UUID v4/variante 2, canonico em minusculas, sem espacos/listas. Invalido retorna 400 antes do dominio e um novo id seguro, sem refletir entrada invalida.
- Resposta sempre inclui esse header, inclusive erros e cache hit. Erro 400 de correlacao e erro 503 da consulta tambem incluem o mesmo `correlation_id` no corpo. Outros contratos de resposta preservam o corpo existente; correlacao fica no header.
- Contexto por thread e MDC durante o escopo da requisicao; limpeza/restauracao em `finally` via `AutoCloseable`, inclusive em erro. Nao usar valor de outra requisicao, cachear correlacao ou assumir propagacao implicita para outra thread.
- Emissor captura correlacao na persistencia do comando e do evento `EmissaoAceita`. O comando ganhou `correlation_id` opcional para compatibilidade de leitura; sem ele, assume UUID do boleto. Nao alterar fingerprint ou chave convenio/controle.
- Publisher JMS usa essa correlacao no header e preserva no JSON da outbox. Registro abre escopo pelo comando e grava a mesma correlacao no evento final. Publisher de eventos ja propaga o envelope ao header JMS; consulta abre escopo durante a invalidacao.
- Correlacao do comando e validada antes do negocio. Eventos exigem UUID v4 tambem na correlacao. Se houver header JMS, ele precisa coincidir com o envelope; divergencia e permanente e vai atomicamente para quarentena. Header ausente e tolerado para mensagens legadas; o envelope precisa continuar valido.
- Consultas HTTP internas encaminham o id da requisicao publica corrente nas duas fontes, sem reutilizar o id do cliente que anteriormente preencheu o cache.
- Correlacao e metadado, **nao identidade de negocio**. Registro e ledger comparam o conteudo excluindo apenas `correlation_id`; resultado repetido nao gera outro evento nem chama a registradora. JSON persistido mantem a correlacao original para evidencia. Compatibilidade com JSON antigo sem campo e testada.
- Repeticao HTTP idempotente tem correlacao da nova requisicao na resposta; a intencao ja existente preserva a correlacao da primeira emissao. Nao republicar evento para cada consulta/reenvio.

### Outras fronteiras e orcamento

JMS preserva `callTimeout=2000`, confirmacao duravel/transacional, `initialConnectAttempts=1`, `reconnectAttempts=0` da URL padrao. Publicacao de comandos/eventos continua limitada pelas respectivas outboxes: tres tentativas totais, minimos 1 s/5 s, isolamento no H2. Consumidores continuam tres entregas e quarentena no broker. Reconexao do listener a cada 2 s recupera a conexao, nao executa uma tentativa extra de negocio.

Nao adicionar circuito/retry em torno da transacao JPA nem em volta do consumidor JMS. Nao adicionar breaker a registradora simulada: ela e local, com ledger H2, nao uma chamada HTTP real; o timeout de 5 s da registradora real continua premissa de contrato, nao funcionalidade comprovada. O adaptador HTTP antigo nao e bean nem fallback; conserva limites historicos 2 s/5 s e agora encaminha correlacao quando usado nos testes legados.

## Alternativas e consequencias

- Somente timeout: limita espera de socket, mas continua pressionando fonte indisponivel.
- Breaker unico para as duas fontes: rejeitado; uma dependencia nao deve abrir circuito da outra.
- Retry HTTP automatico: rejeitado; aumentaria latencia e carga sem necessidade, somando tentativas ocultas.
- Breaker no publicador/consumidor JMS: rejeitado nesta etapa; ja possuem controle persistido de tentativas e destino de isolamento. Um curto-circuito nao pode ser confirmado como envio/processamento.
- Guardar id somente em MDC: rejeitado; contexto nao sobrevive a scheduler, reinicio ou fila.
- Incluir correlacao no fingerprint/comparacao do comando: rejeitado; uma nova tentativa HTTP nao e outra operacao de negocio.
- Ignorar 404 completamente no circuito: rejeitado; a ausencia valida comprova disponibilidade e deve permitir recuperar HALF_OPEN, sem deixar sondas indefinidamente inconclusivas.

Circuitos sao locais e reiniciam fechados ao reiniciar a consulta. Nao ha status endpoint publico de circuitos nem dependencia externa de monitoramento. Logs contem metadados, nunca corpo completo, documentos, endereco ou valor. Isto nao substitui identidade/autorizacao/auditoria E11 nem operacao/replay E12.

## Status e evidencias

Aceita e implementada na E10. `mvn -B clean verify`: **265 testes aprovados**, zero falhas/erros/ignorados, PACT preservado. Matriz de testes, inventario e operacao: [E10](../etapas/E10-resiliencia-e-correlacao.md). Atualiza correlacao provisoria da [ADR-012](ADR-012-eventos-e-invalidacao.md), preservando suas garantias de outbox/invalidacao. E14/E15 continuam pendentes.
