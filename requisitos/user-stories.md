# User Stories — Central de Cobrança e Boletos

> **Compilado por:** Marina Azevedo (PO de Cobrança) · **Base:** cinco sessões de elicitação · **Status:** primeira fonte de verdade.
> Em divergência com as transcrições, **a transcrição prevalece**. Itens **[PREMISSA]** são hipóteses didáticas a validar.
> Termos: ver `glossario-e-regras.md`.

---

## Épico 1 — Emissão

### US-01 — Aceitar a emissão de um boleto
**Como** sistema emissor (ERP/canal corporativo), **quero** enviar a solicitação de um boleto, **para** que ele seja registrado e fique disponível para cobrança.

**Notas:** POST sistema a sistema; a Central não movimenta dinheiro. Campos e formatos: glossário §2.

**Critérios de aceite**
- **Dado** uma solicitação válida, **quando** o POST é recebido, **então** responde **202 Accepted** com identificador interno (UUID v4), status `AGUARDANDO_REGISTRO` e data/hora do recebimento; a resposta diz "aceita para processamento", nunca "registrado".
- **Dado** campo obrigatório ausente ou fora do formato, **então** responde **400** com a lista de todos os campos inválidos (campo + motivo).
- **Dado** juros/multa/desconto/abatimento informados sem seus complementos, **então** 400.
- **Dado** vencimento anterior à emissão ou valor ≤ 0, **então** 400.
- **Dado** que a resposta 202 foi dada, **então** o aceite já está **persistido** (não depende da fila para existir).
- **Dado** que não é possível aceitar com segurança, **então** responde **503** e não 202. Com a outbox transacional, transporte/broker indisponível **não impede sozinho o aceite**: na E9, `202` só após confirmar aceite + comando + intenção de evento no banco; falha nessa persistência retorna 503 ([ADR-005](../docs/adr/ADR-005-saga-e-outbox-no-mvp.md), [ADR-012](../docs/adr/ADR-012-eventos-e-invalidacao.md)).

### US-02 — Idempotência da emissão
**Como** sistema emissor, **quero** reenviar a mesma solicitação sem duplicar boleto.

- **Dado** convênio + número de controle já aceitos com **mesmo conteúdo**, **então** devolve o mesmo identificador e a resposta original (202), sem novo boleto.
- **Dado** a mesma chave com **conteúdo diferente**, **então** **409 Conflict**.

### US-03 — Registrar o título na registradora (assíncrono)
**Como** time de plataforma, **quero** que o registro aconteça por consumidor de fila, **para** suportar picos sem perder solicitações.

- **Dado** um aceite, **quando** a mensagem é consumida, **então** o consumidor chama a registradora e atualiza o estado para `REGISTRADO` (com linha digitável, código de barras, id definitivo, data/hora) ou `REJEITADO` (com código e motivo).
- **Dado** a resposta ao chamador, **então** ela **não espera** o registro.
- **Dado** que toda solicitação aceita deve terminar `REGISTRADO` ou `REJEITADO`, **então** o histórico das transições é guardado.

> Perfil didático E7: os campos de pagamento são marcadores `NAO-PAGAVEL-SIM-...`, não sequências bancárias válidas. O aceite fica no emissor; novos resultados e histórico de origem/transição final são persistidos atomicamente no registro. Finais pre-E7 são preservados sem inventar dados/histórico retroativo. Detalhes e limites na [E7](../docs/etapas/E7-contrato-funcional.md).

---

## Épico 2 — Consulta

### US-04 — Consultar boleto por identificador
**Como** cliente emissor ou atendente, **quero** consultar o estado pelo identificador.

- **Dado** identificador conhecido, **então** retorna **sempre** o estado (inclusive `AGUARDANDO_REGISTRO` logo após o aceite).
- **Dado** `REGISTRADO`, **então** expõe linha digitável e código de barras; em `AGUARDANDO_REGISTRO` e `REJEITADO` **não** expõe dados de pagamento; `REJEITADO` expõe código e motivo.
- **Dado** identificador inexistente, **então** **404**; formato inválido, **400**.
- **Dado** falha técnica de dependência, **então** **503** rastreável (correlation_id) — **nunca** 404.

