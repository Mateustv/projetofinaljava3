# ADR-008 — Resiliência de Mensageria: Retentativas com Backoff, Contenção de Poison Pills e Quarentena (DLQ)

## Contexto

Em sistemas distribuídos bancários de alta criticidade, falhas operacionais e de rede são inevitáveis. No entanto, essas falhas possuem naturezas fundamentalmente distintas, que exigem tratamentos arquiteturais específicos:

1. **Falhas Transitórias de Infraestrutura:** Oscilações passageiras de conexões de socket, timeouts pontuais na câmara registradora externa ou concorrência momentânea de banco de dados. Essas falhas são auto-resolvíveis se o sistema aguardar um intervalo de recuperação.
2. **Falhas Permanentes e Mensagens Venenosas (*Poison Pills*):** Payloads com JSON truncado/corrompido, envelopes malformados ou incompatibilidades estruturais fatais de contrato.
   - *O Risco Crítico:* Se o consumidor de mensageria adotar retentativas cegas ou infinitas para uma mensagem venenosa, a mensagem com erro voltará continuamente para a cabeça da fila (*Head-of-Line Blocking*). Isso paralisará completamente a fila de processamento, impedindo que milhares de cobranças perfeitamente válidas acumuladas atrás dela sejam registradas.
3. **Rejeições por Regras de Negócio Bancárias:** Respostas formais da câmara registradora indicando recusa legítima da cobrança (ex.: *"Documento do pagador inválido"*, *"Convênio bloqueado"* ou *"Data de vencimento fora da regra"*).
   - *A Regra de Ouro:* Uma rejeição de negócio **não é uma falha técnica de transporte**. O processamento foi executado com sucesso e o resultado final foi a não-aprovação do título. Tentar reenviar uma rejeição para a fila gastaria recursos computacionais e geraria falso alarme de suporte técnico.

## Decisão

Adotar uma política rigorosa de **Resiliência com Orçamento Finito de Tentativas, Backoff Exponencial e Fila de Quarentena (Dead-Letter Queue - DLQ)**:

1. **Orçamento Estrito de Três Tentativas:**
   - 1ª tentativa: envio imediato;
   - 2ª tentativa (após 1ª falha temporária): espera mínima de **1 segundo**;
   - 3ª tentativa (após 2ª falha temporária): espera mínima de **5 segundos**.
2. **Transferência Atômica de Mensagens Venenosas:**
   - Mensagens com JSON corrompido, cabeçalhos inválidos ou contratos violados são classificadas como falhas permanentes e **transferidas imediatamente para a Quarentena (`registro.quarentena`)**, na mesma transação atômica que confirma a remoção (ACK) da mensagem original da fila de produção. Não gastam tempo nem retentativas de rede.
3. **Esgotamento de Tentativas e Isolamento:**
   - Se uma mensagem de comando esgotar as 3 tentativas sob falhas temporárias, ela é encaminhada pelo broker diretamente para a fila de quarentena durável. A fila de produção é imediatamente liberada para o processamento das próximas cobranças.
4. **Tratamento Adequado de Rejeição de Negócio:**
   - Quando a câmara registradora recusa o título, o `boleto-registro` persiste o estado definitivo `REJEITADO` com o código e descrição da recusa e confirma a mensagem com sucesso (ACK) no broker. Rejeições de negócio **nunca são enviadas para a quarentena nem sofrem retentativa**.
5. **Auditoria Forense de Falhas sem Vazamento de Dados Pessoais:**
   - O emissor mantém rastreabilidade em banco de comandos isolados (`/interno/outbox/falhas`), expondo apenas metadados técnicos (UUID, número de tentativas, tipo do erro e data da falha), sem expor payloads completos, documentos ou dados cadastrais sensíveis nos logs e nas respostas de diagnóstico.

## Alternativas consideradas

- **Retentativa Infinita sem Quarentena:** Rejeitada sumariamente. Um único payload malformado bloquearia toda a esteira de registro de boletos da instituição bancária.
- **Encaminhar Rejeições de Negócio para a DLQ:** Rejeitada. Poluiria as ferramentas de observabilidade e as filas de emergência de infraestrutura com eventos regulares de negócio que já foram concluídos e persistidos com sucesso.
- **Descarte Silencioso de Mensagens Falhadas:** Rejeitada. Violação grave de auditoria financeira; toda cobrança que não puder ser processada deve ficar preservada na quarentena para intervenção de engenharia e reconciliação.

## Consequências

- **Positivas:**
  - **Imunidade a Bloqueios de Fila (*Head-of-Line*):** Mensagens corrompidas são isoladas em milissegundos, garantindo que o fluxo de cobranças saudáveis continue operando em velocidade máxima.
  - **Auto-Recuperação Inteligente:** Flutuações de rede transitórias são superadas automaticamente pelo backoff sem demandar intervenção humana.
  - **Clareza Operacional:** Separação inequívoca entre incidentes de infraestrutura (Quarentena/DLQ) e respostas legítimas de negócio (`REJEITADO`).
- **Custos / Mitigações:**
  - Mensagens isoladas em quarentena exigem procedimentos de inspeção operacional para diagnóstico da causa raiz antes de qualquer decisão de reprocessamento manual.

## Status

Aceita e implementada na infraestrutura de mensageria e nos listeners dos serviços.
