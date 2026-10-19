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
- **Dado** que não é possível aceitar com segurança (ex.: broker indisponível), **então** responde **503** e não 202.

### US-02 — Idempotência da emissão
**Como** sistema emissor, **quero** reenviar a mesma solicitação sem duplicar boleto.

- **Dado** convênio + número de controle já aceitos com **mesmo conteúdo**, **então** devolve o mesmo identificador e a resposta original (202), sem novo boleto.
- **Dado** a mesma chave com **conteúdo diferente**, **então** **409 Conflict**.

### US-03 — Registrar o título na registradora (assíncrono)
**Como** time de plataforma, **quero** que o registro aconteça por consumidor de fila, **para** suportar picos sem perder solicitações.

- **Dado** um aceite, **quando** a mensagem é consumida, **então** o consumidor chama a registradora e atualiza o estado para `REGISTRADO` (com linha digitável, código de barras, id definitivo, data/hora) ou `REJEITADO` (com código e motivo).
- **Dado** a resposta ao chamador, **então** ela **não espera** o registro.
- **Dado** que toda solicitação aceita deve terminar `REGISTRADO` ou `REJEITADO`, **então** o histórico das transições é guardado.

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
- **Dado** registradora indisponível/timeout/5xx, **então** o sistema retenta com backoff **[PREMISSA]** (3×; 1 s/2 s/4 s; timeout 5 s) sem descartar a mensagem.

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

| História | Sessão | Etapa do `ROTEIRO.md` | Critério da rubrica |
|---|:-:|:-:|:-:|
| US-01 | 1, 2 | E1 | 1 |
| US-02 | 2 | E2 | 3 |
| US-03 | 1, 4 | E4 | 2 |
| US-04 | 3 | E3 | 4 |
| US-05 | 3 | E3 | 4 |
| US-06 | 4 | E5 | 5 |
| US-07 | 4 | E5 | 5 |
| US-08 | 4 | E4 | 3 |
| US-09 | 4 | opcional (Kafka/outbox) | 2, 3 |
| US-10–12 | 5 | opcional / ADR | 7 |