### US-05 — Consulta rápida com cache
**Como** área de produto, **quero** leitura rápida em picos.

- **Dado** consulta, **então** cache primeiro; no miss, fonte persistente; se encontrado, popula o cache.
- **Dado** mudança de estado, **então** a entrada é invalidada/atualizada; TTL **[PREMISSA]**: 30 s transitório, 10 min final.
- "Não encontrado" **não** é cacheado.
- Meta **[PREMISSA]**: p95 < 100 ms com cache.

---

## Épico 3 — Confiabilidade

### US-06 — Retentativa de falhas temporárias
- **Dado** registradora indisponível/timeout/5xx, **então** o sistema retenta com backoff sem descartar a mensagem. A premissa original de 3× e 1 s/2 s/4 s foi substituída pela [ADR-008](../docs/adr/ADR-008-retry-e-isolamento-outbox.md). Na E8, a [ADR-011](../docs/adr/ADR-011-broker-jvm-artemis.md) preserva **três tentativas totais e mínimos de 1 s/5 s por fronteira**: publicação da outbox e processamento pelo broker. Esgotamento do produtor preserva no H2; do consumidor vai à quarentena Artemis. Não há retry de negócio no produtor nem garantia de término após isolamento.

### US-07 — DLQ para falhas permanentes
- **Dado** mensagem que esgota as tentativas ou é malformada, **então** vai para a DLQ **com contexto da falha**; DLQ é quarentena (acesso restrito, reprocesso auditado).
- **Dado** rejeição de negócio da registradora, **então** estado `REJEITADO` — **não** DLQ, **não** retry.

### US-08 — Consumidor idempotente
- **Dado** entrega duplicada, **então** o efeito de negócio não se repete (boleto em estado final não chama a registradora de novo).
- Garantia declarada: **at-least-once + idempotência**.

---

## Épico 4 — Eventos

### US-09 — Publicar eventos de domínio
**Como** antifraude/BI, **quero** ser avisado sobre `EmissaoAceita`, `BoletoRegistrado`, `RegistroRejeitado`, **sem** a Central me conhecer.

- **Dado** mudança de estado, **então** publica evento em tópico com payload mínimo (`id_evento`, `tipo`, `identificador_boleto`, `convenio`, `status`, `data_hora`, `correlation_id`), **sem** dados pessoais.
- **Dado** o resultado do registro, **então** a intenção de publicar é gravada na **mesma transação** (outbox).
- Antifraude **não** bloqueia o registro.

> Notificação efetiva (e-mail/SMS/push): **fora do MVP**.

---

## Épico 5 — Compliance

### US-10 — Auditoria
- **Dado** emissão, consulta, mudança de estado ou reprocessamento, **então** registra `ator`, `ação`, `identificador_boleto`, `data_hora`, `resultado`, `correlation_id`. O boleto inteiro **não** vai ao log; documentos mascarados.

### US-11 — Proteção de dados em todos os canais
- LGPD aplicada a banco, cache, fila, DLQ, logs e eventos: minimização, acesso por necessidade, criptografia/mascaramento conforme padrões institucionais.

### US-12 — Retenção
- Retenção **[PREMISSA]** de 5 anos após o encerramento da cobrança (estado final), a validar com Compliance.

---

## Fora do MVP
Pagamento/liquidação; alteração, cancelamento, protesto, negativação; baixa comandada; busca por documento/período/situação; notificação efetiva; política de descarte pós-retenção.

---

## Rastreabilidade (História → Capacidade Arquitetural → Fase de Entrega)

Conforme a estratégia de **Time-to-Market e Gestão de Risco do MVP** formalizada na [ADR-014](../docs/adr/ADR-014-prioridade-core-e-limites-de-escopo.md), o escopo foi fatiado entre a **Fase 1 (MVP — Núcleo de Alta Confiabilidade Transacional)** e a **Fase 2 (Pós-Go-Live — Expansão de Negócio e Governança)**:

