# Sessão 3 — Consulta e desempenho

**Projeto:** Central de Cobrança e Boletos · **Data:** 20/04/2026, 14h00–14h30 · **Duração:** 30 min · **Plataforma:** Microsoft Teams
**Participantes:** Marina, Sandra, Daniel, Roberto e Caio

> Trechos marcados com ➕ foram acrescentados ao original.

---

**Sandra:** O cliente consulta logo após emitir e recebe "não encontrado", o que incentiva reenvio e duplicidade.

**Daniel:** Se nós emitimos um identificador, a consulta deve encontrar ao menos o estado inicial. Não devemos devolver 404 para algo aceito.

➕ **Marina:** Mas o gravador/registro ainda não processou. De onde vem esse estado inicial?

➕ **Daniel:** Esse é o ponto delicado: o **aceite precisa ser persistido na emissão**, antes de responder 202. A consulta deve conseguir ler esse estado inicial mesmo que o registro ainda não tenha processado a fila. *(O desenho — onde fica essa informação e quem a serve — é decisão da equipe de desenvolvimento, a registrar em ADR.)*

**Caio:** A resposta distingue AGUARDANDO_REGISTRO, REGISTRADO e REJEITADO. Somente REGISTRADO expõe dados utilizáveis de pagamento.

➕ **Caio:** Em cada estado: **AGUARDANDO_REGISTRO** → identificador, status, data/hora do recebimento. **REGISTRADO** → tudo isso + linha digitável, código de barras, identificador definitivo da registradora, data/hora do registro. **REJEITADO** → identificador, status, **código e motivo da rejeição** (sem linha digitável).

**Roberto:** A leitura precisa ser rápida e suportar muitas repetições.

➕ **Marina:** Qual a meta de latência?

➕ **Roberto:** **[PREMISSA]** p95 abaixo de 100 ms em consulta com cache; leitura é ~20× mais frequente que emissão. A meta oficial precisa ser validada.

**Daniel:** Usaremos cache-aside: cache primeiro; no miss, banco; se encontrado, devolve e popula o cache.

**Marina:** Como impedimos status antigo?

**Daniel:** Mudança de estado invalida ou atualiza a entrada. Estados transitórios terão prazo curto. O banco é a fonte persistente.

➕ **Daniel:** **[PREMISSA]** TTL de **30 s** para `AGUARDANDO_REGISTRO` e **10 min** para `REGISTRADO`/`REJEITADO` (estados finais não mudam). Não se cacheia "não encontrado".

**Sandra:** E falha técnica?

**Daniel:** Dependência indisponível não vira "não encontrado". Identificador inexistente dá 404; falha temporária recebe resposta apropriada e rastreável.

➕ **Daniel:** Falha técnica → **503** com identificador de correlação para rastrear. Identificador inexistente → **404**. Identificador com formato inválido (não é UUID) → **400**.

**Marina:** No MVP, consulta por identificador. Busca por documento, período e situação fica para depois.

## Decisões da sessão
1. Consulta por identificador interno.
2. Cache-aside com invalidação por mudança de estado.
3. Identificador conhecido sempre retorna estado.
4. 404 somente para identificador inexistente.
5. Falha técnica não deve ser apresentada como ausência (503 rastreável).
6. Baixa latência e alto volume de leitura são RNFs.
7. ➕ Dados de pagamento só no estado REGISTRADO; REJEITADO expõe código e motivo.
8. ➕ TTL curto (30 s) para transitório e longo (10 min) para final **[PREMISSA]**.
9. ➕ "Não encontrado" não é cacheado.

## Action items
- **Daniel:** definir política de cache.
- **Sandra:** validar textos dos estados.
- **Caio:** revisar dados exibidos por estado.

## Glossário acrescentado
- **Cache-aside:** cache, seguido da fonte persistente em caso de miss.
- **Cache miss:** ausência no cache, não ausência do boleto.
- **Estado transitório:** estado que deve evoluir, como AGUARDANDO_REGISTRO.
- **Estado final:** REGISTRADO ou REJEITADO.
