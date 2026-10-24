# ADR-011 — Infraestrutura de Mensageria Corporativa: Apache ActiveMQ Artemis Standalone em JVM

## Contexto

A Central de Cobrança e Boletos exige uma plataforma de mensageria assíncrona robusta, capaz de sustentar simultaneamente dois padrões de comunicação distintos e fundamentais para a arquitetura:

1. **Mensageria Ponto-a-Ponto (ANYCAST) para Comandos:**
   - O comando de registro (`RegistrarBoletoCommand`) exige entrega para **um único consumidor lógico por vez**, confirmação transacional de entrega (ACK pós-commit no banco), reentregas automáticas com intervalo configurável (*redelivery delay*) e direcionamento durável para Quarentena (*Dead-Letter Address*) em caso de falhas permanentes.
2. **Mensageria de Difusão (MULTICAST) para Eventos de Domínio:**
   - A difusão de fatos passados (`boletos.eventos`) exige modelo de **Publicação/Assinatura (Pub/Sub) com Assinaturas Duráveis (Durable Subscriptions)**, assegurando que consumidores como o `boleto-consulta` possam se desconectar ou reiniciar sem perder nenhum evento publicado durante a sua indisponibilidade.
3. **Restrições de Infraestrutura Bancária On-Premise:**
   - Em servidores regulados e esteiras corporativas restritas, a introdução de plataformas que exijam runtimes adicionais (como Erlang para RabbitMQ) ou orquestradores de contêineres dependentes de privilégios de root (Docker) impõe severas barreiras de homologação e segurança.
   - O broker de mensageria deve executar como um processo nativo da JVM (Java 17), utilizar a especificação padrão de mercado (Jakarta JMS) e oferecer persistência transacional durável em disco (*Journal NIO*), garantindo tolerância a falhas catastróficas sem perda de mensagens.

## Decisão

Adotar o **Apache ActiveMQ Artemis 2.33.0 (gerenciado pelo Spring Boot 3.3.5)** executado em processo Java dedicado e independente (`cobranca-broker`):

1. **Padrão Tecnológico Jakarta JMS 3.1:** Comunicação padronizada utilizando conexões TCP nativas em loopback (`127.0.0.1:61616` ou `61617`), sem dependência de drivers proprietários ou bibliotecas externas não homologadas.
2. **Armazenamento Transacional em Disco (NIO Journal):**
   - O Artemis utiliza um subsistema de journal append-only em disco que assegura persistência imediata antes do ACK de publicação.
   - Mensagens pendentes, contadores de entrega acumulados e quarentenas sobrevivem intactos a quedas de energia e reinicializações de servidores.
3. **Topologia Unificada de Destinos:**
   - `registro.comandos`: fila ANYCAST durável para processamento de comandos de cobrança.
   - `registro.quarentena`: fila ANYCAST durável para isolamento definitivo de mensagens com erro (DLQ).
   - `boletos.eventos`: endereço MULTICAST para difusão de eventos de domínio.
   - `boletos.eventos.consulta`: fila durável vinculada à assinatura exclusiva do serviço de consulta no tópico de eventos.
   - `boletos.eventos.quarentena`: quarentena de eventos com erro.
4. **Isolamento de Processos:** O broker executa em sua própria JVM dedicada, simulando exatamente a topologia de um nó de mensageria corporativo de produção, desacoplado dos ciclos de vida dos microsserviços de negócio.

## Alternativas consideradas

- **Apache Kafka:** Rejeitada para este domínio. O Kafka é um motor de log distribuído voltado a ingestão de *event streaming* contínuo de alta taxa e replay histórico de dias/meses. Ele não possui filas transacionais ponto-a-ponto nativas com confirmação individual por mensagem nem suporte simples a quarentenas/DLQs sem camadas pesadas de abstração (ex.: Kafka Streams / Spring Kafka retry topics). Adotaria complexidade acidental desnecessária para o fluxo de comandos de cobrança.
- **RabbitMQ:** Rejeitada devido às restrições do parque computacional. Exigiria a instalação e homologação do runtime Erlang nos servidores ou dependência obrigatória de orquestração Docker, criando barreiras operacionais em ambientes corporativos restritos.
- **Filas Efêmeras em Memória (In-Process / Thread Pools):** Rejeitada por violar a garantia de sobrevivência de dados bancários: qualquer reinício do processo destruiria as mensagens acumuladas.

## Consequências

- **Positivas:**
  - **Suporte Nativo a Padrões Híbridos:** Uma única infraestrutura de mensageria atende com perfeição tanto a comandos ponto-a-ponto (ANYCAST) quanto a eventos pub/sub duráveis (MULTICAST).
  - **Confiabilidade de Nível Industrial:** Persistência em disco via Journal NIO que garante zero perda de cobranças mesmo sob reinicialização forçada do nó.
  - **Portabilidade Total:** O broker roda nativamente em qualquer servidor com Java 17 sem necessidade de privilégios de sistema operacional ou contêineres.
- **Custos / Mitigações:**
  - Exige a inicialização e monitoramento de um processo JVM a mais (`cobranca-broker`). Isso é mitigado pelo empacotamento Spring Boot executável e scripts padronizados de subida do ambiente.

## Status

Aceita e implementada como a infraestrutura de mensageria definitiva do sistema.
