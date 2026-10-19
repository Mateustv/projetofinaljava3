# ADR-002 — Bases de dados segregadas por serviço

## Contexto
A Central tem responsabilidades distintas (emissão, registro, consulta). Precisamos decidir como os serviços compartilham dados. Critério 1 da rubrica e CORE do brief exigem bases segregadas.

## Decisão
Cada serviço **dono de dado** tem **sua própria base** (H2 em memória no Plano B): `mem:emissor` e `mem:registro`. Nenhum serviço lê tabela de outro; a integração é **só por contrato** (mensagem na fila, evento ou REST interno), definido em `shared-contracts`.

## Alternativas consideradas
- **Base compartilhada** — simples e transacional, mas cria acoplamento por dados: mudança de schema quebra todos e some a independência de deploy. **Rejeitada.**
- **Base por serviço** — mais peças e consistência eventual, mas autonomia real. **Escolhida.**

## Consequências
- Ganho: isolamento por bounded context.
- Custo: não há transação entre serviços → idempotência (E2) e consistência eventual via fila (E4); o dado de um serviço chega ao outro por contrato.
- A `boleto-consulta` ainda não tem base: ver [ADR-004](ADR-004-fonte-do-estado-inicial.md).

## Status
Aceita
