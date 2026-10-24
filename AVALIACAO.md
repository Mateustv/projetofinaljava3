# Auto-avaliação — Central de Cobrança e Boletos Bancários

- **Grupo (3 integrantes — escopo CORE):**
  1. **Mateus Tavares** — Arquitetura de Domínio, Mensageria Transacional (Outbox/Artemis) e Resiliência
  2. **Integrante 2 (`<PREENCHER NOME>`)** — Emissão, Idempotência de Entrada, Validação Estrutural e Persistência
  3. **Integrante 3 (`<PREENCHER NOME>`)** — Consulta, Estratégia de Cache, Invalidação por Eventos e Contratos Executáveis (Pact)
- **Tema / Domínio:** Plataforma bancária que acolhe emissões de boletos de forma assíncrona (`202 Accepted`) para absorver picos de faturamento (virada de mês), homologa o registro obrigatório na Câmara Registradora (simulada) via *Transactional Outbox* e fila durável Apache ActiveMQ Artemis, e disponibiliza consulta de alta performance (proporção 20:1) com cache Caffeine e invalidação reativa por eventos. Não movimenta dinheiro (liquidação ocorre na compensação interbancária).
- **Perfil de execução declarado:** **Plano B (pura-JVM, sem Docker)** — 4 processos JVM independentes (`cobranca-broker`, `boleto-emissor`, `boleto-registro`, `boleto-consulta`).
- **Fallbacks de infraestrutura usados no Perfil B:**
  - *Mensageria:* **Apache ActiveMQ Artemis 2.33.0** embarcado em processo JVM dedicado (`cobranca-broker`), usando protocolo Jakarta JMS 3.1 sobre TCP loopback e persistência em disco via *Journal NIO* (substitui broker em contêiner Docker mantendo filas duráveis `ANYCAST`, tópicos `MULTICAST` com assinaturas duráveis, redelivery com backoff e DLQ/quarentena).
  - *Banco de dados:* **H2 Database** em modo arquivo separado por microsserviço (`data/emissor` e `data/registro`) na execução local e em memória isolada nos testes automatizados (substitui PostgreSQL em contêiner mantendo isolamento físico de esquemas e transações ACID).
  - *Cache:* **Caffeine** em memória local no `boleto-consulta` com política de expiração variável (`Expiry`) por estado do título e coordenação concorrente por travas particionadas (substitui Redis externo mantendo semântica *cache-aside*, TTL diferenciado e invalidação por eventos).

---

## 1. Resumo da Auto-avaliação por Critério

> **Nota sobre os pesos da rubrica:** Utilizamos os pesos oficiais do [brief-alunos.md](brief-alunos.md) (**15 / 15 / 12 / 10 / 12 / 13 / 13 / 5 / 5 = 100 pontos**). Registramos que a tabela do guia da Aula 9 apresenta `12` e `12` nos critérios 6 e 7 (somando 98 apesar de declarar soma 100). Os níveis abaixo refletem o código real testado e as limitações declaradas, sem atribuir pontuação a itens planejados para a Fase 2 ([ADR-014](docs/adr/ADR-014-prioridade-core-e-limites-de-escopo.md)).

