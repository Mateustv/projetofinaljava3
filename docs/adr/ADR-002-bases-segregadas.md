# ADR-002 — Bases de Dados Segregadas por Domínio (Database-per-Service)

## Contexto

A Central de Cobrança e Boletos atende a operações bancárias críticas com comportamentos transacionais opostos entre seus subsistemas:
1. **Emissão de Cobrança:** Ingestão de altíssima frequência nos dias de virada de mês e datas de vencimento concentrado (picos de até 500 requisições/s). Exige gravações rápidas de comprovantes de aceite para liberar os ERPs clientes em milissegundos.
2. **Registro de Título:** Processamento assíncrono conectado à câmara registradora externa (CIP/B3), envolvendo concorrência complexa, controle de transições de estado, quarentenas e histórico detalhado.
3. **Consulta de Títulos:** Tráfego de leitura intensiva (proporção estimada de 20 consultas para cada 1 emissão), altamente sensível à latência de resposta.

Se esses contextos compartilhassem um banco de dados relacional único:
- **Contenção Crítica de Recursos:** A gravação contínua de registros pesados e atualizações de status geraria concorrência por *table locks*, disputa de conexões no *pool* e degradação de I/O de disco, impactando diretamente o tempo de resposta da emissão.
- **Acoplamento de Esquema (Schema Lock-in):** Qualquer alteração estrutural no modelo de cobrança ou na validação de campos exigiria alinhamento sincronizado de deploy, anulando a agilidade operacional.
- **Efeito Dominó de Indisponibilidade:** Um travamento ou lentidão na base de dados durante uma oscilação da câmara externa derrubaria toda a esteira de aceitação de boletos do banco.

## Decisão

Adotar o padrão **Database-per-Service** (Bases de Dados Segregadas por Serviço):
- Cada microsserviço proprietário de dados possui sua **própria base de dados relacional isolada**:
  - `boleto-emissor`: base exclusiva para armazenamento de solicitações aceitas, controle de idempotência de entrada e outbox transacional.
  - `boleto-registro`: base exclusiva para persistência de títulos registrados/rejeitados, histórico de transições de estado e controle de quarentena.
  - `boleto-consulta`: **sem base de dados relacional própria** (adota cache-aside em memória e integrações REST sob demanda, conforme ADR-004 e ADR-006).
- Nenhum serviço possui permissão de leitura ou escrita nas tabelas de outro serviço.
- O tráfego de dados entre contextos ocorre exclusivamente por **mensageria assíncrona durável** (comandos e eventos) e **contratos HTTP internos explícitos**.

## Alternativas consideradas

- **Banco de Dados Compartilhado (Shared Database):** Rejeitada. Embora simplificasse transações ACID entre emissão e registro, geraria acoplamento severo por dados, eliminaria o isolamento de falhas e criaria um ponto único de falha e de gargalo de concorrência nos picos bancários.
- **Múltiplos Schemas no Mesmo Servidor de Banco:** Rejeitada para a arquitetura de produção. Embora isole tabelas logicamente, ainda provocaria contenção nos pools de conexões, CPU e subsistema de armazenamento durante picos massivos de carga.

## Consequências

- **Positivas:**
  - **Isolamento de Falhas e Disponibilidade:** Uma indisponibilidade ou manutenção na base do registro não afeta a capacidade do emissor de continuar recebendo e acolhendo cobranças dos clientes.
  - **Autonomia de Schema:** As equipes podem evoluir as estruturas internas de armazenamento de cada serviço sem medo de quebras silenciosas em outros contextos.
  - **Otimização Especializada:** Cada base de dados é dimensionada e ajustada exatamente para o perfil de carga da sua aplicação (escrita leve de alta vazão vs processamento de estado consistente).
- **Custos / Mitigações:**
  - **Fim da Transação ACID Global:** Não é possível utilizar transações distribuídas (2PC/XA) entre emissão e registro, pois degradariam a vazão do sistema. A consistência entre contextos passa a ser **eventual**, exigindo o padrão *Transactional Outbox* (ADR-005), comunicação assíncrona garantida (ADR-007) e consumidores estritamente idempotentes (ADR-010).

## Status

Aceita e implementada na arquitetura dos serviços.
