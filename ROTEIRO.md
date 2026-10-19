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
| `projeto-final-grupo/` | E0–E3 implementadas; build e testes automatizados e HTTP passaram. A consulta já usa cache-aside e fallback da opção C; leitura de estados finais aguarda o serviço de registro (E4/E5). |

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
- [x] Repositório do grupo com estrutura do brief §3 (`README`, `AVALIACAO.md`, `docs/adr`, `docs/arquitetura.md`, `pom.xml`)
- [x] Módulos: `shared-contracts`, `boleto-emissor` (8081), `boleto-registro` (8082), `boleto-consulta` (8083)
- [x] `mvn clean verify` verde no perfil `plano-b-jvm` (perfil ativo por padrão)
- [x] **Declarar perfil de execução** no README (B, pois a turma não usa Docker)
**Pronto quando:** build e testes passam; falta conferir manualmente cada app com `spring-boot:run` e seu health check.

## E1 — DDD e bases segregadas (Aula 1) — *critérios 1 e 7*
**Referência:** `aula-01/ESTADO.md`, `ADR-001-bases-segregadas.md`, plano aula-01 §7 (cenário PIX completo)
**Conceito:** linguagem ubíqua, event storming, bounded context, agregado.
**Fazer (em grupo, no papel antes do código):**
- [x] Rascunho do event storming documentado com o fluxo de 8 passos, eventos, comandos, atores, políticas e **hotspots**; grupo ainda deve revisar
- [x] Agregado `Boleto` e VOs definidos conceitualmente (sem classe de domínio implementada nesta etapa)
- [x] `docs/arquitetura.md` com diagrama de contextos
- [x] **ADR-002** (bases segregadas), **ADR-003** (corte em 3 serviços) e **ADR-004** (estado inicial — opção C discutida e aceita pelo grupo)
- [x] Código: `boleto-emissor` com `POST /boletos` validando campos essenciais → `202` / `400`; cada serviço com **seu H2**; testes automatizados verdes
> **Hotspot decidido para o MVP (ADR-004):** a consulta tenta o registro; se ainda não encontrou o boleto, busca o aceite no emissor e devolve `AGUARDANDO_REGISTRO`. A opção C foi discutida e aceita pelo grupo e está implementada em E2/E3.

## E2 — Idempotência e consistência (Aula 2) — *critério 3*
**Referência:** `aula-02/ESTADO.md`, plano aula-02 §4–§7
**Conceito:** SAGA/compensação, idempotência, outbox, mito do exactly-once.
**Fazer:**
- [x] Emissor persiste o aceite (H2 próprio) com **chave única (convênio + nº controle)**
- [x] Reenvio igual → mesma resposta (202, mesmo id); mesma chave + conteúdo diferente → **409**
- [x] ADR-005 criada como proposta: sem SAGA síncrona/outbox na E2; reavaliar outbox na E4 com o grupo
- [x] Testes: reenvio igual cria um só registro; conflito não cria outro; concorrência, escala decimal e leitura interna cobertos
> O gabarito faz aqui uma SAGA **síncrona** (emissor→REST→gravador) que a Aula 4 **substitui** por fila.
> Não implementamos a versão síncrona: a E2 persiste o aceite; a decisão sobre outbox está na ADR-005 e deve ser ratificada pelo grupo.

## E3 — Cache na consulta (Aula 4) — *critério 4*
**Referência:** `aula-03/` (`ConsultaService`, `CacheConfig`), plano aula-04
**Conceito:** cache-aside, TTL, invalidação, 404 × falha técnica.
**Fazer:**
- [x] `GET /boletos/{id}`: cache → miss → registro; se 404, fallback ao aceite persistido no emissor (opção C); popula cache quando encontrado
- [x] Caffeine (Plano B) atrás de `@Cacheable`
- [x] TTL provisório curto para transitório (30 s) e longo para finais (10 min); 404 e falhas não são cacheados
- [x] Resposta só expõe linha digitável/código de barras se `REGISTRADO`; rejeição só expõe código/motivo de negócio
- [x] ADR-006 registra TTL, invalidação por evento e write-through como alternativas; decisão provisória
- [x] Testes: cache hit não chama fontes; falha técnica ≠ 404; fallback, projeção dos estados e UUID inválido cobertos
> **Limite desta etapa:** `boleto-registro` ainda não tem endpoint/dados de consulta. O caminho de E3 exercita o 404 do registro e o fallback no emissor; estados finais serão servidos quando o registro for implementado.

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
- [ ] ADR-007 retry/DLQ

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
