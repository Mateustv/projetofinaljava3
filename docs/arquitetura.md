# Arquitetura — Central de Cobrança e Boletos

> **Estado: esqueleto (E0).** Será preenchida na E1 (contextos, fluxo) e atualizada a cada etapa.

## Serviços (bounded contexts)

| Serviço | Porta | Base | Responsabilidade |
|---|:-:|---|---|
| `boleto-emissor` | 8081 | H2 `emissor` (própria) | Receber POST, validar, aceitar (202), idempotência |
| `boleto-registro` | 8082 | H2 `registro` (própria) | Consumir fila, registrar título, manter estado |
| `boleto-consulta` | 8083 | a definir (ADR E1) | Consulta por identificador com cache |
| `shared-contracts` | — | — | Records de comandos/eventos/DTOs |

## Fluxo-alvo (do glossário, 8 passos)
1. Sistema cliente solicita emissão → 2. valida campos e idempotência → 3. persiste aceite, responde 202 → 4. segue à fila de registro → 5. consumidor registra → 6. resultado atualiza estado e cache → 7. eventos por outbox → 8. consulta lê cache e, no miss, a fonte persistente.

*(Diagrama de contextos: E1.)*