| # | Critério | Peso (Brief) | Nível Auto-atribuído (0–3) | Síntese da Justificativa e Evidência Principal |
|---:|---|:---:|:---:|---|
| **1** | **Decomposição de domínio** | 15 | **3 (Avançado)** | 3 bounded contexts em módulos independentes (`boleto-emissor`, `boleto-registro`, `boleto-consulta`), bases H2 fisicamente segregadas, consulta *stateless* sem acesso a tabelas alheias, corte justificado por RNFs opostos ([ADR-002](docs/adr/ADR-002-bases-segregadas.md), [ADR-003](docs/adr/ADR-003-corte-tres-servicos.md)). |
| **2** | **Comunicação assíncrona** | 15 | **3 (Avançado)** | Separação semântica entre **Comando** (fila `ANYCAST` `registro.comandos`) e **Evento de Domínio** (tópico `MULTICAST` `boletos.eventos` com assinatura durável), broker Artemis em JVM própria com Journal NIO e garantia declarada *at-least-once* ([ADR-007](docs/adr/ADR-007-transporte-assincrono.md), [ADR-011](docs/adr/ADR-011-broker-jvm-artemis.md), [ADR-012](docs/adr/ADR-012-eventos-e-invalidacao.md)). |
| **3** | **Idempotência e consistência** | 12 | **3 (Avançado)** | Idempotência em 2 níveis: entrada por `convenio + numero_controle_cliente` + hash SHA-256 (`202` idêntico ou `409 Conflict`) e consumidor por UUID + **Ledger em transação isolada `REQUIRES_NEW`**; atomicidade local via *Transactional Outbox* de comandos e eventos ([ADR-005](docs/adr/ADR-005-saga-e-outbox-no-mvp.md), [ADR-010](docs/adr/ADR-010-contrato-funcional-e-idempotencia-registradora.md)). |
| **4** | **Cache** | 10 | **3 (Avançado)** | Cache-aside Caffeine com TTL diferenciado por mutabilidade (`30 s` para `AGUARDANDO_REGISTRO`, `10 min` para estados finais imutáveis), proibição de cachear `404`/`503`, fallback na janela transitória ([ADR-004](docs/adr/ADR-004-fonte-do-estado-inicial.md)) e invalidação reativa por evento coordenada com 64 *Striped Locks* ([ADR-006](docs/adr/ADR-006-cache-consulta.md), [ADR-012](docs/adr/ADR-012-eventos-e-invalidacao.md)). |
| **5** | **Resiliência** | 12 | **3 (Avançado)** | Orçamento de 3 tentativas com backoff (`1 s` / `5 s`) na outbox e no broker, isolamento imediato de *poison pills* em Quarentena/DLQ (`registro.quarentena`), distinção entre rejeição de negócio (`REJEITADO`) e falha técnica, timeouts HTTP (`2 s` / `3 s`) e Circuit Breakers Resilience4j isolados por fonte ([ADR-008](docs/adr/ADR-008-retry-e-isolamento-outbox.md), [ADR-013](docs/adr/ADR-013-resiliencia-e-correlacao.md)). |
| **6** | **Testabilidade** | 13 | **3 (Avançado)** | **277 testes aprovados** em `mvn -B clean verify` sem Docker; contrato executável **Pact V3** (`boleto-consulta` ↔ `boleto-registro`) com verificação do provedor real + comparação canônica no Failsafe (`PactArtifactIT`); testes de integração com Artemis e H2 reais ([ADR-009](docs/adr/ADR-009-contrato-consulta-registro.md)). |
| **7** | **Decisões arquiteturais** | 13 | **3 (Avançado)** | 14 ADRs em [`docs/adr/`](docs/adr/) no formato Nygard, ancoradas no problema bancário real (pico de fim de mês, risco de duplicidade financeira, norma Bacen/CIP e leitura 20:1), detalhando alternativas rejeitadas, trade-offs e consequências. |
| **8** | **Uso crítico de IA** | 5 | **3 (Avançado)** | Relato transparente e auditável (Seção 3 abaixo e `docs/etapas/`): separa geração de código do julgamento arquitetural humano, documenta 5 problemas técnicos reais detectados/corrigidos na revisão e distingue validação automatizada da validação pessoal do grupo. |
| **9** | **Execução comprovada** | 5 | **3 (Avançado)** | Instruções passo a passo reproduzíveis no [README.md](README.md#como-rodar), perfil B declarado, conferência real das 4 JVMs documentada na [F1](docs/etapas/F1-demonstracao-final.md) e [laboratório web didático](docs/painel-demonstracao.md) opcional. |

---

## 2. Evidências Detalhadas por Critério (Mapa de Localização)

### 1. Decomposição de domínio — Nível: `3` (Peso 15)
- **Tese arquitetural:** Emissão, Registro e Consulta possuem requisitos não-funcionais incompatíveis (ingestão leve de 500 req/s × integração externa pesada × leitura 20:1 com cache). Unificá-los num monólito acoplaria pools de conexões/threads e propagaria lentidão da registradora para toda a plataforma.
- **Evidências no repositório:**
  - **Módulos Maven independentes:** [`pom.xml`](pom.xml), [`boleto-emissor/`](boleto-emissor/), [`boleto-registro/`](boleto-registro/), [`boleto-consulta/`](boleto-consulta/) e contratos neutros em [`shared-contracts/`](shared-contracts/).
  - **Bases segregadas (Database-per-Service):**
    - Emissor usa exclusivamente `jdbc:h2:file:./data/emissor` ([`boleto-emissor/src/main/resources/application.yml`](boleto-emissor/src/main/resources/application.yml));
    - Registro usa exclusivamente `jdbc:h2:file:./data/registro` ([`boleto-registro/src/main/resources/application.yml`](boleto-registro/src/main/resources/application.yml));
    - Consulta **não possui banco nem dependência JPA** ([`boleto-consulta/pom.xml`](boleto-consulta/pom.xml), [`boleto-consulta/src/main/resources/application.yml`](boleto-consulta/src/main/resources/application.yml)).
  - **Documentação e ADRs:** [`docs/arquitetura.md`](docs/arquitetura.md), [ADR-002 (Bases Segregadas)](docs/adr/ADR-002-bases-segregadas.md), [ADR-003 (Corte em Três Serviços)](docs/adr/ADR-003-corte-tres-servicos.md) e [ADR-004 (Fonte do Estado Inicial)](docs/adr/ADR-004-fonte-do-estado-inicial.md).

---

### 2. Comunicação assíncrona — Nível: `3` (Peso 15)
- **Tese arquitetural:** Desacoplamento temporal entre o aceite (`202 Accepted`) e a homologação externa. Comandos imperativos com único dono trafegam em fila ponto-a-ponto (`ANYCAST`); fatos consumados no passado trafegam em tópico pub/sub (`MULTICAST`) com assinaturas duráveis.
- **Garantia de entrega declarada:** **At-least-once** (envio transacional JMS pós-commit local + ACK do consumidor somente após commit JPA, combinado com idempotência no destino).
- **Evidências no repositório:**
  - **Topologia do Broker JVM:** [`BrokerApplication.java`](cobranca-broker/src/main/java/br/com/caixa/cobranca/broker/BrokerApplication.java) (provisiona `registro.comandos`, `registro.quarentena`, `boletos.eventos`, `boletos.eventos.consulta` e `boletos.eventos.quarentena` com Journal NIO).
  - **Produtor de Comandos (Emissor):** [`OutboxDispatcher.java`](boleto-emissor/src/main/java/br/com/caixa/cobranca/emissor/services/OutboxDispatcher.java) e [`JmsRegistroCommandPublisher.java`](boleto-emissor/src/main/java/br/com/caixa/cobranca/emissor/publishers/JmsRegistroCommandPublisher.java).
  - **Consumidor de Comandos (Registro):** [`RegistroCommandListener.java`](boleto-registro/src/main/java/br/com/caixa/cobranca/registro/listeners/RegistroCommandListener.java) e [`RegistroJmsConfiguration.java`](boleto-registro/src/main/java/br/com/caixa/cobranca/registro/configs/RegistroJmsConfiguration.java).
  - **Produtores e Consumidor de Eventos:** módulo [`eventos-outbox/`](eventos-outbox/) ([`EventoOutboxDispatcher.java`](eventos-outbox/src/main/java/br/com/caixa/cobranca/eventos/EventoOutboxDispatcher.java)) e listener da consulta [`ConsultaEventoListener.java`](boleto-consulta/src/main/java/br/com/caixa/cobranca/consulta/listeners/ConsultaEventoListener.java).
  - **Testes de persistência e entrega JMS:** [`BrokerPersistenceTest.java`](cobranca-broker/src/test/java/br/com/caixa/cobranca/broker/BrokerPersistenceTest.java), [`OutboxEntregaJmsTest.java`](boleto-emissor/src/test/java/br/com/caixa/cobranca/emissor/integration/OutboxEntregaJmsTest.java) e relatório [E8 — Broker JVM](docs/etapas/E8-broker-jvm.md).

---

### 3. Idempotência e consistência — Nível: `3` (Peso 12)
- **Tese arquitetural:** Evitar o *Dual-Write Problem* na emissão com *Transactional Outbox* e impedir cobranças/tarifas duplicadas em reentregas *at-least-once* com chaves únicas e *Ledger* externo isolado.
- **Evidências no repositório:**
  - **Idempotência de Entrada (Emissor):** chave composta `convenio + numero_controle_cliente` + fingerprint canônico SHA-256 em [`EmissaoService.java`](boleto-emissor/src/main/java/br/com/caixa/cobranca/emissor/services/EmissaoService.java) e constraint única em [`EmissaoAceitaEntity.java`](boleto-emissor/src/main/java/br/com/caixa/cobranca/emissor/models/EmissaoAceitaEntity.java). Mesma chave e payload retorna o `202` original; payload divergente retorna `409 Conflict`.
  - **Atomicidade Local (Transactional Outbox):** [`EmissaoPersistenceService.java`](boleto-emissor/src/main/java/br/com/caixa/cobranca/emissor/services/EmissaoPersistenceService.java) grava aceite + comando outbox + evento `EmissaoAceita` na mesma transação JPA; [`RegistroPersistenceService.java`](boleto-registro/src/main/java/br/com/caixa/cobranca/registro/services/RegistroPersistenceService.java) grava resultado + histórico + evento final na mesma transação.
  - **Idempotência do Consumidor e Ledger (`REQUIRES_NEW`):** [`RegistroBoletoService.java`](boleto-registro/src/main/java/br/com/caixa/cobranca/registro/services/RegistroBoletoService.java) e [`RegistradoraSimuladaPersistence.java`](boleto-registro/src/main/java/br/com/caixa/cobranca/registro/services/RegistradoraSimuladaPersistence.java) (recupera a mesma resposta externa por UUID se houver queda após a chamada à registradora e antes do commit/ACK local, ignorando metadado `correlation_id` via [`ConteudoComando.java`](boleto-registro/src/main/java/br/com/caixa/cobranca/registro/services/ConteudoComando.java)).
  - **Testes que reprocessam sem duplicar:** [`BoletoControllerTest.java`](boleto-emissor/src/test/java/br/com/caixa/cobranca/emissor/integration/BoletoControllerTest.java), [`DominioRegistroTest.java`](boleto-registro/src/test/java/br/com/caixa/cobranca/registro/integration/DominioRegistroTest.java) e [`RegistroEntregaJmsTest.java`](boleto-registro/src/test/java/br/com/caixa/cobranca/registro/integration/RegistroEntregaJmsTest.java).
  - **ADRs:** [ADR-005 (Transactional Outbox vs SAGA)](docs/adr/ADR-005-saga-e-outbox-no-mvp.md) e [ADR-010 (Idempotência da Registradora)](docs/adr/ADR-010-contrato-funcional-e-idempotencia-registradora.md).

---

### 4. Cache — Nível: `3` (Peso 10)
- **Tese arquitetural:** Proteger as bases de escrita sob proporção de leitura 20:1, respeitando a semântica de mutabilidade do domínio (estado transitório expira rápido; estados finais imutáveis permanecem por mais tempo) e eliminando a defasagem na transição via invalidação orientada a eventos.
- **Evidências no repositório:**
  - **Configuração e TTL Variável (`Expiry`):** [`ConsultaCacheConfiguration.java`](boleto-consulta/src/main/java/br/com/caixa/cobranca/consulta/configs/ConsultaCacheConfiguration.java) — Caffeine limitado a 10.000 entradas, TTL de **30 segundos** para `AGUARDANDO_REGISTRO` e **10 minutos** para `REGISTRADO` e `REJEITADO`.
  - **Cache-Aside, Fallback e Não-Cacheamento de Erros:** [`ConsultaBoletoService.java`](boleto-consulta/src/main/java/br/com/caixa/cobranca/consulta/services/ConsultaBoletoService.java) — busca primeiro no cache; no miss consulta o registro e, **somente em caso de `404`**, aciona fallback ao emissor ([ADR-004](docs/adr/ADR-004-fonte-do-estado-inicial.md)). Respostas `404` e `503` nunca entram no cache.
  - **Invalidação Reativa e Sincronização de Concorrência:** [`ConsultaEventoListener.java`](boleto-consulta/src/main/java/br/com/caixa/cobranca/consulta/listeners/ConsultaEventoListener.java) e as 64 travas particionadas (*Striped Locks*) em [`ConsultaBoletoService.java`](boleto-consulta/src/main/java/br/com/caixa/cobranca/consulta/services/ConsultaBoletoService.java) que impedem que um *cache miss* lento reinstale estado antigo após o ACK do evento final.
  - **Testes automatizados:** [`ConsultaBoletoControllerTest.java`](boleto-consulta/src/test/java/br/com/caixa/cobranca/consulta/integration/ConsultaBoletoControllerTest.java) (verifica política real de TTL do Caffeine e cache hit) e [`ConsultaEventosTest.java`](boleto-consulta/src/test/java/br/com/caixa/cobranca/consulta/integration/ConsultaEventosTest.java) (prova invalidação imediata e corrida miss × evento com latches).
  - **ADRs:** [ADR-006 (Cache-Aside e TTL)](docs/adr/ADR-006-cache-consulta.md) e [ADR-012 (Eventos e Invalidação)](docs/adr/ADR-012-eventos-e-invalidacao.md).

---

### 5. Resiliência — Nível: `3` (Peso 12)
- **Tese arquitetural:** Dimensionar mecanismos de defesa para cada fronteira: retentativas espaçadas para falhas transitórias de transporte, quarentena imediata para mensagens venenosas (*poison pills*) e *Circuit Breaker* com *fail-fast* nas chamadas síncronas da consulta para evitar exaustão de threads.
- **Evidências no repositório:**
  - **Retry c/ Backoff e Isolamento na Outbox (Produtor):** 3 tentativas totais com esperas mínimas de `1 s` e `5 s` em [`OutboxDispatcher.java`](boleto-emissor/src/main/java/br/com/caixa/cobranca/emissor/services/OutboxDispatcher.java) e [`EventoOutboxDispatcher.java`](eventos-outbox/src/main/java/br/com/caixa/cobranca/eventos/EventoOutboxDispatcher.java); inspeção de isolamentos em [`OutboxFalhasController.java`](boleto-emissor/src/main/java/br/com/caixa/cobranca/emissor/controllers/OutboxFalhasController.java).
  - **Redelivery e DLQ/Quarentena no Broker (Consumidor):** 3 entregas totais com delays de `1 s` e `5 s` configurados em [`BrokerApplication.java`](cobranca-broker/src/main/java/br/com/caixa/cobranca/broker/BrokerApplication.java); transferência atômica imediata de JSON inválido/conflito para `registro.quarentena` em [`RegistroCommandListener.java`](boleto-registro/src/main/java/br/com/caixa/cobranca/registro/listeners/RegistroCommandListener.java). Rejeição de negócio (`REJEITADO`) faz commit normal e nunca vai para DLQ.
  - **Timeouts e Circuit Breakers Isolados por Fonte (Consulta):** [`ConsultaClientConfiguration.java`](boleto-consulta/src/main/java/br/com/caixa/cobranca/consulta/configs/ConsultaClientConfiguration.java) (`2000 ms` connect / `3000 ms` read, sem redirects) e [`RestBoletoFonteClient.java`](boleto-consulta/src/main/java/br/com/caixa/cobranca/consulta/clients/RestBoletoFonteClient.java) (dois circuitos Resilience4j independentes: janela de 3 chamadas, abre por `10 s` após 3 falhas técnicas, 1 sonda em `HALF_OPEN`, retorna `503` imediato quando aberto; `404` e `REJEITADO` contam como disponibilidade).
  - **Testes de falha transitória × permanente e circuito:** [`RegistroEntregaJmsTest.java`](boleto-registro/src/test/java/br/com/caixa/cobranca/registro/integration/RegistroEntregaJmsTest.java), [`OutboxDispatcherTest.java`](boleto-emissor/src/test/java/br/com/caixa/cobranca/emissor/services/OutboxDispatcherTest.java) e [`ResilienciaFonteTest.java`](boleto-consulta/src/test/java/br/com/caixa/cobranca/consulta/clients/ResilienciaFonteTest.java).
  - **ADRs:** [ADR-008 (Retry e Quarentena)](docs/adr/ADR-008-retry-e-isolamento-outbox.md) e [ADR-013 (Resiliência HTTP e Correlação)](docs/adr/ADR-013-resiliencia-e-correlacao.md).

---

### 6. Testabilidade — Nível: `3` (Peso 13)
- **Tese arquitetural:** Garantir evolução independente entre equipes sem depender de ambientes integrados instáveis nem de Docker, validando contratos HTTP reais e fluxos assíncronos ponta a ponta diretamente no ciclo `mvn clean verify`.
- **Evidências no repositório:**
  - **Contrato Executável Pact V3 (`boleto-consulta` ↔ `boleto-registro`):**
    - Teste do consumidor: [`RegistroContractTest.java`](boleto-consulta/src/test/java/br/com/caixa/cobranca/consulta/contracts/RegistroContractTest.java) (exercita `RestBoletoFonteClient` real para os cenários `REGISTRADO`, `REJEITADO` e `404`);
    - Contrato canônico versionado: [`boleto-consulta-boleto-registro.json`](shared-contracts/pacts/boleto-consulta-boleto-registro.json);
    - Verificação no provedor real: [`RegistroProviderTest.java`](boleto-registro/src/test/java/br/com/caixa/cobranca/registro/contracts/RegistroProviderTest.java) (sobe Spring Boot e H2 em memória reais);
    - Guardião de fidelidade no Failsafe: [`PactArtifactIT.java`](boleto-consulta/src/test/java/br/com/caixa/cobranca/consulta/contracts/PactArtifactIT.java) (garante que o pact gerado pelo consumidor não divirja do canônico verificado pelo provedor).
  - **Suíte Completa Pura-JVM (277 testes aprovados):**
    - `19` em `infraestrutura-http`, `12` em `cobranca-broker`, `17` em `eventos-outbox`, `99` em `boleto-emissor`, `73` em `boleto-registro` e `56` (Surefire) + `1` (Failsafe) em `boleto-consulta`.
    - Relatórios detalhados em [E6 — Contratos Pact](docs/etapas/E6-contratos-pact.md), [E10 — Resiliência e Correlação](docs/etapas/E10-resiliencia-e-correlacao.md) e [F1 — Demonstração Final](docs/etapas/F1-demonstracao-final.md).

---

### 7. Decisões arquiteturais — Nível: `3` (Peso 13)
- **Tese arquitetural:** Documentar não apenas "o que foi construído", mas principalmente **por que cada escolha foi feita, quais alternativas foram rejeitadas e o que o grupo aceitou sacrificar** diante das restrições do domínio bancário.
- **Evidências no repositório ([`docs/adr/`](docs/adr/)):**
  - [ADR-001](docs/adr/ADR-001-stack-java17-e-perfis.md) — Stack Java 17 LTS, Maven Multi-Módulo e Execução Autônoma em JVM
  - [ADR-002](docs/adr/ADR-002-bases-segregadas.md) — Bases de Dados Segregadas por Domínio (Database-per-Service)
  - [ADR-003](docs/adr/ADR-003-corte-tres-servicos.md) — Corte Arquitetural: Três Bounded Contexts (Emissão, Registro e Consulta)
  - [ADR-004](docs/adr/ADR-004-fonte-do-estado-inicial.md) — Resolução da Janela de Consistência Eventual: Fallback em Duas Fontes na Consulta
  - [ADR-005](docs/adr/ADR-005-saga-e-outbox-no-mvp.md) — Confiabilidade Transacional: Padrão Transactional Outbox vs SAGA Síncrona
  - [ADR-006](docs/adr/ADR-006-cache-consulta.md) — Estratégia de Cache-Aside com TTL Diferenciado por Ciclo de Vida do Título
  - [ADR-007](docs/adr/ADR-007-transporte-assincrono.md) — Comunicação Assíncrona: Nivelamento de Carga e Garantia de Entrega At-Least-Once
  - [ADR-008](docs/adr/ADR-008-retry-e-isolamento-outbox.md) — Resiliência de Mensageria: Retentativas com Backoff, Contenção de Poison Pills e Quarentena (DLQ)
  - [ADR-009](docs/adr/ADR-009-contrato-consulta-registro.md) — Prevenção de Quebras em Produção: Contrato Executável Consumer-Driven (Pact)
  - [ADR-010](docs/adr/ADR-010-contrato-funcional-e-idempotencia-registradora.md) — Idempotência de Integração Externa (Ledger `REQUIRES_NEW`) e Contrato Funcional Não-Pagável
  - [ADR-011](docs/adr/ADR-011-broker-jvm-artemis.md) — Infraestrutura de Mensageria Corporativa: Apache ActiveMQ Artemis Standalone em JVM
  - [ADR-012](docs/adr/ADR-012-eventos-e-invalidacao.md) — Arquitetura Orientada a Eventos: Invalidação Reativa de Cache e Sincronização Concorrente
  - [ADR-013](docs/adr/ADR-013-resiliencia-e-correlacao.md) — Resiliência na Borda (Circuit Breakers Resilience4j) e Rastreabilidade Distribuída (`X-Correlation-ID`)
  - [ADR-014](docs/adr/ADR-014-prioridade-core-e-limites-de-escopo.md) — Gestão de Escopo e Time-to-Market: Delimitação do MVP (Fase 1) vs Fases Futuras (Fase 2)

---

### 8. Uso crítico de IA — Nível: `3` (Peso 5)
- **Evidência completa:** consolidada na **Seção 3 abaixo** e nos relatórios incrementais em [`docs/etapas/`](docs/etapas/) ([E6](docs/etapas/E6-contratos-pact.md), [E7](docs/etapas/E7-contrato-funcional.md), [E8](docs/etapas/E8-broker-jvm.md), [E9](docs/etapas/E9-eventos-e-cache.md), [E10](docs/etapas/E10-resiliencia-e-correlacao.md) e [F1](docs/etapas/F1-demonstracao-final.md)).

---

### 9. Execução comprovada — Nível: `3` (Peso 5)
- **Evidências no repositório:**
  - **Como rodar passo a passo (4 terminais JVM + comandos PowerShell/curl):** [`README.md` (§ Como rodar)](README.md#como-rodar).
  - **Relatório de execução real das 4 JVMs e roteiro de banca (6–8 min):** [`docs/etapas/F1-demonstracao-final.md`](docs/etapas/F1-demonstracao-final.md) e [`docs/validacao-pendente.md`](docs/validacao-pendente.md).
  - **Laboratório visual interativo (perfil `demonstracao`):** [`docs/painel-demonstracao.md`](docs/painel-demonstracao.md) (`http://127.0.0.1:8083/demonstracao`).

---

## 3. Reflexão Crítica sobre o Uso de IA (Critério 8)

O desenvolvimento deste projeto utilizou assistentes de IA generativa de forma intensiva, porém sob direção arquitetural e revisão crítica constante. Abaixo separamos com transparência o que a IA acelerou, quais decisões foram tomadas por julgamento humano, os erros reais que precisamos corrigir e o que foi validado.

### 3.1 O que a IA acelerou (Produção e Commodity)
- Geração do esqueleto multi-módulo Maven, entidades JPA, repositórios e mapeamentos Jackson (`snake_case`).
- Escrita de baterias extensas de casos de teste (como os 54 testes de validação de campos na E7 e os cenários de falha de mensageria na E8/E9).
- Implementação do laboratório web didático (`painel-demonstracao.md`) em HTML/CSS/JS puro para visualização dos rastros em memória e inspeção não-destrutiva (`QueueBrowser`) da quarentena.
- Estruturação e padronização do guia didático ([`docs/guia-do-projeto.md`](docs/guia-do-projeto.md)) para nivelamento de conhecimento da equipe.

### 3.2 Julgamento Arquitetural Humano (O que a IA não decide sozinha)
1. **Remodelagem da Narrativa e das ADRs para o Problema Real de Negócio:**
   - *Intervenção crítica:* Identificamos que a primeira versão das ADRs estava viciada numa visão acadêmica ("escolhemos X porque o critério Y da rubrica exige"). Em uma defesa de Nível III, isso seria uma falha grave: arquitetos resolvem dores de negócio, não rubricas escolares.
   - *Decisão humana:* Redirecionamos toda a modelagem documental ([ADR-001](docs/adr/ADR-001-stack-java17-e-perfis.md) a [ADR-014](docs/adr/ADR-014-prioridade-core-e-limites-de-escopo.md), [`guia-do-projeto.md`](docs/guia-do-projeto.md) e [`requisitos/`](requisitos/)) para ancorar cada decisão nas quatro pressões reais do domínio de boletos: pico de virada de mês (500 req/s), risco jurídico de cobrança duplicada, proibição do Bacen de exibir linha digitável antes do registro na CIP e assimetria de leitura 20:1.
2. **Escolha da Opção C na Janela de Consistência Eventual ([ADR-004](docs/adr/ADR-004-fonte-do-estado-inicial.md)):**
   - Diante das 4 alternativas para resolver o estado inicial logo após o `202 Accepted`, decidimos pelo *fallback* da consulta ao emissor apenas quando o registro retorna `404`. Isso evitou criar um banco de dados redundante na consulta (CQRS pesado) e evitou acoplar o registro de volta ao emissor.
3. **Isolamento do Ledger da Registradora em Transação `REQUIRES_NEW` ([ADR-010](docs/adr/ADR-010-contrato-funcional-e-idempotencia-registradora.md)):**
   - Se a simulação da registradora usasse a mesma transação local do serviço de registro, um *rollback* local apagaria o rastro de que a instituição externa já havia sido chamada, mascarando a janela de duplicidade externa. Exigimos transação `REQUIRES_NEW` independente para simular fielmente uma câmara externa que já comitou.
4. **Delimitação Estratégica de Escopo — Fase 1 MVP vs Fase 2 ([ADR-014](docs/adr/ADR-014-prioridade-core-e-limites-de-escopo.md)):**
   - Decisão consciente de focar nos padrões de arquitetura distribuída do núcleo transacional e registrar formalmente que liquidação financeira, baixa/cancelamento, autenticação mTLS por convênio e expurgo fiscal de 5 anos pertencem à Fase 2, sem fingir que logs técnicos com `X-Correlation-ID` equivalem a auditoria de segurança completa.

### 3.3 Erros Reais Encontrados no Código/Testes da IA e Como Foram Corrigidos
1. **Contaminação de `@JsonTest` por Configuração JPA (E9):**
   - *Problema:* Ao integrar o módulo `eventos-outbox`, a importação direta da configuração JPA na classe `@SpringBootApplication` fazia os testes de fatia `@JsonTest` falharem exigindo `EntityManagerFactory`.
   - *Correção:* Substituímos o `@Import` direto por descoberta via `@ComponentScan` delimitado em [`EventosOutboxConfiguration.java`](eventos-outbox/src/main/java/br/com/caixa/cobranca/eventos/EventosOutboxConfiguration.java), respeitando os filtros de slice do Spring Boot.
2. **Condição de Corrida entre Cache Miss em Voo e Evento de Invalidação (E9):**
   - *Problema:* Uma invalidação ingênua (`cache.invalidate(id)`) permitiria que uma thread de leitura que sofreu *cache miss* milissegundos antes do evento concluísse sua chamada HTTP e escrevesse `AGUARDANDO_REGISTRO` no cache **depois** que o evento `BoletoRegistrado` já havia sido confirmado.
   - *Correção:* Implementamos sincronização com 64 travas particionadas (*Striped Locks*) por UUID em [`ConsultaBoletoService.java`](boleto-consulta/src/main/java/br/com/caixa/cobranca/consulta/services/ConsultaBoletoService.java) e criamos o teste determinístico `missEmAndamentoNaoReinstalaTransitorioDepoisDoAckDoEvento` em [`ConsultaEventosTest.java`](boleto-consulta/src/test/java/br/com/caixa/cobranca/consulta/integration/ConsultaEventosTest.java) usando latches para provar a coordenação.
3. **Corrida de Observação no Teste de Timeout de Leitura HTTP (E10):**
   - *Problema:* No teste `timeoutDeLeituraRealTemLimiteENaoRepeteRequisicao` ([`ResilienciaFonteTest.java`](boleto-consulta/src/test/java/br/com/caixa/cobranca/consulta/clients/ResilienciaFonteTest.java)), o cliente lançava `SocketTimeoutException` em 150 ms antes de a thread do `HttpServer` local incrementar o contador de chamadas, gerando falha intermitente na asserção de contagem.
   - *Correção:* Adicionamos um `CountDownLatch` sinalizado na entrada do handler do servidor antes do `sleep`, garantindo determinismo no teste sem alterar os timeouts de produção (2 s / 3 s).
4. **Falso Positivo de Prontidão do Broker em Porta Ocupada no Windows (E8):**
   - *Problema:* Na máquina local, a porta `61616` estava em estado `Bound` por outro serviço do Windows; checar apenas portas em `Listen` não detectava o conflito e o broker podia anunciar prontidão incorretamente.
   - *Correção:* Adicionamos verificação explícita de ativação do servidor Artemis em [`BrokerApplication.java`](cobranca-broker/src/main/java/br/com/caixa/cobranca/broker/BrokerApplication.java) antes de imprimir `"Broker pronto"` e suporte a porta parametrizável (`--port=61617` + `BROKER_URL`).

### 3.4 Transparência sobre Validação e Execução
- **Execução automatizada e conferência técnica (F1):** A suíte completa de **277 testes** (`mvn -B clean verify`) e a subida real das **4 JVMs** com validação dos cenários HTTP/JMS/Cache/Circuit Breaker/Quarentena ([relatório F1](docs/etapas/F1-demonstracao-final.md)) foram executadas e verificadas no ambiente local com apoio do assistente.
- **Atuação humana dos integrantes:** A definição do domínio, a revisão crítica do escopo e a remodelagem das justificativas arquiteturais foram conduzidas diretamente no projeto. A etapa final **F3** reserva o ensaio prático da demonstração e a divisão da fala entre os três integrantes para a defesa na banca.

---

## 4. Opcionais Entregues

- **Obrigação pelo tamanho do grupo:** Grupo com **3 integrantes** → conforme o [brief (§2)](brief-alunos.md), apenas o **CORE** é obrigatório (0 opcionais extras exigidos).
- **Itens implementados além do mínimo do CORE que fortalecem os critérios gerais:**
  1. **Três microsserviços em vez de dois** (corte completo Emissão, Registro e Consulta — Critério 1);
  2. **Transactional Outbox dupla** (para comandos e para eventos de domínio) + **Ledger `REQUIRES_NEW`** na registradora simulada (Critério 3);
  3. **Tópico Pub/Sub durável de eventos (`boletos.eventos`)** alimentando a invalidação reativa do cache em tempo real (Critérios 2 e 4);
  4. **Resiliência completa com Retry + Backoff + Quarentena/DLQ + Timeouts + Circuit Breakers Resilience4j isolados por fonte** (Critério 5);
  5. **Observabilidade/Rastreabilidade com `X-Correlation-ID` validado (UUID v4)** propagado ponta a ponta via HTTP, Outbox, headers JMS e MDC de logs (apoia Critérios 5 e 8).
