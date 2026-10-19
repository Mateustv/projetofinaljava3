# ADR-004 — Quem guarda o estado inicial (AGUARDANDO_REGISTRO) para a consulta?

## Contexto
Sessão 3: *"Se nós emitimos um identificador, a consulta deve encontrar ao menos o estado inicial. Não devemos devolver 404 para algo aceito."* Mas o `boleto-registro` (dono do estado do título) só passa a conhecer o boleto **depois** de consumir a fila. Entre o 202 e o consumo existe uma **janela** em que só o `boleto-emissor` sabe que o boleto existe.

> **Esta é uma decisão importante da E1: ela afeta consulta, cache e eventos. O solicitante informou em 19/10/2026 que a opção C foi discutida e aceita pelo grupo.**

## Opções
- **A — Consulta lê só do registro, e o registro cria a linha no recebimento da mensagem.** Ainda deixa a janela (mensagem na fila, não consumida) → 404 para algo aceito. **Não atende.**
- **B — O emissor é o dono do estado e o registro devolve o resultado ao emissor.** Resolve a janela, mas inverte o fluxo e acopla emissor ↔ registro; consulta passa a ler do emissor (leitura pesada num serviço de escrita). **Rejeitada.**
- **C — Fallback em duas fontes:** consulta tenta o **registro**; se 404, tenta o **aceite no emissor** (endpoint interno de leitura) e devolve `AGUARDANDO_REGISTRO`; só então 404. Mantém cada serviço dono do seu dado (emissor = aceite; registro = título). Custo: na janela, 2 chamadas e a consulta conhece 2 fontes.
- **D — Read model próprio da consulta (CQRS)**, alimentado por eventos (`EmissaoAceita`, `BoletoRegistrado`, `RegistroRejeitado`). Maior desacoplamento e leitura mais rápida, mas exige eventos/outbox (E4+ e opcionais) e consistência eventual do próprio read model.

## Decisão
**Opção C para o MVP**, aceita pelo solicitante em 19/10/2026. A opção D fica registrada como evolução quando houver eventos/outbox. Em C, o cache usará **TTL curto (30 s)** para `AGUARDANDO_REGISTRO`, para não prender o estado transitório (detalhe em ADR da E3).

## Consequências
- A consulta não tem base própria no MVP; precisa de dois clientes REST internos (registro, emissor).
- O emissor ganha `GET /interno/boletos/{id}` (E2, quando persistir o aceite).
- Falha técnica em qualquer fonte → 503, **nunca** 404 (sessão 3).

## Status
**Aceita pelo grupo (conforme confirmação do solicitante) e implementada no fluxo E2/E3.** O emissor persiste o aceite e fornece leitura interna desde a E2; a consulta executa o fallback nesta E3.
