# ADR-005 — Confiabilidade Transacional: Padrão Transactional Outbox vs SAGA Síncrona

## Contexto

Ao processar a emissão de boletos, o sistema bancário assume um compromisso formal com a empresa cedente: uma vez devolvido o comprovante de aceite (`HTTP 202 Accepted`), a instituição garante juridicamente que a cobrança será submetida à câmara registradora centralizada.

Em arquiteturas distribuídas, a coordenação entre a persistência em banco de dados e a emissão de mensagens para um broker enfrenta o clássico problema da **Gravação Dupla (Dual-Write Problem)**:
- Se o emissor persistir o aceite no banco relacional e tentar publicar a mensagem na rede em passos separados:
  1. Caso o banco confirme o commit e o processo sofra um *crash* ou falha de rede antes de falar com o broker, o comando de registro é perdido para sempre. O boleto ficaria eternamente preso em `AGUARDANDO_REGISTRO`, a empresa acharia que a cobrança estava sendo processada e o sacado nunca receberia o título pagável.
  2. Caso a mensagem seja publicada antes do commit e o banco sofra um *rollback* (ex.: violação de integridade ou queda de conexão), o broker entregaria um comando de registro referente a uma cobrança que não existe formalmente no emissor (título fantasma).

Para contornar esse risco:
- Uma abordagem de **SAGA síncrona** ou chamada REST acoplada forçaria a requisição de emissão a esperar o processamento do registro, destruindo o throughput de 500 requisições/s e tornando a emissão refém da latência da registradora externa.
- Uma **Transação Distribuída (Two-Phase Commit / XA)** entre o banco relacional e o broker introduziria bloqueios de tabela de longa duração, complexidade operacional de coordenadores de transação e degradação severa da escalabilidade horizontal.

## Decisão

Adotar o padrão arquitetural **Transactional Outbox**:

1. Na **mesma transação ACID local** do banco de dados do `boleto-emissor`, são persistidos de forma indivisível:
   - A entidade de aceite do boleto (com chave de idempotência de negócio `convenio + numero_controle_cliente`);
   - O registro na tabela de outbox de comandos (`outbox_comandos`), contendo o payload do `RegistrarBoletoCommand`;
   - O registro na tabela de outbox de eventos (`outbox_eventos`), contendo o evento de domínio `EmissaoAceita`.
2. O `HTTP 202 Accepted` só é retornado ao cliente corporativo após o **commit bem-sucedido dessa transação local**. Se o banco falhar, nenhum dado é gravado e o cliente recebe `HTTP 503` para tentar novamente com segurança.
3. Um processo despachante assíncrono em segundo plano (*Outbox Dispatcher*) lê em lotes as mensagens pendentes e as publica no broker de mensageria com confirmação de entrega transacional JMS.
4. Somente após a confirmação de recebimento pelo broker (commit JMS), a mensagem na outbox é marcada como concluída/removida.
5. O consumidor no `boleto-registro` é projetado para operar com garantia **at-least-once**, absorvendo com idempotência qualquer eventual reentrega provocada por falha de confirmação na rede.

## Alternativas consideradas

- **SAGA Síncrona / Orquestração Bloqueante:** Rejeitada. Faria a emissão aguardar a disponibilidade e o tempo de resposta da câmara registradora externa (que pode demorar centenas de milissegundos ou segundos sob carga), inviabilizando o SLA de ingestão bancária.
- **Transação Distribuída 2PC (XA):** Rejeitada. Gera retenção de conexões e *locks* pessimistas distribuídos, além de apresentar altíssima fragilidade em cenários de partição de rede, contrariando o princípio de alta disponibilidade do Bounded Context de Emissão.
- **Publicação Direta em Memória sem Outbox:** Rejeitada sumariamente por violação grave de integridade bancária: qualquer reinício ou oscilação de rede entre o commit e o envio ao broker resultaria em perda silenciosa de cobranças.

## Consequências

- **Positivas:**
  - **Garantia Absoluta de Não-Perda de Cobranças:** Mesmo que o nó do emissor, o broker ou a rede caiam instantaneamente após o aceite, a intenção de registro está preservada no disco do banco de dados e será retomada automaticamente assim que o serviço for restabelecido.
  - **Desacoplamento Temporal Completo:** A thread de atendimento HTTP do cliente é liberada em menos de 100 ms, assegurando altíssima capacidade de absorção de picos de fim de mês.
  - **Eliminação de Dependências Distribuídas no Caminho Crítico:** O sucesso do aceite da cobrança depende única e exclusivamente da integridade do banco de dados local do emissor.
- **Custos / Mitigações:**
  - **Garantia de Entrega At-Least-Once:** Em caso de perda de confirmação de rede entre o broker e o emissor, uma mensagem da outbox pode ser despachada mais de uma vez. Isso transfere a responsabilidade de deduplicação para o consumidor no serviço de registro, que implementa **idempotência estrita baseada em chave e ledger de operações** (ADR-010).

## Status

Aceita e implementada na arquitetura de mensageria do sistema.
