# ADR-008 — Retry e isolamento de falhas na outbox HTTP

## Contexto

Na E4, comandos pendentes eram reenviados indefinidamente. Uma falha permanente ou uma indisponibilidade prolongada poderia ocupar os primeiros itens do lote e prejudicar comandos novos. O grupo aprovou continuar sem RabbitMQ/Docker, implementando resiliência sobre a outbox HTTP.

## Decisão

- **Três tentativas no total**, incluindo o primeiro envio. Após a primeira falha temporária, aguardar pelo menos 1 segundo; após a segunda, pelo menos 5 segundos. A próxima execução também depende do polling (1 segundo por padrão) e da duração de outras chamadas do lote.
- Persistir `tentativas`, `ultimo_erro`, `proxima_tentativa_em` e `falha_definitiva_em` no H2 do emissor. Buscar somente itens ainda não entregues, não isolados e com horário de retry vencido, em lotes de até 20.
- Falha de conexão, timeout, HTTP 408, 429 e 5xx são retentáveis. Outros HTTP 4xx são tratados como permanentes; exigem corrigir o comando ou a configuração antes de reprocessar. Respostas não 2xx não confirmam entrega.
- JSON inválido/nulo é permanente e isolado imediatamente, sem gastar novas tentativas de rede.
- Ao esgotar as três tentativas, isolar a mensagem na própria tabela outbox, preservando payload e diagnóstico. Não reenviar automaticamente os itens isolados. Comandos legados da E4 que já atingiram esse limite também são isolados.
- Configurar timeouts de conexão de 2 segundos e leitura de 5 segundos no produtor HTTP, evitando esperar indefinidamente por uma chamada.
- Disponibilizar `GET /interno/outbox/falhas` com os 20 isolamentos mais recentes. Expor somente UUID, quantidade de tentativas, tipo do erro e data da falha, nunca o JSON do boleto.
- Registrar logs com UUID, tentativa, classificação do erro e próximo horário. Não registrar corpo HTTP, mensagem livre da exceção ou dados pessoais.
- Rejeição da registradora é resultado de negócio: persistir `REJEITADO`, código e motivo, e confirmar a entrega HTTP. Não fazer retry nem isolar essa mensagem como falha técnica.

## Garantias e limites

O modelo continua permitindo reentregas (at-least-once), não exactly-once. O limite de retry **não garante registro eventual** durante uma indisponibilidade prolongada: após o esgotamento, a mensagem exige análise e intervenção. O isolamento é persistido, mas **não é uma DLQ de RabbitMQ**. Exchange, binding, dead-letter exchange e listener de DLQ continuam pendentes.

Tentativas são contabilizadas ao registrar o resultado da chamada. Se o processo cair entre enviar e persistir esse resultado, poderá haver um envio adicional após reiniciar; a idempotência do consumidor permanece necessária. A execução local suporta um despachante por base H2; coordenação distribuída de múltiplos despachantes não faz parte deste MVP.

Falha técnica não cria `REJEITADO`: sem confirmação de negócio, a consulta pode continuar exibindo `AGUARDANDO_REGISTRO`. Um `POST /boletos` idempotente não reinicia tentativas nem remove o isolamento. Não há endpoint de replay automático nesta etapa; uma operação segura de reprocessamento será uma evolução separada, após corrigir a causa e verificar se o registro já foi efetivado.

## Alternativas consideradas

- **Retry infinito:** rejeitado; esconde falhas permanentes e prejudica novos comandos.
- **Três retries além da primeira tentativa:** rejeitado; adotamos três tentativas totais para evitar ambiguidade.
- **Isolar rejeição de negócio:** rejeitado; o processamento terminou corretamente com resposta negativa da registradora.
- **RabbitMQ/DLQ agora:** adiado conforme a decisão do grupo registrada no ADR-007. A migração futura precisa também de confirmação de publicação no broker antes de concluir a outbox.

## Consequências

- Itens isolados e em backoff deixam de disputar o lote elegível com mensagens boas.
- Payloads isolados continuam contendo dados pessoais e exigem acesso restrito, proteção do arquivo H2 e política de retenção em uma implantação real. O endpoint interno não substitui autenticação/autorização.
- O estado `REJEITADO` exige campos de registradora/data de registro nulos. O script H2 executado após a atualização JPA remove as restrições `NOT NULL` desses dois campos nos bancos da E4, sem apagar registros existentes.

## Status

Aceita para a E5 provisória por HTTP, preservada como histórico. Transporte/topologia migrados na E8 conforme [ADR-011](ADR-011-broker-jvm-artemis.md).

### Atualização E8

Limite/backoff/isolamento da outbox são mantidos para **publicação JMS**, com contadores acumulados, sem reset de isolados. Confirmação é commit JMS no broker, não resultado final. Falha de serialização é permanente; erros JMS de conexão/configuração preservam payload e seguem o orçamento de publicação.

O consumidor tem orçamento separado: três entregas totais do broker e mínimos 1 s/5 s; rollback técnico e quarentena no esgotamento. Erros permanentes são transferidos imediatamente, atomicamente com ack do original. Rejeição de negócio continua final, sem DLQ. Contador persistido no broker antes de entregar; idempotência continua necessária entre commit JPA e ack JMS.

Não há RabbitMQ/DLX, mas há DLQ **Artemis** durável. Credenciais/acesso/auditoria e replay operacional continuam E11/E12. [E8: migração, garantias e evidências](../etapas/E8-broker-jvm.md).
