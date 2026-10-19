# Roteiro — Central de Cobrança/Boletos até o nível da Aula 6

> Objetivo: levar o projeto do grupo ao mesmo nível do `projeto-guiado-pix` após a Aula 6
> (RabbitMQ + retry + DLQ), **transferindo os padrões** — não copiando o PIX.
> Banca: **26/10**. Este roteiro é para aprender: cada etapa tem *conceito → decisão → código → prova*.

---

## 0. Onde estamos e o que o grupo já tem

| Insumo | O que é |
|---|---|
| `projeto-final.pdf` / `projeto-final-grupo/brief-alunos.md` | Regras: CORE, entrega, rubrica (9 critérios), banca |
| `central_cobranca_boletos_elicitacao.docx` | Personas, 5 sessões de requisitos e glossário do **nosso** tema |
| `projeto-guiado-pix/gabarito/aula-0N/` | Código de referência de cada aula (+ `ESTADO.md`) |
| `plano-de-aulas/aula-0N-aluno.md` | Teoria de cada aula |
| `projeto-final-grupo/` | Só o brief. **Nenhum código ainda** → começamos do zero |

### ⚠️ Atenção à numeração (pega muita gente)
As pastas do gabarito **não** batem 1:1 com os planos de aula:

| Plano de aula (teoria) | Pasta do gabarito (código) | Tema |
|---|---|---|
| Aula 1 | `aula-01` | DDD, bases segregadas |
| Aula 2 | `aula-02` | Consistência, SAGA, idempotência |
| Aula 3 | — (sem código) | Panorama de comunicação (síncrono/assíncrono, comando/evento) |
| Aula 4 | `aula-03` | Cache |
| Aula 5 | `aula-04` | Assíncrono (producer/consumer) |
| **Aula 6** | **`aula-05`** | **RabbitMQ: exchange, retry, DLQ** ← onde paramos |
| Aula 7 | `aula-06` | Kafka (tópico de evento) |
| Aula 8 | `aula-07` / `aula-08` | Resiliência / Contract test (PACT) |

**Meta deste roteiro = `gabarito/aula-05`** (conteúdo da Aula 6). Kafka, resiliência e PACT
ficam como "próximos passos" (§ 7) — mas **PACT é CORE**, então não pode ser esquecido.

---

## 1. Tradução PIX → Boletos

| PIX (gabarito) | Boletos (nosso projeto) | Diferença importante |
|---|---|---|
| `comprovante-emissor` | `boleto-emissor` | Idempotência por **convênio + nº controle**; reenvio diferente → **409** |
| `comprovante-gravador` | `boleto-registro` | Além de gravar, **registra na registradora** (simulada) e muda estado |
| `comprovante-consulta` | `boleto-consulta` | Dado **muda** (AGUARDANDO→REGISTRADO/REJEITADO) → cache precisa de invalidação |
| `GravarComprovanteCommand` | `RegistrarBoletoCommand` | Comando = mensagem na fila |
| UUID gerado no emissor | `identificador` interno gerado no aceite | Chave de idempotência do consumidor |
| Comprovante imutável | Boleto com **máquina de estados** | O PIX nunca fica "velho"; o boleto fica |
| Falha = DLQ | Falha técnica = retry/DLQ; **rejeição da registradora = estado final `REJEITADO`, NÃO DLQ** | Distinção de negócio × técnica |

Estados (Sessão 1): `AGUARDANDO_REGISTRO → REGISTRADO | REJEITADO`.
Fora do MVP: pagamento, baixa, cancelamento, notificação efetiva.

---

## 2. Visão geral das etapas

```
E0 Fundação ─ E1 DDD ─ E2 Idempotência ─ E3 Cache ─ E4 Assíncrono ─ E5 Retry+DLQ ─ E6 Fechamento
 (esqueleto)  (corte)   (409/estados)    (consulta) (fila)         (resiliência)   (docs/AVALIACAO)
```

Cada etapa = **1 commit/tag** (`etapa-0`, `etapa-1`…) → evidência de processo (exigida no brief).

---

## E0 — Fundação (esqueleto Maven)
**Referência:** `gabarito/aula-01/pom.xml`, módulos vazios, `ambiente/checklist-semana-0.md`
**Conceito:** multi-módulo Maven, profiles A/B/C (`plano-b-jvm`), `shared-contracts`.
**Fazer:**
- [ ] Repositório do grupo com estrutura do brief §3 (`README`, `AVALIACAO.md`, `docs/adr`, `docs/arquitetura.md`, `pom.xml`)
- [ ] Módulos: `shared-contracts`, `boleto-emissor` (8081), `boleto-registro` (8082), `boleto-consulta` (8083)
- [ ] `mvn clean compile` verde no perfil `plano-b-jvm`
- [ ] **Declarar perfil de execução** no README (B, pois a turma não usa Docker)
**Pronto quando:** compila sem erro e cada app sobe com `spring-boot:run`.

## E1 — DDD e bases segregadas (Aula 1) — *critérios 1 e 7*
**Referência:** `aula-01/ESTADO.md`, `ADR-001-bases-segregadas.md`, plano aula-01 §7 (cenário PIX completo)
**Conceito:** linguagem ubíqua, event storming, bounded context, agregado.
**Fazer (em grupo, no papel antes do código):**
- [ ] Event storming com o fluxo de 8 passos do docx: eventos (`EmissaoAceita`, `BoletoRegistrado`, `RegistroRejeitado`), comandos, atores, políticas, **hotspots**
- [ ] Definir o agregado `Boleto` (entidade) e VOs (`Valor`, `Pagador`, `Convenio`…)
- [ ] `docs/arquitetura.md` com diagrama de contextos
- [ ] **ADR-001** (bases segregadas), **ADR-002** (por que 3 serviços e não 1 — o "corte")
- [ ] Código: `boleto-emissor` com `POST /boletos` validando campos essenciais → `202` / `400`; cada serviço com **seu H2**
> **Decisão em aberto (hotspot!):** recém-emitido, a consulta precisa achar `AGUARDANDO_REGISTRO`
> (Sessão 3), mas o `boleto-registro` ainda não gravou nada. Quem guarda o estado inicial?
> Opções: (a) emissor persiste o aceite e a consulta cai nele no fallback; (b) emissor também publica…
> Isso vira ADR — é exatamente o tipo de trade-off que a rubrica quer.

