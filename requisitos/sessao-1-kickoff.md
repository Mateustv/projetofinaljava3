# Sessão 1 — Kickoff: contexto, problema e escopo

**Projeto:** Central de Cobrança e Boletos · **Data:** 06/04/2026, 14h00–14h30 · **Duração:** 30 min · **Plataforma:** Microsoft Teams
**Participantes:** Marina, Roberto, Sandra, Daniel e Caio
**Ausente:** Dra. Helena (reservou retenção, privacidade e auditoria para a Sessão 5)

> Diálogo do original preservado; trechos marcados com ➕ foram acrescentados para dar contexto.

---

**Marina:** Hoje vamos entender a dor, estabelecer o escopo e criar uma linguagem comum.

**Sandra:** O cliente empresa envia uma cobrança e não sabe se ela foi apenas recebida, se já foi registrada ou se foi rejeitada. A operação consulta vários sistemas e encontra respostas divergentes.

**Roberto:** Isso gera contato repetido e atraso no envio da cobrança ao pagador.

➕ **Sandra:** Em semana de fechamento recebemos centenas de chamados só perguntando "meu boleto saiu?". Cada chamado custa tempo do atendente e, pior, o cliente reenvia a mesma cobrança "por garantia" e acaba duplicando.

**Caio:** Precisamos separar emissão de registro. Emitir é aceitar a solicitação e criar a identidade da cobrança. Registrar é obter a confirmação da instituição registradora.

➕ **Marina:** Quem são os clientes emissores?

➕ **Sandra:** Empresas que cobram seus clientes: ERPs e canais corporativos chamam a Central sistema a sistema. O pagador final só recebe o boleto.

**Daniel:** A Central receberá a emissão, colocará o trabalho numa fila de registro e acompanhará o processamento. Ela não movimenta dinheiro.

**Sandra:** A maior dor é "acabei de emitir e não aparece". A segunda é exibir como pronto um boleto que ainda não pode ser pago.

**Daniel:** Teremos estados explícitos: AGUARDANDO_REGISTRO, REGISTRADO e REJEITADO.

➕ **Caio:** E aqui vale um alerta: um boleto só pode ser pago depois de registrado. Se mostrarmos linha digitável antes disso, o pagador tenta pagar e o banco recusa.

➕ **Marina:** Qual é o pico que a gente espera?

➕ **Roberto:** Virada de mês e dia 5/10 concentram a maior parte. Não posso perder solicitação nesses dias. **[PREMISSA]** pico de ~500 emissões/s; o número real precisa ser validado com a operação.

**Roberto:** MVP: emissão, registro assíncrono e consulta rápida. Pagamento, alteração, cancelamento, protesto e baixa comandada ficam fora.

**Marina:** Fechado. O sistema deve suportar picos sem perder solicitações e sem confundir aceite com registro.

## Decisões da sessão
1. MVP: emissão, fila de registro e consulta por identificador.
2. Aceite e registro são fatos diferentes.
3. Fora do MVP: pagamento, liquidação financeira, alteração, cancelamento, protesto, negativação e baixa comandada.
4. Status reais e suporte a picos são requisitos essenciais.
5. Estados: `AGUARDANDO_REGISTRO`, `REGISTRADO`, `REJEITADO`.
6. ➕ Os clientes emissores são **sistemas** (ERPs/canais). O pagador final nunca chama a Central.

## Action items
- **Marina:** consolidar linguagem ubíqua.
- **Daniel:** detalhar aceite assíncrono.
- **Sandra:** levar o caso recém-emitido à Sessão 3.
- **Caio:** listar retornos da registradora.

## Glossário acrescentado
- **Emissão:** recebimento e validação inicial da cobrança.
- **Registro:** confirmação do título pela registradora.
- **Título:** representação da cobrança.
- **Boleto recém-emitido:** solicitação aceita ainda aguardando registro.
