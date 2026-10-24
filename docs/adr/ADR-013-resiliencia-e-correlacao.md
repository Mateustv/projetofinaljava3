# ADR-013 — Resiliência na Borda (Circuit Breakers Resilience4j) e Rastreabilidade Distribuída (Correlation ID)

## Contexto

O microsserviço `boleto-consulta` executa chamadas síncronas HTTP internas contra os microsserviços `boleto-registro` e `boleto-emissor` sempre que ocorre um *cache miss*.

Em ambientes bancários de produção com alto volume de acessos, essa comunicação síncrona enfrenta dois desafios arquiteturais severos:

1. **O Risco de Falhas em Cascata (Cascading Failures) e Exaustão de Threads:**
   - Se o serviço de registro sofrer sobrecarga de banco de dados ou lentidão extrema na integração externa, as chamadas HTTP da consulta ficarão retidas esperando resposta.
   - Na proporção de tráfego de leitura 20:1, em poucos segundos **todo o pool de threads HTTP da consulta seria exaurido** (*Thread Pool Exhaustion*).
   - O resultado seria o colapso completo do serviço de consulta: ele deixaria de atender até mesmo as requisições de boletos que já estavam perfeitamente cacheados na memória, amplificando a falha de um serviço secundário para toda a plataforma!
2. **Rastreabilidade Operacional em Sistemas Distribuídos Assíncronos:**
   - O processamento de uma cobrança atravessa fronteiras heterogêneas: Ingestão HTTP → Transação JPA → Outbox de Comandos → Fila JMS → Consumidor de Registro → Outbox de Eventos → Tópico JMS → Invalidação e Consultas HTTP.
   - Quando um cliente corporativo relata uma anomalia em uma remessa, a equipe de engenharia e sustentação precisa reconstruir a cadeia causal completa daquela requisição nos logs de múltiplos servidores.
   - **Regra de Ouro:** O identificador de rastreabilidade é um metadado puramente técnico e **nunca deve fazer parte da chave de negócio de idempotência**, garantindo que reenviar a mesma cobrança com um novo cabeçalho HTTP não gere títulos duplicados.

## Decisão

Implementar uma estratégia abrangente de **Proteção de Borda com Circuit Breakers Isolados (Resilience4j)** e **Rastreabilidade Forense Padronizada (`X-Correlation-ID`)**:

1. **Circuit Breakers Independentes por Dependência Remota:**
   - A consulta gerencia **duas instâncias de circuito totalmente isoladas**: uma dedicada ao `boleto-registro` e outra dedicada ao `boleto-emissor`. Uma lentidão ou queda no registro não afeta o canal com o emissor.
   - Utilização do `resilience4j-circuitbreaker` 2.2.0 de forma direta e programática (sem AOP ou anotações opacas).
   - **Limites Rígidos de Socket:** Timeout de conexão fixado em **2 segundos** e timeout de leitura em **3 segundos**, com redirecionamentos automáticos desativados.
   - **Métrica de Abertura:** Janela deslizante de 3 chamadas. Caso ocorram **3 falhas técnicas consecutivas (100%)**, o circuito transita imediatamente para **`OPEN` por 10 segundos**.
   - **Comportamento em Falha Rápida (*Fast Failure*):** Com o circuito aberto, requisições com *cache miss* são rejeitadas em sub-milissegundos com **HTTP 503 Service Unavailable**, sem tentar chamadas remotas inúteis e permitindo que o serviço degradado se restabeleça.
   - **Preservação de Cache Hits:** Consultas de boletos já consolidados em cache continuam respondendo `HTTP 200 OK` instantaneamente, mesmo que o circuito para a fonte esteja aberto.
   - **Semântica de Disponibilidade:** Respostas `HTTP 404 Not Found` (ausência válida) e respostas de negócio `REJEITADO` contam como disponibilidade (sucesso técnico) para o circuito, pois a fonte remota respondeu de forma saudável e determinística.
2. **Propagação Ponta a Ponta de Rastreabilidade (`X-Correlation-ID`):**
   - Módulo comum `infraestrutura-http` intercepta todas as requisições HTTP dos três microsserviços.
   - Se o cliente informar o cabeçalho `X-Correlation-ID`, o filtro valida estritamente a conformidade com a especificação RFC UUID v4 canônico em minúsculas (rejeitando strings inválidas com HTTP 400 antes de atingir o domínio). Se ausente, gera um novo UUID v4 automaticamente.
   - O identificador é inserido no MDC (*Mapped Diagnostic Context*) do SLF4J, propagado nas tabelas de outbox, injetado nos metadados de mensagens JMS, transferido aos eventos de domínio e retornado em todos os cabeçalhos de resposta HTTP (inclusive em erros 400, 404 e 503).
   - O contexto do MDC é limpo de forma determinística em bloco `finally` via `AutoCloseable` ao término de cada requisição ou consumo de mensagem.
3. **Desacoplamento da Identidade de Negócio:**
   - O cálculo do hash de idempotência e a verificação de duplicidade no emissor e no registro ignoram expressamente o campo `correlation_id`, garantindo que retentativas de rede preservem a unicidade financeira da cobrança.

## Alternativas consideradas

- **Apenas Timeouts de Socket sem Circuit Breaker:** Rejeitada. Timeouts apenas limitam a duração de uma requisição isolada, mas sob tráfego de 500 req/s continuam disparando milhares de requisições por segundo contra um backend que já está em colapso, perpetuando a indisponibilidade.
- **Circuit Breaker Compartilhado Globalmente:** Rejeitada. A queda do serviço de registro abriria o circuito para o emissor, impedindo o fallback de boletos recém-emitidos.
- **Retry Automático nas Chamadas HTTP da Consulta:** Rejeitada. Adicionar retries síncronos na consulta amplificaria a latência do pagador e geraria o efeito de tempestade de retentativas (*retry storm*) contra fontes já sobrecarregadas.

## Consequências

- **Positivas:**
  - **Blindagem Contra Falhas em Cascata:** Uma falha técnica ou sobrecarga severa no registro não derruba a consulta; ela falha rápido com 503 e continua servindo cache hits.
  - **Diagnóstico Preciso de Incidentes:** A equipe de sustentação pode correlacionar logs de ponta a ponta em segundos utilizando uma busca única por Correlation ID.
  - **Preservação de Dados Sensíveis:** Logs estruturados registram metadados e correlation IDs sem vazar CPFs, nomes de beneficiários ou valores de boletos.
- **Custos / Mitigações:**
  - Sob indisponibilidade das fontes, clientes com *cache miss* recebem 503 imediato durante a janela de abertura do circuito (10 s), exigindo tratamento defensivo nos canais de atendimento e ERPs.

## Status

Aceita e implementada em todos os microsserviços da solução.
