# ADR-005 — SAGA e outbox no MVP

## Contexto

Na E2, o emissor persiste o aceite antes de responder `202`. A chave de idempotência evita criar dois aceites para o mesmo convênio e número de controle. Na E4, emissão e registro passam a se comunicar de forma assíncrona.

A intenção de publicar o comando precisa ser persistida junto com o aceite. Isso evita perder o trabalho se o processo falhar entre confirmar a emissão e enviar o comando.

## Decisão

- **Não implementar a SAGA síncrona** do projeto de referência. Ela chamaria o serviço de registro durante a emissão, acoplando os serviços e fazendo o `POST` esperar uma operação que deve ser assíncrona.
- **Na E2, adiar a outbox.** Ainda não havia transporte assíncrono para exercitar.
- **Na E4, usar outbox transacional**: a emissão aceita e o comando `RegistrarBoletoCommand` são gravados na mesma transação local do emissor. Um despachante entrega o comando assincronamente ao serviço de registro por HTTP.
- O consumidor persiste o resultado antes de confirmar a resposta HTTP. Reentregas são possíveis e o consumidor é idempotente pelo UUID do boleto.
- O H2 do perfil de execução usa arquivo para que itens pendentes sobrevivam à reinicialização dos serviços.

Esta decisão não promete entrega exatamente-uma-vez. O modelo permite reentregas (at-least-once): se o registro processar o comando e a resposta se perder antes de o emissor marcar a outbox como entregue, o comando poderá ser reenviado. A chave única no registro transforma essa repetição em resposta idempotente. Na E5, o retry passou a ser limitado (ADR-008); comandos isolados exigem intervenção, sem garantia de registro eventual em uma indisponibilidade prolongada.

## Alternativas consideradas

- **SAGA síncrona emissor → registro:** rejeitada; faria o POST depender da disponibilidade do registro.
- **Publicação direta depois de persistir:** rejeitada na E4; uma falha entre o commit e o envio poderia perder a intenção de registro.
- **Broker RabbitMQ na E4:** adiado pelo grupo até haver ambiente Docker disponível; o contrato e o processamento ficam isolados do transporte atual para permitir a substituição posterior.

## Consequências

- O `202` só é enviado depois que o aceite e a mensagem outbox foram confirmados na mesma transação local.
- Uma falha ao consultar ou gravar o aceite impede a resposta de sucesso e retorna `503`.
- A entrega HTTP é assíncrona e pode ocorrer após o `202`; indisponibilidade provoca retry conforme o limite da ADR-008. A mensagem continua persistida mesmo se esgotar as tentativas e for isolada.
- O H2 em arquivo melhora a persistência local, mas não substitui uma base de produção gerenciada nem a topologia de um broker.

## Status

**Aceita para o MVP na E4; outbox transacional preservada.** A direção de transporte foi atualizada na [ADR-007](ADR-007-transporte-assincrono.md): broker JVM sem Docker na E8/E9, não aguardar RabbitMQ/Docker.

Consolidação E7: transporte indisponível não impede sozinho o 202 se aceite + outbox foram confirmados; falha nessa persistência retorna 503. A [ADR-010](ADR-010-contrato-funcional-e-idempotencia-registradora.md) acrescenta idempotência durável da registradora simulada e histórico transacional local, sem prometer atomicidade distribuída.

Atualização E8: [ADR-011](ADR-011-broker-jvm-artemis.md) implementa Artemis/Jakarta JMS sem alterar a transação de aceite/outbox. Publicação confirmada agora significa commit no broker, não registro concluído. Pendentes antigos mantêm UUID/contadores/backoff; isolados não são reiniciados. Consumidor confirma JMS após commit JPA; duplicatas entre transações são absorvidas pelo domínio E7.
