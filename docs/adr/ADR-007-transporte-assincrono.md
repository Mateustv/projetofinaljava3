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

HTTP não é um message broker: a solução não oferece exchange, binding, roteamento, DLQ ou isolamento de fluxo por fila. O retry atual é polling simples da outbox; backoff e tratamento explícito de mensagens inválidas ficam para a E5. A migração para RabbitMQ com Docker poderá trocar o transporte e adicionar topologia real, preservando o contrato e o consumidor.

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

**Aceita pelo grupo para a E4. Reavaliar na E5 quando Docker/RabbitMQ estiver disponível.**
