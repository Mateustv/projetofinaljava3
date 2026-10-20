# ADR-007 — Transporte assíncrono da E4

## Contexto

O grupo quer avançar na comunicação produtor/consumidor sem exigir RabbitMQ ou Docker neste momento. Os serviços `boleto-emissor` e `boleto-registro` continuam sendo aplicações separadas. Portanto, uma fila puramente em memória não conseguiria entregar comandos entre os dois processos.

## Decisão

- O emissor grava `RegistrarBoletoCommand` em uma outbox H2 na mesma transação que persiste o aceite.
- Um despachante em segundo plano envia os comandos pendentes ao endpoint interno do serviço de registro por HTTP.
- A resposta do consumidor só é considerada confirmação depois que o resultado do registro foi persistido.
- `RegistroCommandPublisher` isola o transporte do emissor; `RegistroBoletoService` concentra o processamento idempotente. Essa separação permite adicionar adaptadores `RabbitTemplate`/`@RabbitListener` sem alterar as regras de emissão e registro.
- Os H2 do perfil de execução usam arquivos locais para preservar o aceite, o registro e comandos outbox pendentes após reiniciar um serviço. Os testes continuam usando bancos em memória.

## Garantia e limites

A entrega é **at-least-once**, não exactly-once. Uma falha de rede depois de o registro confirmar a transação, mas antes de o emissor receber a resposta, provoca reenvio. A chave única por identificador e a comparação do conteúdo tornam a reentrega idempotente; o mesmo UUID com outro conteúdo retorna `409`.

HTTP não é um message broker: a solução não oferece exchange, binding, roteamento ou DLQ de broker. Na E5, a [ADR-008](ADR-008-retry-e-isolamento-outbox.md) adicionou backoff e isolamento persistido de mensagens inválidas/tentativas esgotadas. O limite de retry pode exigir intervenção: não há garantia de registro eventual durante falhas prolongadas. A migração para RabbitMQ com Docker poderá trocar o transporte e adicionar topologia real, preservando o contrato e o consumidor.

## Alternativas consideradas

- **RabbitMQ nesta etapa:** adiado porque o grupo quer manter execução sem Docker por enquanto.
- **Fila em memória:** rejeitada para os serviços separados; perderia mensagens com o processo e exigiria executar produtor e consumidor na mesma JVM.
- **POST síncrono no caminho do `POST /boletos`:** rejeitado; faria a emissão esperar pelo registro e poderia perder o comando entre o commit do aceite e a chamada.

## Consequências

- A emissão continua respondendo `202` sem esperar a operação do registro; o despachante atua fora da requisição HTTP original.
- O registro pode levar algum tempo; a consulta pode observar `AGUARDANDO_REGISTRO` durante essa janela e por até 30 segundos adicionais devido ao TTL atual do cache.
- O projeto pode ser executado e testado sem broker externo.
- O endpoint interno de transporte não deve ser exposto publicamente; na implantação real deve ser protegido por rede/autenticação de serviço.

## Status

**Aceita pelo grupo para E4/E5; transporte substituído na E8 pela [ADR-011](ADR-011-broker-jvm-artemis.md).** Texto acima preservado como histórico.

### Atualização de direção — 20/10/2026

O grupo confirmou que **Docker não será utilizado na entrega**. A migração planejada deixa de aguardar RabbitMQ/Docker: comandos usarão fila em **broker executado em JVM (E8)**, e os eventos de domínio usarão publicação/assinatura pelo **mesmo broker (E9)**.

A tecnologia será selecionada após verificar disponibilidade no ambiente/Nexus, compatibilidade Java 17/Spring Boot 3.3, persistência, confirmação, assinaturas duráveis e quarentena. A E8 registrará a decisão técnica e a migração, preservando outbox e idempotência. Não há broker implementado nesta atualização documental nem fallback HTTP automático aprovado para indisponibilidade do broker.

A alternativa RabbitMQ/Docker acima fica preservada como histórico da E4, não como destino da entrega. Plano e critérios de aceite: [roteiro final](../../ROTEIRO.md).

### Migração validada na E8

Artemis/Jakarta JMS em JVM separada substituiu o publicador HTTP, que deixou de ser bean. Endpoint HTTP de comandos desativado por padrão; leitura GET permanece HTTP. Aceite/outbox/idempotência preservados; pendentes mantêm UUID/contadores/backoff e isolados/entregues antigos não são reiniciados.

Nova confirmação da outbox significa **commit no broker**, não conclusão no registro. O consumidor confirma JMS após commit JPA; duplicatas na janela entre transações são absorvidas pela E7. Execução e evidências: [E8](../etapas/E8-broker-jvm.md). Sem fallback HTTP automático.
