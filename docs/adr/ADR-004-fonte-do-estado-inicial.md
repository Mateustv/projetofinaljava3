# ADR-004 — Resolução da Janela de Consistência Eventual: Fallback em Duas Fontes na Consulta

## Contexto

Quando um sistema cliente (ERP corporativo) submete uma cobrança à Central, o `boleto-emissor` valida os dados, persiste a intenção e responde imediatamente com `202 Accepted`, informando o identificador (UUID v4) e o status transitório `AGUARDANDO_REGISTRO`.

Como o processamento regulatório é assíncrono (desacoplado por outbox e fila de mensageria), existe uma **janela temporal de consistência eventual** entre o momento em que a emissão confirma o aceite e o momento em que o `boleto-registro` consome a mensagem e persiste o título em sua própria base. Em condições normais essa janela dura milissegundos, mas durante picos de fim de mês ou oscilações da câmara externa ela pode durar vários segundos.

Durante esse intervalo transitório:
- O `boleto-registro` ainda **não possui o registro** em suas tabelas.
- Apenas o `boleto-emissor` possui o registro persistido do aceite em seu banco de dados.

**O Risco Crítico de Negócio:**
Se o cliente consultar o estado do boleto imediatamente após receber o HTTP 202 e a Consulta buscar dados exclusivamente no serviço de Registro, o sistema responderá com **HTTP 404 Not Found**. Para o cliente bancário, isso é catastrófico: *"O banco acabou de emitir um comprovante dizendo que recebeu minha cobrança, mas ao consultar diz que o boleto não existe! O sistema perdeu meus dados!"*. Isso levaria o cliente a reenviar a remessa em pânico, gerando duplicidade de cobranças e sobrecarga severa no suporte operacional.

## Decisão

Adotar a estratégia de **Fallback em Duas Fontes no Serviço de Consulta (Opção C)**:

1. Ao receber uma consulta de boleto no *cache miss*, o `boleto-consulta` requisita prioritariamente o `boleto-registro` (que é a autoridade máxima sobre os estados finais `REGISTRADO` e `REJEITADO`).
2. **Se e somente se o registro responder HTTP 404 Not Found**, a consulta aciona imediatamente um *fallback* interno ao `boleto-emissor` (`GET /interno/boletos/{id}`).
3. Se o emissor localizar o aceite, a consulta projeta o estado `AGUARDANDO_REGISTRO`, armazena no cache com **TTL curto (30 segundos)** e responde HTTP 200 ao cliente.
4. Somente se **ambos os serviços responderem 404**, a consulta responde 404 ao cliente, garantindo que o título realmente nunca foi recebido pelo banco.
5. Se qualquer uma das fontes apresentar erro de conexão, timeout ou erro 5xx, a consulta responde **HTTP 503 Service Unavailable** com `X-Correlation-ID`; **falha técnica nunca é mascarada como 404 Not Found**.

## Alternativas consideradas

- **Consulta lê exclusivamente do Registro (Opção A):** Rejeitada. Devolveria 404 falso para qualquer consulta disparada na janela de processamento assíncrono, quebrando a integridade percebida pelo cliente e violando a regra básica de UX bancária.
- **Emissor como Dono Central de Estados (Opção B):** Rejeitada. Exigiria que o Registro chamasse o Emissor de volta para atualizar o estado final, acoplando os dois serviços bidirecionalmente e sobrecarregando o serviço de emissão com todo o tráfego massivo de consulta (20:1).
- **CQRS com Read Model Próprio na Consulta (Opção D):** Considerada para fases futuras. Criar uma base de dados relacional própria na consulta alimentada exclusivamente por eventos de domínio adicionaria a complexidade de gerenciar a consistência eventual do próprio banco de leitura, além de exigir infraestrutura de persistência adicional no MVP. A Opção C resolve a dor de negócio sem necessidade de banco de dados na consulta.

## Consequências

- **Positivas:**
  - **Eliminação Absoluta do "Falso 404":** O cliente corporativo sempre obtém visibilidade imediata de sua solicitação, mesmo milissegundos após o aceite assíncrono.
  - **Arquitetura Leve na Consulta:** O `boleto-consulta` permanece *stateless* em termos de persistência relacional (sem banco relacional próprio), operando de forma extremamente rápida com cache em memória e clientes REST.
  - **Respeito à Autoridade de Domínio:** O emissor é a autoridade sobre o aceite inicial; o registro é a autoridade sobre o título final.
- **Custos / Mitigações:**
  - Na janela transitória de um boleto recém-emitido, a consulta executa duas chamadas HTTP em cascata (registro → emissor). Esse custo é absorvido e mitigado pelo cache Caffeine (ADR-006), timeouts agressivos de rede e Circuit Breakers independentes por fonte (ADR-013).

## Status

Aceita e implementada no fluxo de integração entre consulta, registro e emissor.
