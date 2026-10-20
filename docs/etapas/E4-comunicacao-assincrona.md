# E4 — Comunicação assíncrona com outbox

## O que esta etapa ensina

Em vez de executar todo o trabalho dentro do `POST /boletos`, o emissor aceita a solicitação e devolve `202 Accepted`. O serviço de registro processa o comando depois. Essa separação evita que a emissão fique esperando pela registradora e permite repetir uma entrega sem duplicar o registro.

## Caminho implementado

1. O emissor valida a solicitação e calcula a chave/idempotência como na E2.
2. Na mesma transação H2, grava o aceite e o `RegistrarBoletoCommand` na tabela outbox.
3. Depois do commit local, o despachante lê itens pendentes e faz `POST /interno/comandos/registro` no serviço de registro, sem fazer parte da requisição original. O despacho pode ocorrer antes ou depois de a resposta `202` chegar ao cliente.
4. O registro valida a correspondência do UUID, simula uma confirmação da registradora e persiste o estado `REGISTRADO` no H2 que pertence a esse serviço.
5. Só depois do commit do registro a chamada retorna sucesso; então o emissor marca a outbox como entregue e remove dali o JSON do comando.
6. Se o envio falhar, o comando permanece na outbox. Na E5, foi adicionado retry limitado seguido de isolamento técnico (ver [E5](E5-resiliencia-outbox.md)). Se houver dúvida sobre o resultado (por exemplo, a resposta HTTP se perder), o comando poderá chegar mais de uma vez.
7. O consumidor compara o comando recebido com o já persistido: reentrega idêntica retorna o registro existente; mesmo UUID com conteúdo diferente retorna `409`.

## O que significa “at-least-once”

O sistema pode repetir entregas porque não sabe se o registro terminou quando uma conexão cai no instante da resposta. A garantia não é “processar uma única vez”; é “poder entregar novamente sem duplicar o efeito”, graças à idempotência do consumidor. Na E5, foram acrescentados limite de retry e isolamento; não há garantia de registro eventual se as falhas persistirem.

## Transporte provisório e migração

Por decisão do grupo, esta E4 não exige RabbitMQ nem Docker. Como os serviços são processos separados, uma fila em memória não seria suficiente; usamos a outbox persistida e HTTP assíncrono como transporte provisório.

O contrato `RegistrarBoletoCommand` e o serviço de processamento não dependem de HTTP. Quando houver Docker, a integração pode adicionar um produtor RabbitMQ no lugar de `HttpRegistroCommandPublisher` e um listener `@RabbitListener` que delegue ao mesmo `RegistroBoletoService`. RabbitMQ adicionará broker, exchanges, filas e roteamento. A E5 implementou backoff e isolamento na outbox HTTP, não DLQ de broker.

O H2 usado ao executar os serviços grava arquivos na pasta `data/` do módulo de cada serviço; os testes automatizados usam bancos em memória. Os arquivos locais são ignorados pelo Git.

## Simulação da registradora

Por padrão, `RegistradoraSimulada` confirma o registro e gera um identificador `SIM-...`. A E5 acrescentou os modos de rejeição e indisponibilidade. Ela não registra o título em uma instituição real e não calcula linha digitável/código de barras de pagamento; esses campos permanecem ausentes. Não use esses dados para cobrança.

## Como validar

Na raiz do projeto:

```powershell
mvn -B -pl boleto-emissor,boleto-registro -am test
mvn -B clean verify
```

Para o teste manual entre processos, inicie primeiro `boleto-registro` (porta 8082) e depois `boleto-emissor` (porta 8081). Envie um `POST /boletos`; ele deve responder `202` com `AGUARDANDO_REGISTRO`. O despachante envia o comando em segundo plano. Consulte `GET http://localhost:8082/interno/boletos/{id}` para confirmar `REGISTRADO` e o identificador `SIM-{id}`. Se o serviço de registro estiver desligado, a E5 limita as tentativas e isola o comando quando o limite é atingido.

O consumidor e o despachante também são cobertos por testes automatizados de reentrega, conflito de conteúdo, entrega confirmada e falha de transporte.
