# ADR-010 — Idempotência de Integração Externa (Ledger REQUIRES_NEW) e Contrato Funcional Não-Pagável

## Contexto

A integração entre o sistema bancário interno e a Câmara Registradora Centralizada (CIP/B3) envolve riscos operacionais, financeiros e regulatórios de alta gravidade:

1. **Risco de Cobranças Duplicadas de Tarifas Bancárias:**
   - Câmaras registradoras cobram tarifas por cada transação submetida e rejeitam sumariamente tentativas de re-registro com o mesmo número de controle.
   - Em arquiteturas distribuídas com semântica de mensageria *at-least-once*, se o consumidor do `boleto-registro` submeter a cobrança à câmara externa com sucesso, mas a aplicação sofrer um *crash* ou um *rollback* no banco local antes de persistir a entidade e enviar o ACK ao broker, a mensagem retornará à fila.
   - Na retentativa, se o consumidor invocasse a câmara externa novamente, o banco sofreria cobrança duplicada de tarifas ou receberia um erro de duplicidade que mascararia o fato de que o boleto já havia sido registrado com sucesso!
2. **Exigência Regulatória do Banco Central (Bacen / Febraban):**
   - A regulamentação bancária determina que **nenhum título de cobrança pode ser disponibilizado para pagamento sem que o registro esteja efetivamente concluído e confirmado na câmara central**.
   - Disponibilizar linha digitável ou código de barras para boletos em estado `AGUARDANDO_REGISTRO` ou `REJEITADO` induz o pagador a erro e viola a regulamentação do Sistema Financeiro Nacional.
3. **Segurança e Isolamento em Ambientes Não-Produtivos:**
   - Dados sintéticos de demonstração e homologação jamais podem conter sequências numéricas bancárias que possam ser acidentalmente interpretadas ou processadas por câmaras compensadoras reais.

## Decisão

Implementar uma estratégia rigorosa de **Idempotência Externa Durável via Ledger Transacional Independente** e **Contrato Funcional Seguro**:

1. **Padrão Ledger de Integração em Transação Isolada (`REQUIRES_NEW`):**
   - O adaptador da câmara registradora mantém uma tabela dedicada de auditoria de operações (`registradora_simulada_operacao`).
   - A submissão à registradora é encapsulada em uma transação própria do Spring Data com propagação **`Propagation.REQUIRES_NEW`**, totalmente isolada da transação local do serviço de registro.
   - A chave primária da operação é o UUID imutável do boleto.
   - Se ocorrer um *rollback* na transação de negócio do registro ou uma queda do serviço antes do ACK da mensagem, o registro da operação externa **não é desfeito**.
   - Na reentrega da mensagem pela fila, o adaptador consulta previamente o ledger: localizando a operação já efetivada, **recupera imediatamente a mesma resposta anterior** e devolve ao serviço local para que este conclua a persistência pendente, **sem realizar nenhuma nova chamada externa**.
2. **Geração Segura de Marcadores Não-Pagáveis:**
   - Títulos com registro aprovado recebem marcadores explícitos: `linha_digitavel = "NAO-PAGAVEL-SIM-LINHA-{uuid}"` e `codigo_barras = "NAO-PAGAVEL-SIM-BARRAS-{uuid}"`.
   - Esses marcadores comprovam a geração de dados pelo sistema sem qualquer risco de colisão com a câmara de compensação real.
   - Boletos em estado `AGUARDANDO_REGISTRO` ou `REJEITADO` possuem esses campos estritamente `null`.
3. **Validação Estrutural Canônica Reutilizada:**
   - As regras de validação estrutural da cobrança são consolidadas em `shared-contracts/SolicitacaoBoletoValidator`. Tanto a borda HTTP do `boleto-emissor` quanto o consumidor do `boleto-registro` executam o mesmo validador canônico, garantindo consistência absoluta entre as fronteiras sem duplicação de regras.

## Alternativas consideradas

- **Confiar Apenas na Transação Local do Banco de Dados:** Rejeitada sumariamente. Se a transação fosse única, qualquer falha local após a chamada à câmara desfazeria a prova da submissão externa, forçando a reentrega a disparar uma segunda chamada à registradora e provocando cobrança indevida de taxas.
- **Idempotência Externa Baseada Apenas em Cache em Memória:** Rejeitada. Quedas ou reinicializações do nó consumidor perderiam o histórico em memória, abrindo brechas para efeitos colaterais duplicados em reentregas da fila.
- **Geração de Linhas Digitáveis Aparentemente Válidas:** Rejeitada por compliance de segurança bancária. Gerar números matematicamente válidos em ambientes de simulação cria risco de vazamento para esteiras de pagamento reais.

## Consequências

- **Positivas:**
  - **Imunidade a Chamadas Duplicadas Externas:** O sistema pode reprocessar mensagens infinitas vezes com a garantia matemática de que a câmara registradora só é executada exatamente uma vez por boleto.
  - **Conformidade Regulatória Estrita:** Pagadores e canais corporativos nunca recebem dados pagáveis antes da homologação definitiva do título.
  - **Integridade Canônica de Validação:** Erros de formato são barrados na entrada da emissão e confirmados defensivamente no consumidor do registro.
- **Custos / Mitigações:**
  - Custo adicional de gerenciar duas transações de banco de dados (`REQUIRES_NEW` no ledger e `REQUIRED` na persistência do resultado do registro). Esse custo é marginal e plenamente justificado pela proteção financeira contra custos de duplicidade.

## Status

Aceita e implementada na camada de integração do serviço de registro.