| História | Sessão | Implementação na Fase 1 (MVP) | Status no MVP | Padrão Arquitetural Relacionado |
|---|:-:|---|---|:-:|
| **US-01** | 1, 2 | Aceite persistido atômico com Transactional Outbox; validação estrutural canônica compartilhada | **Entregue** | Bounded Context Emissor, Transactional Outbox ([ADR-005](../docs/adr/ADR-005-saga-e-outbox-no-mvp.md)) |
| **US-02** | 2 | Idempotência radical por convênio + número de controle, hash SHA-256 e conflito 409 | **Entregue** | Deduplicação de Entrada ([ADR-003](../docs/adr/ADR-003-corte-tres-servicos.md)) |
| **US-03** | 1, 4 | Fila durável ANYCAST Artemis (`registro.comandos`), dados não-pagáveis e histórico atômico | **Entregue** | Comunicação Assíncrona ([ADR-007](../docs/adr/ADR-007-transporte-assincrono.md), [ADR-011](../docs/adr/ADR-011-broker-jvm-artemis.md)) |
| **US-04** | 3 | Consulta com fallback em duas fontes cobrindo a janela transitória sem falso 404 | **Entregue** | Fallback de Leitura ([ADR-004](../docs/adr/ADR-004-fonte-do-estado-inicial.md)), Contrato Pact ([ADR-009](../docs/adr/ADR-009-contrato-consulta-registro.md)) |
| **US-05** | 3 | Cache-aside Caffeine com TTL diferenciado e invalidação reativa em tempo real | **Entregue** | Cache-aside ([ADR-006](../docs/adr/ADR-006-cache-consulta.md)), Invalidação Reativa ([ADR-012](../docs/adr/ADR-012-eventos-e-invalidacao.md)) |
| **US-06** | 4 | Resiliência com 3 tentativas e backoff (1s/5s) na outbox e consumidor; timeouts e Circuit Breakers | **Entregue** | Resiliência e Circuit Breaker ([ADR-008](../docs/adr/ADR-008-retry-e-isolamento-outbox.md), [ADR-013](../docs/adr/ADR-013-resiliencia-e-correlacao.md)) |
| **US-07** | 4 | Quarentena durável no Artemis (`registro.quarentena`) para contenção imediata de Poison Pills | **Entregue** | Dead-Letter Queue / Quarentena ([ADR-008](../docs/adr/ADR-008-retry-e-isolamento-outbox.md)) |
| **US-08** | 4 | Consumidor idempotente com Ledger de operações externas em transação isolada `REQUIRES_NEW` | **Entregue** | Idempotência Externa ([ADR-010](../docs/adr/ADR-010-contrato-funcional-e-idempotencia-registradora.md)) |
| **US-09** | 4 | Tópico pub/sub MULTICAST com envelopes de eventos mínimos (7 metadados) sem dados sensíveis | **Entregue** | Event-Driven Architecture ([ADR-012](../docs/adr/ADR-012-eventos-e-invalidacao.md)) |
| **US-10** | 5 | Logs estruturados com Correlation ID propagado ponta a ponta sem vazar dados sensíveis | **Fase 1 Parcial / Fase 2** | Rastreabilidade Distribuída ([ADR-013](../docs/adr/ADR-013-resiliencia-e-correlacao.md)) |
| **US-11** | 5 | Minimização estrita em eventos e logs; autenticação mTLS e HSM planejados para a Fase 2 | **Fase 2** | Governança Corporativa ([ADR-014](../docs/adr/ADR-014-prioridade-core-e-limites-de-escopo.md)) |
| **US-12** | 5 | Hipótese regulatória de retenção fiscal de 5 anos; rotinas de expurgo automatizado para a Fase 2 | **Fase 2** | Retenção Arquivística Fiscal ([ADR-014](../docs/adr/ADR-014-prioridade-core-e-limites-de-escopo.md)) |

A delimitação entre a Fase 1 e a Fase 2 assegura que o núcleo crítico transacional entre em produção com confiabilidade inabalável, deixando as integrações periféricas de segurança corporativa para o ciclo de pós-estabilização.
