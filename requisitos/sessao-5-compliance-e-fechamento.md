# Sessão 5 — Compliance, segurança, retenção e fechamento

**Projeto:** Central de Cobrança e Boletos · **Data:** 04/05/2026, 14h00–14h30 · **Duração:** 30 min · **Plataforma:** Microsoft Teams
**Participantes:** Marina, Helena, Téo, Daniel, Caio e Roberto

> Trechos marcados com ➕ foram acrescentados ao original.

---

**Helena:** A Central trata identificação, documentos, endereço, valor, vencimento e histórico. Precisamos de finalidade, base legal, minimização e acesso legítimo.

**Daniel:** Isso se aplica a banco, cache, fila, DLQ, logs e eventos?

**Helena:** Sim. Cada canal recebe apenas o necessário. A DLQ não é arquivo informal e evento interno também transporta dados protegidos.

**Téo:** A auditoria deve registrar emissão, consulta, mudança de estado e reprocessamento.

**Helena:** Registro mínimo: ator ou sistema, ação, título, data/hora, resultado e correlação. Não coloque o boleto inteiro no log.

➕ **Helena:** Em log, documento e nome saem **mascarados** (ex.: `***.456.789-**`). Valor e endereço, nem isso: não entram no log.

**Caio:** Qual o prazo de retenção?

**Helena:** O prazo real deve vir da matriz institucional e das obrigações aplicáveis. Para o exercício, adotaremos como hipótese didática cinco anos após o encerramento da cobrança, claramente marcada para validação.

➕ **Daniel:** Entendi "encerramento" como o momento em que o boleto chega a estado final (REGISTRADO sem pendência de cobrança ou REJEITADO). Isso precisa ficar no glossário como marco inicial da retenção.

**Daniel:** Cache terá prazo curto e herdará autorização. DLQ terá acesso restrito e reprocessamento auditado.

➕ **Téo:** Quem acessa? **Sistema emissor** só emite e consulta os **seus** convênios; **atendimento** consulta; **operação/SRE** acessa DLQ e reprocessa. Acesso sempre por necessidade.

**Roberto:** O que é obrigatório no MVP?

**Helena:** Controle de acesso, auditoria, retenção formal, criptografia conforme padrões institucionais, mascaramento e payload mínimo.

**Marina:** Fechamento: 202 e idempotência; fila; estados; cache; retentativas, DLQ e outbox; eventos; e proteção em todos os canais.

## Decisões da sessão
1. LGPD e segurança se aplicam a todos os canais (banco, cache, fila, DLQ, logs, eventos).
2. Minimização e acesso por necessidade legítima.
3. Auditoria sem copiar dados excessivos para logs (campos mínimos abaixo).
4. Prazo real depende de validação; hipótese didática: cinco anos após encerramento da cobrança.
5. Criptografia, mascaramento e acesso restrito à DLQ entram no MVP.
6. ➕ Reprocessamento da DLQ é ação auditada e restrita à operação.
7. ➕ Em logs, documentos mascarados; valor e endereço não são registrados.

## Registro de auditoria — campos mínimos
`ator/sistema` · `ação` (EMISSAO, CONSULTA, MUDANCA_ESTADO, REPROCESSAMENTO) · `identificador_boleto` · `data_hora` · `resultado` · `correlation_id`

## Action items
- **Helena:** validar matriz de retenção e base legal.
- **Téo:** detalhar auditoria.
- **Daniel:** garantir proteção equivalente em todos os componentes.
- **Marina:** marcar premissas nas user stories.

## Glossário acrescentado
- **Trilha de auditoria:** registro protegido de ator, ação, título, momento e resultado.
- **Minimização:** uso apenas dos dados necessários.
- **Correlação:** identificador para reconstruir o fluxo entre serviços.
- **Retenção:** prazo formal com marco inicial documentado.
