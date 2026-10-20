# Arquitetura — Central de Cobrança e Boletos

> **Estado: E5 provisória por HTTP.** Outbox com retry limitado, isolamento persistido e estados finais de negócio; topologia RabbitMQ/DLQ pendente. Detalhes em [`etapas/`](etapas/).

## Serviços (bounded contexts)

| Serviço | Porta | Base | Responsabilidade |
|---|:-:|---|---|
| `boleto-emissor` | 8081 | H2 `emissor` (própria) | Receber POST, validar, persistir aceite (202), idempotência |
| `boleto-registro` | 8082 | H2 `registro` (própria) | Consumir comando assíncrono, simular registradora, manter estado |
| `boleto-consulta` | 8083 | sem base (ADR-004, opção C) | Consulta por identificador, fallback e cache |
| `shared-contracts` | — | — | Records de comandos/eventos/DTOs |

## Diagrama de contextos

```mermaid
flowchart LR
    ERP[Sistema emissor / ERP] -->|POST /boletos 202| EM[boleto-emissor<br/>base: emissor]
    EM -->|outbox → HTTP assíncrono<br/>transporte provisório - E4| RG[boleto-registro<br/>base: registro]
    RG -->|chama| REG[(Registradora<br/>simulada)]
    CS[boleto-consulta<br/>cache] -->|GET interno| RG
    CS -.->|fallback na janela<br/>ADR-004 opção C| EM
    ERP -->|GET /boletos/id| CS
```

## Fluxo implementado
1. Sistema cliente solicita emissão → 2. valida campos e idempotência → 3. persiste aceite e outbox na mesma transação, responde 202.
4. O despachante entrega o comando ao registro por HTTP em segundo plano → consumidor idempotente simula a registradora e persiste o estado final.
5. A consulta lê cache; no miss tenta registro e, se ainda não existe, busca o aceite no emissor.

HTTP é provisório, não um message broker. O grupo optou por resiliência HTTP na E5; RabbitMQ continua pendente até haver ambiente disponível, preservando o contrato do comando e o processamento.

## Máquina de estados
`AGUARDANDO_REGISTRO → REGISTRADO | REJEITADO` (detalhe em `requisitos/glossario-e-regras.md`).

## Decisões
- [ADR-001](adr/ADR-001-stack-java17-e-perfis.md) stack e perfis · [ADR-002](adr/ADR-002-bases-segregadas.md) bases segregadas · [ADR-003](adr/ADR-003-corte-tres-servicos.md) corte em 3 serviços · [ADR-004](adr/ADR-004-fonte-do-estado-inicial.md) estado inicial (**opção C aceita pelo grupo**) · [ADR-005](adr/ADR-005-saga-e-outbox-no-mvp.md) SAGA/outbox (**outbox aceita na E4**) · [ADR-006](adr/ADR-006-cache-consulta.md) cache da consulta (**provisória**) · [ADR-007](adr/ADR-007-transporte-assincrono.md) transporte HTTP provisório

## Garantia de entrega e idempotência
O emissor persiste o aceite e o comando outbox na mesma transação antes de responder. O transporte HTTP tenta entregar pelo menos uma vez; em caso de resposta perdida pode haver reentrega, que o registro trata de forma idempotente usando o UUID.

Na E5, há até três tentativas totais registradas, com backoff de 1 s e 5 s. Erros permanentes ou tentativas esgotadas ficam isolados na própria outbox, fora dos lotes elegíveis. Não há promessa de registro eventual em falhas prolongadas nem replay automático. A API `/interno/outbox/falhas` expõe somente metadados dos 20 isolamentos recentes. Rejeição de negócio é persistida como `REJEITADO` e confirma a entrega, sem entrar no isolamento técnico. Ver [ADR-008](adr/ADR-008-retry-e-isolamento-outbox.md).

## Consulta e cache
`boleto-consulta` usa Caffeine cache-aside por UUID: TTL provisório de 30 s para `AGUARDANDO_REGISTRO` e 10 min para estados finais. No miss, consulta registro e usa fallback no emissor somente quando recebe 404. Não cacheia 404; falhas técnicas retornam 503 com correlation ID. Ver [ADR-006](adr/ADR-006-cache-consulta.md).
