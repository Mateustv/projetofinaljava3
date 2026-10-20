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

## Rastreabilidade (história → sessão → etapa do projeto)

O [roteiro final](../ROTEIRO.md) distingue implementação parcial de aceite completo. As sessões continuam preservadas como histórico; premissas substituídas remetem à decisão vigente.

| História | Sessão | Base implementada | Complemento/aceite planejado | Critério da rubrica relacionado |
|---|:-:|---|---|:-:|
| US-01 | 1, 2 | E1/E2 — aceite persistido; E7 — matriz e validação estrutural compartilhada, 202/outbox esclarecido | E14: jornada integrada | 1, 3 |
| US-02 | 2 | E2 — chave, fingerprint e conflito; E7 — regressão preservada | E14: jornada integrada | 3 |
| US-03 | 1, 4 | E7 — dados não pagáveis/histórico; E8 — fila durável Artemis e prova em processos separados | E14: jornadas integradas | 1, 2 |
| US-04 | 3 | E3/E5 — consulta por estado; E6/E7 — PACT e dados simulados atualizados | E11: autorização; E14: aceite | 4, 6 |
| US-05 | 3 | E3 — cache-aside/TTL; E9 — invalidação final e corrida com preenchimento testada | E11: acesso inclusive cache hit; E14: p95/expiração e aceite integrado | 4 |
| US-06 | 4 | E8 — publicação/redelivery limitados por fronteira; E10 — limites HTTP, circuitos independentes e recuperação sem retry extra | E12: recuperação operacional; E14: jornadas finais | 5 |
| US-07 | 4 | E8 — isolamento produtor e DLQ Artemis durável; transferência atômica de inválidos | E11/E12: acesso e reprocessamento auditado | 5 |
| US-08 | 4 | E4 — consumidor idempotente; E7 — ledger externo simulado, resposta perdida/rollback recuperáveis | E12/E14: replay operacional e recuperação integrada | 3 |
| US-09 | 4 | E9 — três eventos/outbox/assinaturas; E10 — correlação HTTP validada e preservada até eventos/invalidação | E11/E12: proteção e recuperação; E14: aceite integrado | 2, 3 |
| US-10 | 5 | E10 — correlação ponta a ponta e logs mínimos, sem trilha completa | E11/E12: auditoria e replay | 7, 9 |
| US-11 | 5 | Sem autorização implementada | E11: proteção de todos os canais; E14: testes de acesso | 1, 7 |
| US-12 | 5 | Premissa de retenção documentada | E12: política, marco e limites; expurgo automático fora do MVP | 7 |

US-09–12 são **requisitos do tema**, não opcionais automáticos. A tecnologia Kafka pode ser opcional na rubrica geral; isso não elimina os eventos acordados na Sessão 4. Mudanças de escopo exigem decisão explícita. E14 reúne as evidências de aceite e **E15 é o fechamento, somente depois**.
