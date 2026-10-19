# Requisitos — Central de Cobrança e Boletos

Pacote de **elicitação de requisitos** do projeto final do grupo (BE-JV-010). Segue o mesmo formato do pacote do projeto-guia (`../../projeto-guiado-pix/requisitos`), construído a partir do material original do grupo (`../../../central_cobranca_boletos_elicitacao.docx`), **mantendo todas as decisões já tomadas** e acrescentando o detalhe que faltava para começar a desenvolver.

## O sistema, em uma frase

Uma **Central** que **recebe solicitações de emissão de boletos**, aceita-as de forma assíncrona, **registra o título na instituição registradora** (sistema externo, simulado no projeto) e permite **consultar o estado** do boleto. Ela **não movimenta dinheiro**: pagamento e liquidação ficam fora.

## O que tem neste pacote

| Arquivo | O que é |
|---|---|
| `personas.md` | Ficha de cada participante (papel, objetivos, jeito de falar, vieses). |
| `sessao-1-kickoff.md` | Contexto, problema, escopo, estados. |
| `sessao-2-emissao.md` | Campos, formatos, idempotência, resposta 202, erros. |
| `sessao-3-consulta-e-desempenho.md` | Consulta, cache-aside, invalidação, 404 × falha técnica. |
| `sessao-4-confiabilidade-e-integracoes.md` | Retentativa, DLQ, idempotência, outbox, eventos. |
| `sessao-5-compliance-e-fechamento.md` | LGPD, auditoria, retenção, fechamento. |
| `user-stories.md` | Documento compilado pela PO — **primeira fonte de verdade**. |
| `glossario-e-regras.md` | Glossário, máquina de estados, códigos da registradora, RNFs, premissas e pontos em aberto. |

## Como usar

1. Comece pelas `user-stories.md`.
2. Recorra às transcrições para entender **por que** cada decisão foi tomada.
3. **Em divergência, a transcrição prevalece** sobre a user story.
4. Valores numéricos marcados como **[PREMISSA]** são hipóteses didáticas (o material original os listava como "a validar"). Vocês podem alterá-los, desde que registrem num **ADR**.

## O que foi acrescentado em relação ao documento original

| Lacuna do original | Onde ficou resolvida |
|---|---|
| Não havia user stories nem critérios de aceite | `user-stories.md` |
| Campos listados sem tipo/tamanho/obrigatoriedade | Sessão 2 + `glossario-e-regras.md` §2 |
| Regras condicionais (juros, multa…) sem detalhe | Sessão 2 + glossário §2 |
| Corpo dos erros (400/409/404/503) não definido | Sessão 2/3 + glossário §3 |
| Estados sem transições; sem tratamento de falha técnica | Glossário §1 |
| Códigos da registradora "a validar" | Glossário §4 (hipótese didática) |
| Sem números de RNF (latência, pico, TTL, retry, timeout) | Glossário §5 **[PREMISSA]** |
| Eventos sem payload mínimo | Sessão 4 + glossário §6 |
| Auditoria/LGPD sem campos nem regras de log | Sessão 5 + glossário §7 |
| Sem rastreabilidade história → sessão → etapa do projeto | `user-stories.md` (tabela final) |

## Relação com o projeto-guia

Mapeia os padrões do módulo: DDD (emissão · registro · consulta), aceite assíncrono, cache-aside, fila + retry + DLQ, idempotência, outbox/eventos, contrato entre serviços. Detalhes técnicos de implementação ficam em `../ROTEIRO.md`; **este pacote é sobre o quê e o porquê**, não sobre o como.
