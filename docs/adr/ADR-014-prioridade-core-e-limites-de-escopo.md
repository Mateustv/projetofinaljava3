# ADR-014 — Gestão de Escopo e Time-to-Market: Delimitação do MVP da Central de Cobrança vs Fases Futuras

## Contexto

O ciclo de vida completo de uma operação de cobrança bancária corporativa é extenso e multidisciplinar. Em uma instituição financeira de grande porte, ele abrange:
1. Ingestão e validação de solicitações em lote de ERPs conveniados;
2. Registro regulatório obrigatório perante a Câmara Registradora Centralizada (CIP/B3);
3. Disponibilização pública de 2ª via e consulta de situação para pagadores;
4. Liquidação financeira em tempo real (compensação bancária interbancária via STR/CIP/PIX);
5. Baixa comandada de títulos após confirmação de pagamento em outras instituições financeiras;
6. Gestão de alterações contratuais (prorrogação de vencimento, concessão de abatimento, alteração de juros/multa);
7. Comandos judiciais e cartorários de protesto de títulos e negativação em birôs de crédito (Serasa/SPC);
8. Governança regulatória estendida: retenção arquivística e expurgo fiscal por 5 anos (resoluções Bacen) e gestão avançada de autorização multi-tenant por convênio com certificados digitais mTLS.

**O Desafio de Time-to-Market e Risco Operacional:**
Tentar abarcar a totalidade desse ecossistema em um único ciclo de entrega inicial (MVP) acarretaria riscos proibitivos de prazo, complexidade acidental e instabilidade operacional. 

A dor mais urgente e crítica relatada pelas empresas clientes e pelas operações do banco concentra-se no **gargalo da esteira de entrada e homologação do título**: o colapso dos sistemas nos picos de fechamento contábil, a perda de solicitações de emissão, o risco jurídico de cobranças duplicadas e a demora inaceitável para que o pagador tenha acesso ao boleto registrado para efetuar o pagamento.

## Decisão

Adotar uma abordagem estratégica de **Lançamento Fatiado por Valor de Negócio**, delimitando estritamente o escopo da **Fase 1 (MVP de Produção)** ao **Núcleo Crítico de Registro e Disponibilização de Cobranças**:

1. **Escopo Consolidado da Fase 1 (MVP — Entregue e Validado):**
   - **Ingestão Assíncrona de Alta Vazão:** Borda HTTP leve respondendo `202 Accepted` em < 100 ms com comprovação formal de aceite (`boleto-emissor`);
   - **Idempotência de Entrada Radical:** Deduplicação por chave de negócio (`convenio + numero_controle_cliente`) e contenção com `409 Conflict` contra alterações divergentes;
   - **Atomicidade Local e Confiabilidade:** Padrão *Transactional Outbox* para comandos e eventos de domínio, eliminando o problema de gravação dupla (*Dual-Write*);
   - **Mensageria Durável de Nível Industrial:** Broker Apache ActiveMQ Artemis em JVM própria, com armazenamento transacional em disco (Journal NIO), filas ANYCAST para comandos e tópicos MULTICAST para eventos (`cobranca-broker`);
   - **Processamento Assíncrono Idempotente:** Consumidor resiliente no `boleto-registro` com *Ledger* em transação isolada (`REQUIRES_NEW`), protegendo a instituição contra custos de chamadas duplicadas à câmara registradora;
   - **Consulta de Ultra-Baixa Latência:** Serviço desacoplado (`boleto-consulta`) com cache-aside Caffeine, TTL variável por ciclo de vida (30 s / 10 min), fallback elegante na janela transitória e invalidação reativa em tempo real orientada a eventos;
   - **Resiliência de Borda:** Circuit Breakers isolados por dependência remota (Resilience4j) e timeouts agressivos para blindagem contra falhas em cascata;
   - **Rastreabilidade e Contratos Executáveis:** Propagação ponta a ponta de `X-Correlation-ID` validado e testes de contrato orientados pelo consumidor (Pact) integrados ao build.
2. **Escopo Formalmente Postergado para a Fase 2 (Pós-Go-Live e Estabilização do Núcleo):**
   - **Liquidação Financeira e Movimentação de Fundos:** A Central não debita nem credita contas correntes; a movimentação de valores continua operando na esteira especializada de compensação interbancária;
   - **Baixa Comandada, Cancelamento e Protesto:** Funcionalidades de manutenção de títulos posteriores ao registro serão integradas em módulo especializado na Fase 2;
   - **Segurança Corporativa Avançada e Ciclo de Retenção Fiscal:** A autenticação mTLS por certificado de convênio, auditoria persistida imutável em HSM e rotinas automatizadas de expurgo fiscal de 5 anos serão implementadas na esteira de segurança corporativa após a estabilização do núcleo transacional.

## Alternativas consideradas

- **Tentar Implementar Todo o Ciclo Bancário no Primeiro Release:** Rejeitada. Diluiria o foco da engenharia, aumentaria geometricamente a superfície de falhas e adiaria o lançamento da solução em mais de um ano, mantendo os clientes sofrendo com o gargalo de emissão.
- **Construir um MVP Minimalista Síncrono (Monólito sem Fila):** Rejeitada. Um sistema síncrono falharia categoricamente no primeiro pico de fim de mês (500 req/s), não resolvendo a causa raiz da instabilidade bancária.

## Consequências

- **Positivas:**
  - **Entrega Cirúrgica de Valor:** Foco absoluto na resolução do maior gargalo técnico e financeiro da instituição: aceitar cobranças massivas sem perder dados e registrar títulos na câmara sem duplicidade.
  - **Robustez Industrial Comprovada:** O núcleo do sistema entra em produção com 277 testes automatizados, contratos executáveis e resiliência de alto nível nas fronteiras de rede.
  - **Clareza de Fronteiras:** A organização sabe exatamente o que a plataforma faz na Fase 1 e como os módulos futuros de liquidação e cancelamento se plugarão através de eventos de domínio na Fase 2.
- **Custos / Mitigações:**
  - Cancelamentos manuais ou consultas de boletos liquidados continuam dependendo provisoriamente dos sistemas legados de compensação da instituição até o início da Fase 2.

## Status

Aceita pela liderança de engenharia e produto como a diretriz estratégica de lançamento.
