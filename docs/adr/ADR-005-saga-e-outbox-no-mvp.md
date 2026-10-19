# ADR-005 — SAGA e outbox no MVP

## Contexto
Na E2, o emissor precisa persistir o aceite antes de responder `202`. A chave de idempotência evita criar dois aceites para o mesmo convênio e número de controle. Ainda não existe comunicação entre serviços nesta etapa; a publicação na fila será implementada na E4.

A sessão 4 pede que a intenção de publicar um evento seja persistida junto com a mudança de estado. Isso evita o intervalo em que o banco confirma a emissão, mas a aplicação falha antes de enviar a mensagem ao broker.

## Decisão (proposta para o MVP)
- **Não implementar a SAGA síncrona** do projeto de referência. Ela chamaria o serviço de registro via HTTP durante a emissão, acoplando os serviços e esperando por uma operação que será assíncrona na E4.
- **Não implementar outbox na E2.** Não há broker nem publicação nesta etapa; o aceite e a resposta idempotente são persistidos no mesmo banco local.
- **Reavaliar outbox na E4**, quando a publicação de `RegistrarBoletoCommand` for adicionada. Se a garantia de não perder a intenção de publicar for necessária, a gravação do aceite e do registro outbox deverá compartilhar a transação local.

Esta decisão não promete entrega exatamente-uma-vez. Quando houver broker, a garantia será definida com base em entrega at-least-once e idempotência do consumidor.

## Alternativas consideradas
- **SAGA síncrona emissor → registro:** rejeitada para o MVP; repete um fluxo que será substituído pela fila e torna o POST dependente da disponibilidade do registro.
- **Outbox desde a E2:** adiada; sem produtor/broker, adicionaria tabela e processo de publicação que ainda não podem ser exercitados.
- **Aceite persistido sem publicar na E2:** proposta aceita para esta etapa; mantém o escopo focado em persistência/idempotência. O envio será incorporado na etapa de mensageria.

## Consequências
- O `202` só é enviado depois que o aceite foi gravado pelo emissor.
- Uma falha ao consultar ou gravar o aceite impede a resposta de sucesso e retorna `503`.
- Na E2 ainda não há mensagem para o registro; portanto, o boleto fica em `AGUARDANDO_REGISTRO`.
- A escolha sobre outbox deverá ser revista em grupo antes ou durante a E4, considerando a garantia exigida e a complexidade operacional.

## Status
**Proposta técnica para o MVP — compartilhar e ratificar com o grupo.**