## E2 — Idempotência e consistência (Aula 2) — *critério 3*
**Referência:** `aula-02/ESTADO.md`, plano aula-02 §4–§7
**Conceito:** SAGA/compensação, idempotência, outbox, mito do exactly-once.
**Fazer:**
- [ ] Emissor persiste o aceite (H2 próprio) com **chave única (convênio + nº controle)**
- [ ] Reenvio igual → mesma resposta (202, mesmo id); mesma chave + conteúdo diferente → **409**
- [ ] ADR-003: SAGA/outbox — o que vamos (ou não) implementar e por quê
- [ ] Teste: enviar 2× a mesma emissão → 1 só registro
> O gabarito faz aqui uma SAGA **síncrona** (emissor→REST→gravador) que a Aula 4 **substitui** por fila.
> Recomendação: **não implemente a versão síncrona**; pegue só a idempotência + compensação como conceito.

## E3 — Cache na consulta (Aula 4) — *critério 4*
**Referência:** `aula-03/` (`ConsultaService`, `CacheConfig`), plano aula-04
**Conceito:** cache-aside, TTL, invalidação, 404 × falha técnica.
**Fazer:**
- [ ] `GET /boletos/{id}`: cache → miss → fonte persistente → popula cache
- [ ] Caffeine (Plano B) atrás de `@Cacheable`
- [ ] **Diferença do PIX:** estado muda ⇒ política: **TTL curto para estados transitórios**, longo para finais; não cachear 404
- [ ] Resposta só expõe linha digitável/código de barras se `REGISTRADO` (Sessão 3)
- [ ] ADR-004 estratégia de cache (alternativas: TTL só, invalidação por evento, write-through)
- [ ] Teste: 2ª consulta não chega na fonte; falha técnica ≠ 404

## E4 — Comunicação assíncrona (Aula 5) — *critério 2*
**Referência:** `aula-04/` (`GravacaoPublisher`, `GravacaoListener`), plano aula-05
**Conceito:** producer/consumer, mensagem × evento, at-least-once, consumidor idempotente.
**Fazer:**
- [ ] Emissor publica `RegistrarBoletoCommand` e responde 202 sem esperar
- [ ] `boleto-registro` consome (`@RabbitListener`), grava e simula a registradora
- [ ] Consumidor **idempotente** (mesmo id reentregue não duplica) + teste que prova
- [ ] README declara a **garantia de entrega** (at-least-once + idempotência)
- [ ] Broker: Plano B (Qpid embarcado) ou Plano C (in-process) — **declarar fallback**

## E5 — Topologia, retry e DLQ (Aula 6) — *critério 5*
**Referência:** `aula-05/` (`MessagingConfig`, `application.yml`, `GravacaoListener`), plano aula-06
**Conceito:** exchange/binding, ack, backoff, DLQ, poison message.
**Fazer:**
- [ ] `registro.ex` → `registro.q` (+ `registro.dlx` → `registro.dlq`)
- [ ] Retry com backoff (3×) para falha **transitória** (registradora fora do ar)
- [ ] Falha **permanente** (mensagem inválida) → DLQ com listener que loga **sem vazar dados** (Sessão 5)
- [ ] Rejeição de negócio → estado `REJEITADO` (não DLQ)
- [ ] Prova: mensagem envenenada → 3 tentativas → DLQ; mensagem boa segue
- [ ] ADR-005 retry/DLQ

## E6 — Fechamento da base
- [ ] `README.md` (tema, arquitetura, como rodar, perfil)
- [ ] `AVALIACAO.md` preenchido (critério → evidência)
- [ ] Seção honesta de **uso de IA** (critério 8)
- [ ] `mvn verify` verde

---

## 6. Cobertura da rubrica após E6

| # | Critério | Peso | Etapa |
|---|---|:-:|---|
| 1 | Decomposição | 15 | E1 |
| 2 | Assíncrono | 15 | E4 |
| 3 | Idempotência | 12 | E2, E4 |
| 4 | Cache | 10 | E3 |
| 5 | Resiliência | 12 | E5 |
| 6 | Testabilidade (**falta PACT**) | 13 | parcial → §7 |
| 7 | ADRs | 13 | todas |
| 8 | IA | 5 | E6 |
| 9 | Execução | 5 | E0/E6 |

## 7. Depois (para completar o CORE e chegar à banca)
1. **Contract test PACT** (`gabarito/aula-08`, plano aula-08-contract) — **obrigatório no CORE**
2. Opcional: Kafka/tópico `boleto-registrado` (`aula-06`), `@RetryableTopic` + circuit breaker (`aula-07`)
3. Opcional: outbox transacional (pedido pelas Sessões 4)

## 8. Sugestão de calendário (hoje 19/10 → banca 26/10)
| Dia | Etapas |
|---|---|
| 19–20 | E0 + E1 (em grupo: decisões e ADRs) |
| 21 | E2 + E3 |
| 22–23 | E4 + E5 |
| 24 | PACT (§7.1) |
| 25 | E6 + ensaio da defesa |
