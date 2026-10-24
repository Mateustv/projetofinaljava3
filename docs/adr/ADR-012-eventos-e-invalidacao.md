# ADR-012 — Arquitetura Orientada a Eventos: Invalidação Reativa de Cache e Sincronização Concorrente

## Contexto

Embora a estratégia de Cache-Aside (ADR-006) com TTL de 30 segundos proteja as bases de dados de sobrecarga, ela impõe um compromisso de negócio indesejável: o pagador ou o ERP emissor poderiam ter que aguardar até 30 segundos após a confirmação do registro pela câmara externa para que a linha digitável e o código de barras aparecessem na tela de consulta.

Para zerar essa defasagem temporal e fornecer uma experiência em tempo real aos clientes corporativos:
1. O subsistema de registro precisa notificar o ecossistema assim que a câmara externa homologar o título ou recusar a cobrança.
2. A notificação deve ser um **Fato no Passado (Evento de Domínio)**, permitindo que a consulta (e futuros consumidores como auditoria ou antifraude) reaja de forma autônoma sem acoplamento direto com o serviço de registro.

No entanto, a invalidação de cache em sistemas concorrentes de alto tráfego enfrenta o risco crítico de **Condição de Corrida entre Leitura e Invalidação (Race Condition)**:
- Se uma requisição de consulta sofrer um *cache miss* e for buscar os dados no backend no exato milissegundo em que o evento de registro é entregue:
  1. A thread de leitura obtém a resposta (que pode ainda refletir o estado defasado `AGUARDANDO_REGISTRO`);
  2. O listener de eventos recebe a mensagem, invalida a chave no cache e confirma o ACK ao broker;
  3. A thread de leitura finalmente conclui e reinsere o estado antigo `AGUARDANDO_REGISTRO` no cache, anulando a invalidação e mantendo o dado desatualizado preso até a expiração do TTL!

## Decisão

Implementar uma **Arquitetura Orientada a Eventos com Invalidação Reativa e Coordenação de Concorrência por Travas Particionadas**:

1. **Eventos de Domínio Canônicos e Mínimos (Sem Vazamento de Dados Pessoais):**
   - Três eventos de domínio estritos em `shared-contracts`: `EmissaoAceita`, `BoletoRegistrado` e `RegistroRejeitado`.
   - O envelope contém exatamente 7 metadados essenciais: `evento_id` (UUID), `tipo_evento`, `identificador_boleto` (UUID), `convenio`, `estado_resultante`, `data_hora_evento` e `correlation_id`.
   - **Nenhum dado cadastral pessoal ou financeiro sensível trafega no evento** (sem CPF, sem valores nominais, sem linha digitável). O evento atua puramente como um sinalizador de transição de estado.
2. **Publicação Confiável via Outbox de Eventos:**
   - O `boleto-emissor` grava a intenção de `EmissaoAceita` na mesma transação atômica do aceite.
   - O `boleto-registro` grava a intenção de `BoletoRegistrado` ou `RegistroRejeitado` na mesma transação atômica do resultado e histórico regulatório.
   - Despachantes assíncronos publicam as mensagens no endereço MULTICAST durável `boletos.eventos`.
3. **Coordenação Estrita Leitura vs Invalidação (*Striped Locks*):**
   - O microsserviço `boleto-consulta` implementa um conjunto de 64 travas particionadas (*Striped Locks*) mapeadas pelo UUID do boleto.
   - A thread de preenchimento de cache e a thread de consumo de eventos disputam a mesma trava: a invalidação aguarda qualquer leitura em voo finalizar, expurga a chave do Caffeine e **somente depois emite o ACK ao broker JMS**.
   - Se uma leitura atrasada tentar recolocar o dado antigo, a trava garante que a invalidação do evento tem a palavra final, mantendo o cache limpo para a próxima requisição pública.
4. **Resiliência a Duplicatas e Fora de Ordem:**
   - Eventos duplicados ou reentregues apenas repetem a invalidação de forma idempotente, sem jamais alterar ou rebaixar estados consolidados no cache.

## Alternativas consideradas

- **Atualização Direta do Cache pelo Evento (*Event-Carried State Transfer*):** Rejeitada. Exigiria inflar o payload do evento com dados cadastrais completos, endereços e linhas digitáveis, aumentando o acoplamento de schema e violando as diretrizes de privacidade de dados em tráfego de mensageria. A invalidação reativa (*Cache Eviction*) mantém o evento leve e garante que a fonte oficial de dados forneça o payload completo na leitura.
- **Invalidação Ingênua sem Travas:** Rejeitada por gerar inconsistência intermitente sob concorrência, permitindo que leituras em voo reinstalassem dados defasados logo após o evento de registro.
- **Depender Exclusivamente de Polling com TTL:** Rejeitada. Forçaria o pagador a esperar até 30 segundos para conseguir visualizar os dados do boleto já registrado na câmara.

## Consequências

- **Positivas:**
  - **Latência de Atualização Zero:** O pagador tem acesso aos dados para pagamento no mesmo instante em que a câmara externa homologa o registro.
  - **Eliminação de Condições de Corrida:** A sincronização via travas particionadas impede que leituras em voo reinstalem estados velhos após a confirmação do evento.
  - **Privacidade e Desacoplamento:** O tráfego de eventos no broker não carrega dados pessoais, atendendo a princípios de segurança e minimização de dados.
- **Custos / Mitigações:**
  - Adição de infraestrutura de outbox de eventos nos serviços produtores e alocação de assinaturas duráveis dedicadas no broker para a consulta.

## Status

Aceita e implementada nos três microsserviços.
