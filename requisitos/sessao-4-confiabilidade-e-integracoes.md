# Sessão 4 — Confiabilidade e integrações

**Projeto:** Central de Cobrança e Boletos · **Data:** 27/04/2026, 14h00–14h30 · **Duração:** 30 min · **Plataforma:** Microsoft Teams
**Participantes:** Marina, Daniel, Caio, Téo, Roberto e Sandra

> Trechos marcados com ➕ foram acrescentados ao original.

---

**Marina:** Precisamos provar que o fluxo assíncrono não perde nem duplica boletos.

**Daniel:** Toda solicitação aceita deve terminar REGISTRADA ou REJEITADA, com histórico. Falha temporária gera retentativa.

**Caio:** A registradora pode ficar indisponível. A mensagem não pode ser descartada.

➕ **Caio:** Classificando as falhas: **temporárias** — registradora indisponível, timeout, erro 5xx → **retentar**. **Rejeição de negócio** — a registradora recebeu e recusou (ex.: CPF/CNPJ inválido, convênio inativo, vencimento anterior à emissão) → **não é falha técnica**: o boleto vai para `REJEITADO` com código e motivo; **não retenta e não vai para a DLQ**. **Permanente/técnica** — mensagem malformada ou que nunca poderá ser processada → **DLQ**.

**Daniel:** A entrega pode repetir. O consumidor e a integração devem ser idempotentes para que o efeito de negócio não se repita.

➕ **Daniel:** Idempotência do consumidor pelo **identificador interno**: se o boleto já está em estado final, reprocessar a mensagem não muda nada nem chama a registradora de novo.

**Sandra:** E uma mensagem inválida?

**Daniel:** Após o limite de tentativas, vai para DLQ com contexto da falha. DLQ é quarentena, não descarte.

➕ **Daniel:** **[PREMISSA]** 3 tentativas com backoff exponencial (1 s, 2 s, 4 s), timeout de 5 s na chamada à registradora. A política real depende do contrato — fica como ponto a validar.

**Téo:** Quero eventos para Emissão Aceita, Boleto Registrado e Registro Rejeitado. Antifraude não pode bloquear o registrador.

➕ **Téo:** Payload mínimo de cada evento: `id_evento`, `tipo`, `identificador_boleto`, `convenio`, `status`, `data_hora`, `correlation_id`. **Sem** nome, documento nem endereço do pagador — quem precisar disso consulta pelo identificador.

**Daniel:** Publicaremos em tópico. Para evitar o buraco entre gravar e publicar, usaremos outbox transacional.

➕ **Daniel:** O resultado do registro e a intenção de publicar o evento são gravados **na mesma transação**; um publicador separado lê a outbox e envia ao tópico, podendo repetir (consumidores toleram duplicata).

**Roberto:** Notificação ao cliente entra agora?

**Marina:** A publicação do evento entra. E-mail, SMS ou push ficam fora do núcleo do MVP.

## Decisões da sessão
1. Retentativas para falhas temporárias.
2. DLQ para falhas permanentes.
3. ➕ Rejeição de negócio = estado `REJEITADO` (não é retry, não é DLQ).
4. Processamento idempotente suporta reentrega.
5. Eventos de domínio desacoplados (payload mínimo, sem dados pessoais).
6. Outbox garante intenção de publicação junto ao resultado.
7. Notificação efetiva fica fora do MVP.
8. ➕ Garantia de entrega: **at-least-once + consumidor idempotente** (não se promete exactly-once).

## Action items
- **Daniel:** detalhar retentativa, DLQ e outbox.
- **Caio:** classificar falhas.
- **Téo:** definir payload mínimo.

## Glossário acrescentado
- **DLQ:** quarentena de mensagens não processadas.
- **Outbox:** registro transacional da intenção de publicar evento.
- **Evento de domínio:** fato relevante já ocorrido.
- **Idempotência do consumidor:** repetição sem duplicar efeito de negócio.
- **Falha temporária × permanente × rejeição de negócio:** ver decisão 3.
