# Guia didático do projeto — Central de Cobrança e Boletos

**Para quem é:** integrantes que precisam entender o trabalho mesmo sem terem acompanhado sua construção.
**Versão de referência:** implementação E0–E10, com pacotes organizados no commit `439e610` e laboratório didático local adicionado em 21/10/2026.
**Objetivo:** explicar o que o sistema faz, como as partes se conectam e onde encontrar cada responsabilidade. Não é necessário decorar todas as classes.

Este guia descreve código existente, não uma proposta de novas funcionalidades. A regressão completa do painel tem **277 testes aprovados**, incluindo PACT. Isso não significa que os três integrantes já executaram a demonstração ou que a entrega final foi encerrada. O que falta está no [roteiro F1–F3](../ROTEIRO.md).

**Para ver o fluxo funcionando:** siga o [laboratório web](painel-demonstracao.md). Ele executa os caminhos reais e mostra rastros das aplicações; não substitui a leitura nem cria requisitos extras.

## Índice e ordem de leitura

1. [O problema e o vocabulário](#1-o-problema-e-o-vocabulário)
2. [As peças e as tecnologias](#2-as-peças-e-as-tecnologias)
3. [Uma solicitação do começo ao fim](#3-uma-solicitação-do-começo-ao-fim)
4. [Por que existem tantos cuidados](#4-por-que-existem-tantos-cuidados)
5. [Como ler as pastas e o Java/Spring](#5-como-ler-as-pastas-e-o-javaspring)
6. [Catálogo das classes de produção](#6-catálogo-das-classes-de-produção)
7. [APIs, dados e configurações](#7-apis-dados-e-configurações)
8. [Como estudar e experimentar](#8-como-estudar-e-experimentar)
9. [Testes e critérios da avaliação](#9-testes-e-critérios-da-avaliação)
10. [O que foi construído em cada etapa](#10-o-que-foi-construído-em-cada-etapa)
11. [O que não existe e o que falta](#11-o-que-não-existe-e-o-que-falta)
12. [Perguntas para treinar a defesa](#12-perguntas-para-treinar-a-defesa)

**Primeira leitura:** seções 1–5. **Com o código aberto:** seções 6–8. **Preparação para a banca:** seções 9–12. O [README](../README.md) continua sendo a referência para iniciar/parar as aplicações; a [arquitetura](arquitetura.md) reúne o diagrama técnico e as decisões.

## 1. O problema e o vocabulário

Uma empresa quer solicitar uma cobrança e acompanhar se o título foi registrado ou rejeitado. A Central recebe a solicitação, confirma que a guardou e processa o registro em segundo plano. Depois, o cliente consulta o resultado.

**Em uma frase:** aceitar agora, registrar depois e permitir consultar o estado sem duplicar o trabalho.

Exemplo: um ERP solicita uma cobrança de R$ 150,00. A Central responde “recebi sua solicitação”, com um identificador. Outro serviço processa o registro em uma registradora **simulada**. A consulta passa a mostrar o resultado. Não há pagamento real.

| Termo | Significado no nosso projeto |
|---|---|
| Beneficiário | Quem deve receber a cobrança; faz parte dos dados enviados |
| Pagador | Pessoa indicada para pagar; não realiza pagamento dentro da Central |
| Convênio | Código de seis dígitos que participa da identificação da solicitação |
| Número de controle do cliente | Identificador definido pelo sistema que solicita a cobrança |
| UUID do boleto | Identificador interno criado pela Central; não é o número de controle do cliente |
| Registradora | Sistema externo que confirmaria o registro; aqui é uma implementação Java com resultados simulados |
| Emissão aceita | Solicitação validada e persistida; **ainda não significa registro concluído** |
| Idempotência | Repetir a mesma operação não deve criar outro efeito de negócio |
| Consistência eventual | Emissor e registro não mudam juntos numa transação única; o resultado aparece depois do processamento |
| Bounded context | Limite de responsabilidade e linguagem de uma parte do domínio; aqui emissão, registro e consulta |

### Estados do boleto

```text
AGUARDANDO_REGISTRO
        |
        +----> REGISTRADO
        |
        +----> REJEITADO
```

- `AGUARDANDO_REGISTRO`: o aceite existe, mas ainda não há resultado de negócio.
- `REGISTRADO`: a simulação aprovou; a consulta inclui dados de registro e marcadores não pagáveis.
- `REJEITADO`: a simulação recusou por negócio; a consulta inclui código e motivo, sem dados de pagamento.
- Não existe estado “pago” ou “liquidado” nesta implementação.
- Falha técnica **não vira rejeição de negócio**. Se as tentativas se esgotarem sem resultado, o boleto pode permanecer aguardando e o trabalho ficar isolado.

## 2. As peças e as tecnologias

Há **três microsserviços de negócio e um broker em processo separado**. Os outros módulos são bibliotecas utilizadas por esses serviços, não aplicações adicionais a iniciar.

| Módulo | Função | Execução e armazenamento |
|---|---|---|
| [boleto-emissor](../boleto-emissor/) | Validar o pedido, persistir o aceite e preparar o comando de registro | JVM própria; HTTP 8081; H2 próprio |
| [boleto-registro](../boleto-registro/) | Consumir o comando, chamar a simulação e persistir o resultado | JVM própria; HTTP 8082; H2 próprio |
| [boleto-consulta](../boleto-consulta/) | Consultar o estado e manter cache de leitura | JVM própria; HTTP 8083; sem banco próprio |
| [cobranca-broker](../cobranca-broker/) | Receber, guardar e entregar comandos/eventos | JVM própria; porta padrão 61616; journal em disco |
| [shared-contracts](../shared-contracts/) | Compartilhar formatos de dados e validação estrutural | Biblioteca, sem servidor ou banco |
| [eventos-outbox](../eventos-outbox/) | Persistir e publicar intenções de eventos | Biblioteca executada em cada produtor; tabelas nos bancos deles |
| [infraestrutura-http](../infraestrutura-http/) | Gerar/validar/propagar correlação HTTP | Biblioteca usada pelos três serviços |

**Bases segregadas:** registro não abre o banco do emissor e consulta não lê tabelas alheias. A comunicação é feita por API ou mensagem. Compartilhar a biblioteca de outbox não cria um banco compartilhado.

| Tecnologia | Para que a usamos |
|---|---|
| Java 17 | Linguagem/runtime das aplicações; os `record`s representam vários contratos |
| Spring Boot 3.3.5 | Inicialização, servidor HTTP e integração das configurações |
| Spring MVC | Controllers, leitura de JSON e respostas HTTP |
| Jackson | Conversão Java ↔ JSON, com nomes em `snake_case` |
| Spring Data JPA/Hibernate | Mapeamento de entidades e acesso ao H2 por repositories |
| H2 em arquivo | Persistência didática local do aceite, resultados, outboxes e ledger |
| Artemis 2.33.0 + Jakarta JMS | Broker real e API Java de publicação/consumo transacional |
| Caffeine | Cache em memória da consulta |
| Resilience4j 2.2.0 | Circuit breaker nas fontes HTTP da consulta |
| Maven | Compilação, dependências, testes e empacotamento dos módulos |
| JUnit/Mockito e testes Spring | Casos de teste, simulação controlada de dependências e integração |
| PACT | Teste executável do acordo HTTP entre consulta e registro |
| Actuator | Endpoints de saúde/informação; saúde não prova conclusão de uma emissão |
| SLF4J/MDC | Logs e contexto de correlação; não substituem auditoria persistida |

## 3. Uma solicitação do começo ao fim

### 3.1 Visão geral

```mermaid
sequenceDiagram
    participant C as Cliente/ERP
    participant E as Emissor
    participant B as Artemis
    participant R as Registro
    participant Q as Consulta
    C->>E: POST /boletos
    Note over E: Transação local: aceite + comando outbox + evento outbox
    E-->>C: 202 + UUID + AGUARDANDO_REGISTRO
    Note over E,B: Despachantes publicam em segundo plano
    E->>B: RegistrarBoletoCommand
    B->>R: Entrega do comando
    Note over R: Simulação; resultado + histórico + evento outbox
    R-->>B: Confirma consumo após commit local
    R->>B: Evento final publicado pela outbox
    B->>Q: Evento final invalida cache
    C->>Q: GET /boletos/UUID
    Note over Q: Cache; no miss, HTTP registro e fallback após 404
    Q-->>C: Estado consultado
```

O desenho mostra uma sequência típica, não um prazo garantido. A consulta pode ocorrer antes de o comando ser consumido; os despachantes de comando e de evento são independentes.

### 3.2 Aceitar a solicitação

1. O filtro HTTP cria ou valida `X-Correlation-ID` para rastrear a requisição.
2. O controller do emissor converte o JSON em uma solicitação Java e chama o service.
3. O validador reúne erros de campos: obrigatoriedade, formatos e combinações condicionais.
4. O service verifica **convênio + número de controle** e o fingerprint do conteúdo:
   - chave nova: continua;
   - chave conhecida com conteúdo igual: devolve o aceite original;
   - chave conhecida com conteúdo diferente: responde `409`.
5. Uma transação grava **aceite + intenção de comando + intenção de evento `EmissaoAceita`**.
6. Só depois do commit, o controller responde `202` com UUID, estado e data/hora de recebimento.

**Por que gravar antes de enviar ao broker?** Para não confirmar ao cliente algo cuja intenção de processamento poderia ser perdida numa falha entre banco e envio. Broker fora do ar não impede sozinho o `202`, desde que a transação local consiga preservar o trabalho.

### 3.3 Publicar e registrar

1. O dispatcher busca comandos elegíveis na outbox e usa o publisher JMS.
2. O publisher envia JSON persistente para `registro.comandos`; commit JMS confirma aceitação pelo broker, **não registro concluído**.
3. O listener do registro recebe o comando e valida JSON, envelope e correlação.
4. O service valida o comando e procura um resultado existente:
   - mesmo UUID e negócio já processado: devolve o resultado, sem repetir o efeito;
   - mesmo UUID com negócio diferente: conflito, encaminhado à quarentena no fluxo JMS.
5. A registradora simulada recupera ou cria seu resultado no ledger.
6. A transação local do registro grava **resultado + histórico + intenção de evento final**.
7. Só após o retorno com commit JPA, o consumo JMS é confirmado.

**Janela importante:** banco e JMS não fazem parte de uma transação distribuída única. Se cair depois do commit local e antes da confirmação JMS, o comando pode voltar. A idempotência existe justamente para absorver isso.

### 3.4 Consultar e atualizar o cache

1. O cliente chama a consulta usando o UUID do aceite.
2. Com cache preenchido, ela devolve a entrada sem chamar as fontes.
3. No miss, chama o registro por HTTP.
4. **Somente um `404` do registro** permite buscar o aceite no emissor. Isso atende a janela em que a solicitação existe, mas ainda não foi registrada.
5. Se o emissor conhece o aceite, a consulta representa `AGUARDANDO_REGISTRO`; se também não conhece, retorna `404`.
6. Falha técnica de uma fonte necessária retorna `503`; não é convertida em ausência nem sucesso.
7. Quando chega um evento final, a consulta remove a entrada. A próxima leitura busca o resultado atualizado.

O emissor conserva o aceite original; não é atualizado para o estado final do registro. Por isso, consultar diretamente o endpoint interno do emissor **não substitui consultar o serviço de consulta**.

## 4. Por que existem tantos cuidados

### Outbox, comando e evento não são a mesma coisa

- **Outbox:** tabela de “trabalho a enviar”, gravada junto com a mudança local. Não é a fila do broker.
- **Comando:** pedido de ação — “registre este boleto”. Tem um destino de processamento.
- **Evento:** fato — “a emissão foi aceita”, “o boleto foi registrado”, “o registro foi rejeitado”. Pode interessar a assinantes independentes.
- **Ack:** confirmação de consumo. Não deve ocorrer antes de preservar o resultado exigido.
- **At-least-once + idempotência:** pode haver reentrega; ela não deve duplicar o efeito de negócio. Não estamos prometendo exactly-once.

### Fila e publicação/assinatura

Topologia definida no [broker](../cobranca-broker/src/main/java/br/com/caixa/cobranca/broker/BrokerApplication.java):

| Destino | Tipo/função |
|---|---|
| `registro.comandos` | ANYCAST; fila durável de comandos para registro |
| `registro.quarentena` | Fila durável para comando inválido/permanente ou esgotamento de entregas |
| `boletos.eventos` | Endereço MULTICAST de eventos |
| `boletos.eventos.consulta` | Fila/assinatura durável da consulta no endereço de eventos |
| `boletos.eventos.quarentena` | Quarentena de eventos inválidos ou entregas esgotadas |

ANYCAST distribui o trabalho para uma fila de destino; MULTICAST permite uma cópia por assinatura. Ter dois consumidores na **mesma** fila não cria duas cópias independentes. A demonstração usa uma instância de cada serviço; duas consultas com caches locais precisariam de assinaturas próprias.

### Retry, backoff e quarentena

- Outbox de comandos: **três tentativas totais de publicação**, com esperas mínimas de **1 s e 5 s**. Ao esgotar, item permanece isolado no H2.
- Outbox de eventos: três tentativas totais, mesma política de espera e isolamento local.
- Consumo de comandos/eventos: até três entregas totais pelo broker para falhas temporárias; esperas mínimas de 1 s e 5 s, depois quarentena.
- Intervalo do scheduler e tempo de processamento podem alongar esses tempos; não são prazos exatos.
- JSON/envelope inválido ou conflito permanente não precisam gastar as três entregas de negócio: o listener pode transferir diretamente à quarentena, na mesma transação JMS do ack original.
- Rejeição de negócio é um resultado válido `REJEITADO`, não uma falha a retentar.

Os orçamentos são de **fronteiras distintas**: conseguir publicar e conseguir processar. Não significam nove chamadas obrigatórias à registradora. Depois do isolamento não há promessa de recuperação automática nem endpoint de replay.

### Ledger da simulação e transações

O ledger guarda o resultado por UUID em transação `REQUIRES_NEW`, independente da transação do registro. Ele representa didaticamente um sistema externo que já confirmou a operação, mesmo que a Central sofra rollback depois.

Na reentrega, a simulação recupera a mesma resposta em vez de gerar outro efeito. Isso demonstra recuperação de resposta perdida, **não garante idempotência de uma registradora bancária real**. Fisicamente o ledger está no H2 do registro; não existe um quarto microsserviço de registradora.

### Cache, TTL e corrida com evento

- Estratégia **cache-aside**: a aplicação verifica cache; no miss, busca a fonte e preenche.
- TTL: **30 s** para estado transitório, **10 min** para estado final.
- Limite: **10.000 entradas**. O cache fica na memória da consulta e não sobrevive ao reinício.
- `404` e falhas técnicas não são armazenados.
- Evento final invalida antes do TTL. O TTL continua como proteção se o evento atrasar/falhar.
- Leitura/preenchimento e invalidação usam as mesmas 64 travas por grupos de UUIDs. Isso evita o caso de remover uma entrada e uma leitura antiga em andamento recolocá-la depois do ack.
- Uma resposta já em voo ainda pode refletir o estado antigo; não é consistência forte global.

### Timeout e circuit breaker

A consulta limita conexão a **2 s** e leitura a **3 s**. Não segue redirecionamentos nem acrescenta retry HTTP de aplicação.

Cada fonte tem circuito próprio: três falhas técnicas nas últimas três chamadas abrem por **10 s**. Em OPEN, chamadas são bloqueadas e viram `503`. Depois, uma única sonda é permitida em HALF_OPEN: sucesso fecha, erro reabre. `404` válido conta como disponibilidade, mas continua sendo ausência; `REJEITADO` em resposta válida também não é indisponibilidade.

Cache hit válido continua respondendo sem chamar a fonte. Os limites são de socket, não um SLA total incluindo DNS, travas e todas as operações. Não confundir com comprovação de p95.

### Correlação não é identidade de negócio

`X-Correlation-ID` identifica uma requisição/fluxo para diagnóstico. Ausente, é gerado; informado, deve ser UUID v4 canônico em minúsculas, variante 2, sem espaços ou múltiplos valores. Entrada inválida gera `400` e um identificador seguro na resposta.

A correlação atravessa HTTP, outbox, comando JMS e evento; os consumidores reabrem e limpam seu contexto. Consultas/reenvios podem ter novos IDs de requisição, sem alterar a intenção original. O fingerprint da emissão não passa a depender do header; comparação de comandos no registro/ledger ignora somente `correlation_id`.

## 5. Como ler as pastas e o Java/Spring

| Pasta/tipo | Pergunta que responde |
|---|---|
| `controllers` | Por qual URL entra e qual resposta HTTP sai? |
| `services` | Qual operação é coordenada e em qual transação? |
| `models` | O que fica persistido ou representa estado interno? |
| `repositories` | Como localizar/gravar os dados do próprio serviço? |
| `dtos` | Qual formato local de resposta/integração é transportado? |
| `clients` | Como chamar uma fonte ou a registradora simulada? |
| `publishers` / `listeners` | Como enviar / receber uma mensagem? |
| `configs` | Como criar e parametrizar os componentes? |
| `validators` | Quais dados são aceitos antes de processar? |
| `exceptions` / `handlers` | Como representar uma falha / convertê-la em resposta HTTP? |

Não existem pastas vazias só para completar o padrão. Os contratos entre serviços ficam em [shared-contracts](../shared-contracts/), não são copiados para cada DTO local. Uma entidade JPA não é automaticamente o JSON da API.

| Recurso Java/Spring | Como interpretar ao ler o código |
|---|---|
| `interface` e implementação | Contrato de comportamento e classe que o realiza; por exemplo, client de fonte e sua implementação REST |
| `record` | Estrutura de dados com componentes definidos; não implica tabela de banco |
| Injeção pelo construtor | O Spring fornece as dependências, em vez de a classe criá-las manualmente |
| `@SpringBootApplication` | Ponto de entrada/configuração; permanece na raiz dos pacotes do serviço |
| `@RestController`, `@GetMapping`, `@PostMapping` | Exposição de operações HTTP |
| `@Service`, `@Component`, `@Bean` | Formas de disponibilizar objetos gerenciados pelo Spring |
| `@Entity`, `@Id`, `@Table` | Mapeamento de dados persistidos para banco |
| `JpaRepository` | Operações de persistência e métodos de consulta |
| `@Transactional` | Fronteira de transação local; não engloba automaticamente outro banco ou JMS |
| `REQUIRES_NEW` | Transação independente, usada pelo ledger da simulação |
| `MANDATORY` | Exige transação já aberta, usado ao adicionar evento à outbox |
| `@Scheduled` | Execução periódica dos despachantes, fora do POST |
| `@JmsListener` | Método chamado ao chegar mensagem do broker |
| `@ConditionalOnProperty` | Liga/desliga determinado componente conforme configuração |
| `@RestControllerAdvice` / `@ExceptionHandler` | Tratamento de exceções na fronteira HTTP, não ack/retry do listener JMS |

## 6. Catálogo das classes de produção

Cada linha aponta ao arquivo atual. Classes pequenas foram mantidas no catálogo para explicar também exceções, DTOs e configurações. Métodos auxiliares devem ser lidos no contexto da responsabilidade, não como novas funcionalidades.

### 6.1 Emissor

| Classe | O que faz |
|---|---|
| [EmissorApplication](../boleto-emissor/src/main/java/br/com/caixa/cobranca/emissor/EmissorApplication.java) | Inicializa o serviço, descobre componentes e habilita agendamento |
| [BoletoController](../boleto-emissor/src/main/java/br/com/caixa/cobranca/emissor/controllers/BoletoController.java) | Recebe `POST /boletos`, chama o service e devolve `202` |
| [ConsultaInternaBoletoController](../boleto-emissor/src/main/java/br/com/caixa/cobranca/emissor/controllers/ConsultaInternaBoletoController.java) | Expõe o aceite original para o fallback da consulta |
| [OutboxFalhasController](../boleto-emissor/src/main/java/br/com/caixa/cobranca/emissor/controllers/OutboxFalhasController.java) | Lista até 20 isolamentos do produtor, com metadados; contém o record de resposta `FalhaOutbox`. Não consulta a DLQ |
| [EmissaoService](../boleto-emissor/src/main/java/br/com/caixa/cobranca/emissor/services/EmissaoService.java) | Valida, canonicaliza conteúdo para fingerprint SHA-256, verifica repetição/conflito e coordena o aceite |
| [EmissaoPersistenceService](../boleto-emissor/src/main/java/br/com/caixa/cobranca/emissor/services/EmissaoPersistenceService.java) | Grava aceite, comando outbox e evento na mesma transação local |
| [OutboxDispatcher](../boleto-emissor/src/main/java/br/com/caixa/cobranca/emissor/services/OutboxDispatcher.java) | Busca até 20 comandos elegíveis por execução, publica e atualiza entrega/falhas/backoff |
| [EmissaoAceitaEntity](../boleto-emissor/src/main/java/br/com/caixa/cobranca/emissor/models/EmissaoAceitaEntity.java) | Guarda UUID, chave de idempotência, conteúdo/fingerprint e recebimento; representa o aceite, não o resultado final |
| [ComandoRegistroOutboxEntity](../boleto-emissor/src/main/java/br/com/caixa/cobranca/emissor/models/ComandoRegistroOutboxEntity.java) | Guarda intenção de comando, contadores, próxima tentativa e isolamento; limpa o JSON após entrega confirmada |
| [EmissaoAceitaRepository](../boleto-emissor/src/main/java/br/com/caixa/cobranca/emissor/repositories/EmissaoAceitaRepository.java) | Consulta/grava aceites e procura por convênio + número de controle |
| [ComandoRegistroOutboxRepository](../boleto-emissor/src/main/java/br/com/caixa/cobranca/emissor/repositories/ComandoRegistroOutboxRepository.java) | Seleciona pendências elegíveis e isolamentos da outbox |
| [RegistroCommandPublisher](../boleto-emissor/src/main/java/br/com/caixa/cobranca/emissor/publishers/RegistroCommandPublisher.java) | Interface da publicação de comando, sem fixar o transporte |
| [JmsRegistroCommandPublisher](../boleto-emissor/src/main/java/br/com/caixa/cobranca/emissor/publishers/JmsRegistroCommandPublisher.java) | Implementação ativa: serializa JSON, verifica destino e envia mensagem JMS persistente/transacional |
| [HttpRegistroCommandPublisher](../boleto-emissor/src/main/java/br/com/caixa/cobranca/emissor/publishers/HttpRegistroCommandPublisher.java) | Adaptador HTTP antigo, sem bean ativo; preservado para regressão, não é fallback automático |
| [EmissaoValidator](../boleto-emissor/src/main/java/br/com/caixa/cobranca/emissor/validators/EmissaoValidator.java) | Especialização local do validador estrutural compartilhado |
| [ConflitoIdempotencia](../boleto-emissor/src/main/java/br/com/caixa/cobranca/emissor/dtos/ConflitoIdempotencia.java) | Corpo de resposta com código/mensagem de conflito |
| [ChaveIdempotenciaConflitanteException](../boleto-emissor/src/main/java/br/com/caixa/cobranca/emissor/exceptions/ChaveIdempotenciaConflitanteException.java) | Representa reutilização da chave com conteúdo diferente |
| [EmissaoInvalidaException](../boleto-emissor/src/main/java/br/com/caixa/cobranca/emissor/exceptions/EmissaoInvalidaException.java) | Carrega a lista de campos inválidos |
| [FalhaAoAceitarEmissaoException](../boleto-emissor/src/main/java/br/com/caixa/cobranca/emissor/exceptions/FalhaAoAceitarEmissaoException.java) | Representa falha técnica ao aceitar com segurança |
| [ErroHandler](../boleto-emissor/src/main/java/br/com/caixa/cobranca/emissor/handlers/ErroHandler.java) | Converte erros de entrada/JSON em `400`, conflito em `409` e falha de aceite em `503` |

### 6.2 Registro

| Classe | O que faz |
|---|---|
| [RegistroApplication](../boleto-registro/src/main/java/br/com/caixa/cobranca/registro/RegistroApplication.java) | Inicializa o serviço e descobre componentes locais/comuns |
| [RegistroCommandListener](../boleto-registro/src/main/java/br/com/caixa/cobranca/registro/listeners/RegistroCommandListener.java) | Recebe comandos JMS, confere envelope/correlação, chama o service e isola inválidos/conflitos |
| [RegistroJmsConfiguration](../boleto-registro/src/main/java/br/com/caixa/cobranca/registro/configs/RegistroJmsConfiguration.java) | Configura listener transacional, concorrência 1 e recuperação de conexão a cada 2 s |
| [RegistroBoletoService](../boleto-registro/src/main/java/br/com/caixa/cobranca/registro/services/RegistroBoletoService.java) | Valida comando/UUID, protege concorrência, consulta resultados e verifica duplicação/conflito; contém o record `ResultadoRegistro` |
| [RegistroPersistenceService](../boleto-registro/src/main/java/br/com/caixa/cobranca/registro/services/RegistroPersistenceService.java) | Chama a registradora e grava resultado/histórico/evento em transação local |
| [RegistradoraSimuladaPersistence](../boleto-registro/src/main/java/br/com/caixa/cobranca/registro/services/RegistradoraSimuladaPersistence.java) | Recupera ou grava a resposta no ledger por UUID, com `REQUIRES_NEW` |
| [ConteudoComando](../boleto-registro/src/main/java/br/com/caixa/cobranca/registro/services/ConteudoComando.java) | Compara comandos JSON removendo somente `correlation_id`; compartilhado entre service local e ledger |
| [RegistradoraClient](../boleto-registro/src/main/java/br/com/caixa/cobranca/registro/clients/RegistradoraClient.java) | Interface da operação de registro externo |
| [RegistradoraSimulada](../boleto-registro/src/main/java/br/com/caixa/cobranca/registro/clients/RegistradoraSimulada.java) | Implementação ativa que usa cenário configurado e ledger; não chama banco externo real |
| [ResultadoRegistradora](../boleto-registro/src/main/java/br/com/caixa/cobranca/registro/dtos/ResultadoRegistradora.java) | Transporta resultado aprovado/rejeitado e seus campos, verificando consistência do resultado |
| [RegistroBoletoEntity](../boleto-registro/src/main/java/br/com/caixa/cobranca/registro/models/RegistroBoletoEntity.java) | Persiste resultado e comando original; cria histórico de origem/transição final e produz projeção de consulta |
| [TransicaoRegistro](../boleto-registro/src/main/java/br/com/caixa/cobranca/registro/models/TransicaoRegistro.java) | Valor embutido do histórico com estado e instante |
| [OperacaoRegistradoraSimuladaEntity](../boleto-registro/src/main/java/br/com/caixa/cobranca/registro/models/OperacaoRegistradoraSimuladaEntity.java) | Persiste comando/resposta da simulação por UUID |
| [RegistroBoletoRepository](../boleto-registro/src/main/java/br/com/caixa/cobranca/registro/repositories/RegistroBoletoRepository.java) | Acesso aos resultados locais pelo identificador |
| [OperacaoRegistradoraSimuladaRepository](../boleto-registro/src/main/java/br/com/caixa/cobranca/registro/repositories/OperacaoRegistradoraSimuladaRepository.java) | Acesso ao ledger da simulação |
| [RegistroConsultaInternaController](../boleto-registro/src/main/java/br/com/caixa/cobranca/registro/controllers/RegistroConsultaInternaController.java) | Expõe o resultado para o cliente HTTP da consulta |
| [RegistroComandoController](../boleto-registro/src/main/java/br/com/caixa/cobranca/registro/controllers/RegistroComandoController.java) | Endpoint HTTP legado de comando; **desligado por padrão**, não participa do fluxo JMS atual |
| [ComandoRegistroInvalidoException](../boleto-registro/src/main/java/br/com/caixa/cobranca/registro/exceptions/ComandoRegistroInvalidoException.java) | Comando/envelope fora das regras |
| [ComandoRegistroConflitanteException](../boleto-registro/src/main/java/br/com/caixa/cobranca/registro/exceptions/ComandoRegistroConflitanteException.java) | Mesmo UUID com conteúdo de negócio divergente |
| [FalhaAoRegistrarBoletoException](../boleto-registro/src/main/java/br/com/caixa/cobranca/registro/exceptions/FalhaAoRegistrarBoletoException.java) | Falha técnica, diferente de rejeição de negócio |
| [RegistroExceptionHandler](../boleto-registro/src/main/java/br/com/caixa/cobranca/registro/handlers/RegistroExceptionHandler.java) | Traduz exceções da fronteira HTTP; retry/quarentena JMS ficam no fluxo do listener/broker |

### 6.3 Consulta

| Classe | O que faz |
|---|---|
| [ConsultaApplication](../boleto-consulta/src/main/java/br/com/caixa/cobranca/consulta/ConsultaApplication.java) | Inicializa serviço de leitura e correlação |
| [ConsultaBoletoController](../boleto-consulta/src/main/java/br/com/caixa/cobranca/consulta/controllers/ConsultaBoletoController.java) | Recebe GET público, interpreta UUID e chama consulta |
| [ConsultaBoletoService](../boleto-consulta/src/main/java/br/com/caixa/cobranca/consulta/services/ConsultaBoletoService.java) | Cache-aside, fontes/fallback, normalização por estado e coordenação entre preenchimento/invalidação |
| [BoletoFonteClient](../boleto-consulta/src/main/java/br/com/caixa/cobranca/consulta/clients/BoletoFonteClient.java) | Interface de busca; valida resposta mínima coerente e impede estado final vindo da fonte de aceite |
| [RestBoletoFonteClient](../boleto-consulta/src/main/java/br/com/caixa/cobranca/consulta/clients/RestBoletoFonteClient.java) | Faz GET interno, propaga correlação, distingue `404` de falha técnica e aplica circuito por fonte |
| [ConsultaClientConfiguration](../boleto-consulta/src/main/java/br/com/caixa/cobranca/consulta/configs/ConsultaClientConfiguration.java) | Cria os dois clientes HTTP, valida timeouts positivos e desliga redirecionamentos |
| [ConsultaCacheConfiguration](../boleto-consulta/src/main/java/br/com/caixa/cobranca/consulta/configs/ConsultaCacheConfiguration.java) | Cria Caffeine, TTL por estado e limite de entradas |
| [ConsultaEventosConfiguration](../boleto-consulta/src/main/java/br/com/caixa/cobranca/consulta/configs/ConsultaEventosConfiguration.java) | Configura consumo JMS transacional de eventos |
| [ConsultaEventoListener](../boleto-consulta/src/main/java/br/com/caixa/cobranca/consulta/listeners/ConsultaEventoListener.java) | Valida evento/correlação, invalida cache em eventos finais e isola eventos inválidos |
| [ErroConsulta](../boleto-consulta/src/main/java/br/com/caixa/cobranca/consulta/dtos/ErroConsulta.java) | Corpo de erro com código, mensagem e correlação |
| [BoletoNaoEncontradoException](../boleto-consulta/src/main/java/br/com/caixa/cobranca/consulta/exceptions/BoletoNaoEncontradoException.java) | Ausência informada por uma fonte |
| [IdentificadorBoletoInvalidoException](../boleto-consulta/src/main/java/br/com/caixa/cobranca/consulta/exceptions/IdentificadorBoletoInvalidoException.java) | Identificador que não pode ser interpretado como UUID |
| [FalhaFonteBoletoException](../boleto-consulta/src/main/java/br/com/caixa/cobranca/consulta/exceptions/FalhaFonteBoletoException.java) | Dependência indisponível, resposta inconsistente ou circuito aberto |
| [ConsultaExceptionHandler](../boleto-consulta/src/main/java/br/com/caixa/cobranca/consulta/handlers/ConsultaExceptionHandler.java) | Produz `400`/`404`/`503`, com erro técnico rastreável e sem mascará-lo como ausência |

### 6.4 Contratos compartilhados

Estes arquivos não iniciam serviços. Os nomes Java são convertidos para `snake_case` no JSON pelas configurações das aplicações.

| Arquivo | O que representa |
|---|---|
| [EmitirBoletoRequest](../shared-contracts/src/main/java/br/com/caixa/cobranca/contracts/EmitirBoletoRequest.java) | Pedido com convênio/controle, pessoas, valor, datas, espécie e opcionais |
| [BoletoAceito](../shared-contracts/src/main/java/br/com/caixa/cobranca/contracts/BoletoAceito.java) | Resposta de aceite com identificador, estado e recebimento |
| [BoletoConsulta](../shared-contracts/src/main/java/br/com/caixa/cobranca/contracts/BoletoConsulta.java) | Projeção de leitura; métodos auxiliares deixam apenas os campos adequados ao estado |
| [RegistrarBoletoCommand](../shared-contracts/src/main/java/br/com/caixa/cobranca/contracts/RegistrarBoletoCommand.java) | Envelope do comando com UUID, aceite, solicitação e correlação; aceita legado sem correlação usando UUID do boleto |
| [EventoBoleto](../shared-contracts/src/main/java/br/com/caixa/cobranca/contracts/EventoBoleto.java) | Envelope mínimo do fato; valida UUIDs, convênio, instante e correspondência tipo/estado |
| [TipoEventoBoleto](../shared-contracts/src/main/java/br/com/caixa/cobranca/contracts/TipoEventoBoleto.java) | `EmissaoAceita`, `BoletoRegistrado`, `RegistroRejeitado` e estados associados |
| [StatusBoleto](../shared-contracts/src/main/java/br/com/caixa/cobranca/contracts/StatusBoleto.java) | Os três estados permitidos |
| [Pessoa](../shared-contracts/src/main/java/br/com/caixa/cobranca/contracts/Pessoa.java) | Nome, tipo/número do documento e endereço |
| [Endereco](../shared-contracts/src/main/java/br/com/caixa/cobranca/contracts/Endereco.java) | Logradouro, cidade, UF e CEP |
| [TipoDocumento](../shared-contracts/src/main/java/br/com/caixa/cobranca/contracts/TipoDocumento.java) | CPF ou CNPJ |
| [Encargo](../shared-contracts/src/main/java/br/com/caixa/cobranca/contracts/Encargo.java) | Tipo, valor e data de início de juros/multa informados |
| [TipoEncargo](../shared-contracts/src/main/java/br/com/caixa/cobranca/contracts/TipoEncargo.java) | Fixo ou percentual |
| [Desconto](../shared-contracts/src/main/java/br/com/caixa/cobranca/contracts/Desconto.java) | Valor e data limite do desconto informado |
| [CampoInvalido](../shared-contracts/src/main/java/br/com/caixa/cobranca/contracts/CampoInvalido.java) | Campo + motivo, sem reproduzir o valor recebido |
| [ErroValidacao](../shared-contracts/src/main/java/br/com/caixa/cobranca/contracts/ErroValidacao.java) | Lista de campos inválidos no retorno |
| [SolicitacaoBoletoValidator](../shared-contracts/src/main/java/br/com/caixa/cobranca/contracts/SolicitacaoBoletoValidator.java) | Validação estrutural comum usada nas duas fronteiras, sem depender de Spring/JPA |
| [package-info](../shared-contracts/src/main/java/br/com/caixa/cobranca/contracts/package-info.java) | Documentação do pacote de contratos |

Validar formato/tamanho de CPF/CNPJ **não verifica dígitos verificadores nem consulta cadastro real**. Espécies aceitas (`DM`/`DS`) são hipóteses didáticas. Juros, multa, desconto e abatimento são dados validados/transportados; não existe motor de pagamento/liquidação calculando uma cobrança real.

### 6.5 Outbox de eventos, correlação e broker

| Classe | O que faz |
|---|---|
| [EventoOutboxEntity](../eventos-outbox/src/main/java/br/com/caixa/cobranca/eventos/EventoOutboxEntity.java) | Persiste JSON do evento, identidade estável, publicação e falhas; unicidade por boleto/tipo |
| [EventoOutboxRepository](../eventos-outbox/src/main/java/br/com/caixa/cobranca/eventos/EventoOutboxRepository.java) | Seleciona eventos elegíveis e acessa as intenções |
| [EventoOutboxService](../eventos-outbox/src/main/java/br/com/caixa/cobranca/eventos/EventoOutboxService.java) | Adiciona a intenção exigindo transação já aberta (`MANDATORY`) |
| [EventoOutboxDispatcher](../eventos-outbox/src/main/java/br/com/caixa/cobranca/eventos/EventoOutboxDispatcher.java) | Publica lotes elegíveis no endereço de eventos, confirma publicação ou registra retry/isolamento |
| [EventosOutboxConfiguration](../eventos-outbox/src/main/java/br/com/caixa/cobranca/eventos/EventosOutboxConfiguration.java) | Configura agendamento, descoberta JPA/repositories e publisher JMS transacional/persistente |
| [Correlacao](../infraestrutura-http/src/main/java/br/com/caixa/cobranca/http/Correlacao.java) | Contexto UUID por thread/MDC; seu escopo restaura/limpa valores ao terminar |
| [CorrelacaoFilter](../infraestrutura-http/src/main/java/br/com/caixa/cobranca/http/CorrelacaoFilter.java) | Valida/gera header, devolve-o nas respostas e registra logs mínimos da requisição |
| [CorrelacaoConfiguration](../infraestrutura-http/src/main/java/br/com/caixa/cobranca/http/CorrelacaoConfiguration.java) | Registra o filtro com prioridade alta nos serviços |
| [BrokerApplication](../cobranca-broker/src/main/java/br/com/caixa/cobranca/broker/BrokerApplication.java) | Cria topologia/persistência/redelivery, lê porta/diretório, inicia em loopback, confirma prontidão e para no encerramento |

### 6.6 Laboratório didático opcional

| Classe/recurso | O que faz |
|---|---|
| [Rastros](../infraestrutura-http/src/main/java/br/com/caixa/cobranca/http/demo/Rastros.java) | Guarda diagnóstico limitado a 2.000 rastros por JVM; commit só é registrado após confirmação da transação |
| [DemonstracaoConfiguration](../infraestrutura-http/src/main/java/br/com/caixa/cobranca/http/demo/DemonstracaoConfiguration.java) | Ativa/desativa o diagnóstico no ciclo de vida do perfil `demonstracao` |
| [RastrosController](../infraestrutura-http/src/main/java/br/com/caixa/cobranca/http/demo/RastrosController.java) | Disponibiliza a janela de rastros de um UUID no perfil |
| [PainelDemonstracaoController](../boleto-consulta/src/main/java/br/com/caixa/cobranca/consulta/controllers/PainelDemonstracaoController.java) | Serve a página, encaminha emissão ao destino local fixo e agrega diagnósticos dos três serviços/quarentena |
| [QuarentenaDemonstracaoController](../boleto-registro/src/main/java/br/com/caixa/cobranca/registro/controllers/QuarentenaDemonstracaoController.java) | Examina até 200 mensagens por browse, sem consumir ou reenviar |
| [HTML](../boleto-consulta/src/main/resources/demonstracao/index.html), [JavaScript](../boleto-consulta/src/main/resources/demonstracao/painel.js) e [CSS](../boleto-consulta/src/main/resources/demonstracao/painel.css) | Formulário, cartões, linha do tempo e respostas HTTP; somente recursos locais, sem dependências de frontend |

As chamadas de diagnóstico estão nas classes já catalogadas dos serviços e despachantes. Fora do perfil, não acumulam rastros nem mudam o cenário da registradora. Isso não é auditoria nem um quarto microsserviço; detalhes e comandos estão no [guia do painel](painel-demonstracao.md).

## 7. APIs, dados e configurações

### 7.1 Endpoints

| Serviço/porta | Método e caminho | Uso |
|---|---|---|
| Emissor/8081 | `POST /boletos` | API de emissão; sucesso `202`, validação `400`, conflito `409`, falha de aceite `503` |
| Consulta/8083 | `GET /boletos/{identificador}` | API de leitura; `200`, identificador inválido `400`, ausência `404`, falha técnica `503` |
| Emissor/8081 | `GET /interno/boletos/{identificador}` | Retorna aceite para o fallback; não é a fonte do estado final |
| Registro/8082 | `GET /interno/boletos/{identificador}` | Retorna resultado conhecido pelo registro; antes disso pode responder `404` |
| Emissor/8081 | `GET /interno/outbox/falhas` | Metadados dos isolamentos de comando no produtor; não lê mensagens da quarentena |
| Registro/8082 | `POST /interno/comandos/registro` | **Legado desativado por padrão**; usado em testes específicos, não no caminho ativo |
| Três serviços | `GET /actuator/health` | Prontidão básica do serviço; observar também logs e fluxo real |

Os nomes “interno” e “falhas” **não são controle de acesso**. Não há autenticação/autorização nestas APIs. Usar somente localmente, com dados sintéticos.

### 7.2 Dados de entrada e saída

O pedido exige convênio de seis dígitos, controle não vazio de até 25 caracteres, beneficiário/pagador com documentos e endereço, valor positivo com até duas casas, datas coerentes e espécie aceita. Complementos condicionais de juros/multa/desconto são exigidos quando esses objetos são enviados. A lista completa está no [validador](../shared-contracts/src/main/java/br/com/caixa/cobranca/contracts/SolicitacaoBoletoValidator.java) e na [matriz E7](etapas/E7-contrato-funcional.md).

O resultado não é o pedido inteiro:

- Aceite: `identificador`, `status`, `data_hora_recebimento`.
- Consulta: esses dados mais campos de registro/pagamento **ou** de rejeição, conforme estado.
- Novas aprovações: marcadores `NAO-PAGAVEL-SIM-LINHA-{uuid}` e `NAO-PAGAVEL-SIM-BARRAS-{uuid}`. **Nunca usar para pagamento.**
- Evento: `id_evento`, `tipo`, `identificador_boleto`, `convenio`, `status`, `data_hora`, `correlation_id`. Sem nome, documento, endereço ou valor.
- Bancos e comandos precisam de mais dados que os eventos; não confundir “evento mínimo” com inexistência de dados pessoais em todo o armazenamento.

### 7.3 Onde os dados ficam

| Local | Dados |
|---|---|
| H2 do emissor: `emissao_aceita` | Aceite, chave única convênio/controle, fingerprint e pedido serializado |
| H2 do emissor: `comando_registro_outbox` | Intenção de registro, situação de entrega e tentativas |
| H2 do registro: `boleto_registrado` | Resultado local e comando recebido |
| H2 do registro: `registro_historico` | Origem e transição final dos novos resultados |
| H2 do registro: `registradora_simulada_operacao` | Ledger do efeito externo simulado |
| H2 de **cada produtor**: `evento_outbox` | Intenções de eventos daquele produtor; não é uma tabela central |
| Journal do Artemis | Mensagens/filas duráveis, assinaturas e metadados de entrega |
| Memória da consulta | Entradas Caffeine e circuitos locais; reinício limpa esses estados |

Com os diretórios de execução do [README](../README.md), o emissor usa `data\emissor` e o registro `data\registro` nas respectivas pastas dos módulos; o broker usa `data\broker` na pasta dele. H2 cria seus arquivos a partir desses caminhos. Rodar de outra pasta muda o destino dos caminhos relativos.

Não enviar bancos/journal/payloads ao Git e não apagar arquivos para “resolver” reentrega ou conflito. O [schema SQL do registro](../boleto-registro/src/main/resources/schema.sql) é uma compatibilidade para arquivos antigos, não uma rotina de limpeza: permite resultado rejeitado sem campos exclusivos de aprovação. Registros finais antigos são preservados, sem backfill inventado de dados/eventos/histórico.

### 7.4 Configurações que um colega precisa reconhecer

Fontes: configurações do [emissor](../boleto-emissor/src/main/resources/application.yml), [registro](../boleto-registro/src/main/resources/application.yml) e [consulta](../boleto-consulta/src/main/resources/application.yml).

| Configuração | Finalidade |
|---|---|
| `BROKER_URL` | URL completa do broker nos três serviços; preservar limites ao trocar porta |
| `EMISSOR_DB_URL` / `REGISTRO_DB_URL` | Substituir destino do H2; não apontar os dois serviços ao mesmo banco |
| `REGISTRADORA_CENARIO` | No registro: `APROVAR` (padrão), `REJEITAR` ou `INDISPONIVEL`; vale para novas operações, não desfaz resultado no ledger |
| `OUTBOX_DISPATCH_ENABLED` / `OUTBOX_DISPATCH_INTERVAL_MS` | Ativação e intervalo de despacho de comandos; padrão ligado e 1000 ms |
| `REGISTRO_MENSAGERIA_ENABLED` | Ativar listener de registro; padrão ligado |
| `CONSULTA_EVENTOS_ENABLED` | Ativar consumo de eventos na consulta; padrão ligado |
| `eventos.outbox.enabled` / `eventos.outbox.interval-ms` | Ativação do dispatcher de eventos e intervalo; padrão ligado e 1000 ms |
| `integracoes.*.base-url` na consulta | Localização HTTP do emissor e registro |
| `integracoes.connect-timeout-ms` / `integracoes.read-timeout-ms` | Limites HTTP positivos, padrão 2000/3000 ms |
| `--port` / `--data` do broker | Porta e diretório persistente do broker |
| `--server.address=127.0.0.1` | Restringir os serviços à máquina local, como nos comandos do README |

Desativadores são usados em testes e cenários controlados; desligar listener/dispatcher na demonstração interrompe partes do fluxo. Não é fallback automático.

O [POM pai](../pom.xml) agrega módulos/versões e ativa o perfil JVM por padrão; cada módulo tem seu POM com dependências próprias. Perfis A/C são referências conceituais, não outros ambientes prontos. `src/main/resources` contém configuração/recursos da aplicação; `src/test/resources`, configuração dos testes; `target` é saída gerada de build, não código a editar. O [logback do broker](../cobranca-broker/src/main/resources/logback.xml) configura sua saída de logs.

## 8. Como estudar e experimentar

### 8.1 Percurso pelo código

Abra cada caminho nesta ordem, acompanhando as chamadas pelo IDE:

1. Emissão: [controller](../boleto-emissor/src/main/java/br/com/caixa/cobranca/emissor/controllers/BoletoController.java) → [service](../boleto-emissor/src/main/java/br/com/caixa/cobranca/emissor/services/EmissaoService.java) → [persistência transacional](../boleto-emissor/src/main/java/br/com/caixa/cobranca/emissor/services/EmissaoPersistenceService.java).
2. Transporte: [dispatcher](../boleto-emissor/src/main/java/br/com/caixa/cobranca/emissor/services/OutboxDispatcher.java) → [publisher JMS](../boleto-emissor/src/main/java/br/com/caixa/cobranca/emissor/publishers/JmsRegistroCommandPublisher.java) → [listener](../boleto-registro/src/main/java/br/com/caixa/cobranca/registro/listeners/RegistroCommandListener.java).
3. Resultado: [service do registro](../boleto-registro/src/main/java/br/com/caixa/cobranca/registro/services/RegistroBoletoService.java) → [persistência local](../boleto-registro/src/main/java/br/com/caixa/cobranca/registro/services/RegistroPersistenceService.java) → [ledger simulado](../boleto-registro/src/main/java/br/com/caixa/cobranca/registro/services/RegistradoraSimuladaPersistence.java).
4. Leitura: [controller](../boleto-consulta/src/main/java/br/com/caixa/cobranca/consulta/controllers/ConsultaBoletoController.java) → [service/cache](../boleto-consulta/src/main/java/br/com/caixa/cobranca/consulta/services/ConsultaBoletoService.java) → [cliente REST](../boleto-consulta/src/main/java/br/com/caixa/cobranca/consulta/clients/RestBoletoFonteClient.java).
5. Atualização: [publicação de evento](../eventos-outbox/src/main/java/br/com/caixa/cobranca/eventos/EventoOutboxDispatcher.java) → [listener da consulta](../boleto-consulta/src/main/java/br/com/caixa/cobranca/consulta/listeners/ConsultaEventoListener.java) → invalidação do cache.

### 8.2 Exercício orientado, ainda não atribuído aos integrantes

Este é um percurso de aprendizagem, **não uma evidência de que alguém já o executou**:

1. Seguir os comandos dos quatro terminais no [README](../README.md), sem Docker.
2. Criar um pedido sintético a partir do método `corpo` no [teste do controller](../boleto-emissor/src/test/java/br/com/caixa/cobranca/emissor/integration/BoletoControllerTest.java). Usar um número de controle novo para nova emissão.
3. Enviar ao POST do emissor e guardar o UUID retornado. A resposta é `202`, não uma promessa de já estar registrado.
4. Consultar esse UUID em `http://127.0.0.1:8083/boletos/{identificador}`; não consultar o aceite interno como se fosse resultado final.
5. Reenviar o mesmo pedido: deve retornar o aceite original, sem outro boleto. Alterar valor mantendo convênio/controle: deve conflitar com `409`.
6. Localizar nos logs a correlação do POST e as mensagens do processamento. Cada GET pode ter sua própria correlação.
7. Estudar os testes de rejeição e falha técnica antes de alterar cenários. Alterar variável de ambiente do registro exige reiniciar esse processo; usar outro UUID/controle para testar novo resultado.
8. Anotar o que observou, o que não entendeu e o que validou pessoalmente. Encerrar serviços e depois broker conforme README.

Não é preciso provocar todas as falhas manualmente. A F1 seleciona uma demonstração curta e aproveita testes existentes. Não usar dados de clientes reais e não publicar bancos/journal nos commits.

### 8.3 Situações que confundem no início

| Observação | Como interpretar/investigar |
|---|---|
| POST `202` enquanto broker está parado | Pode ser correto: aceite e intenções foram guardados. Verificar depois despacho/tentativas; não garante sucesso após esgotamento |
| UUID permanece aguardando | Verificar cenário, logs do listener, outboxes e isolamento; não inventar rejeição |
| Endpoint de falhas retorna lista vazia | Não prova ausência de DLQ; ele só lista isolamentos de comando do produtor |
| Registro retorna `404` logo após emissão | Pode ser a janela antes do processamento; a API pública usa fallback ao emissor |
| Fonte caiu, mas GET ainda retorna `200` | Pode ser cache hit; no miss com fonte necessária indisponível deve ser falha técnica |
| Mensagem aparece mais de uma vez | É previsto na garantia; verificar que o efeito/histórico não duplicaram |
| Trocar cenário não altera boleto já final | Resultado idempotente está persistido; usar solicitação nova, não apagar banco |
| Reinício “perdeu” banco | Conferir diretório de execução e URL H2 antes de qualquer ação; caminhos são relativos |
| Build passou, mas demo falha | Testes usam recursos isolados; conferir portas, variáveis e arquivos locais da demonstração |

## 9. Testes e critérios da avaliação

### 9.1 Como os testes ajudam a entender o sistema

Na raiz do projeto, `mvn -B clean verify` compila, executa testes, empacota e faz a comparação final do pact. `mvn test` sozinho **não executa o Failsafe da comparação**. Os testes usam H2/brokers isolados, sem exigir Docker nem um broker externo previamente iniciado.

| Evidência | O que ensina/verifica |
|---|---|
| [EmissaoValidatorTest](../boleto-emissor/src/test/java/br/com/caixa/cobranca/emissor/validators/EmissaoValidatorTest.java) | Regras de entrada e campos condicionais |
| [BoletoControllerTest](../boleto-emissor/src/test/java/br/com/caixa/cobranca/emissor/integration/BoletoControllerTest.java) | Aceite HTTP, repetição, conflito e concorrência |
| [OutboxDispatcherTest](../boleto-emissor/src/test/java/br/com/caixa/cobranca/emissor/services/OutboxDispatcherTest.java) | Contadores, backoff e isolamento do produtor |
| [OutboxEntregaJmsTest](../boleto-emissor/src/test/java/br/com/caixa/cobranca/emissor/integration/OutboxEntregaJmsTest.java) | Publicação real, confirmação, duplicação possível e reinício |
| [DominioRegistroTest](../boleto-registro/src/test/java/br/com/caixa/cobranca/registro/integration/DominioRegistroTest.java) | Dados finais, histórico, idempotência externa simulada e rollback |
| [RegistroEntregaJmsTest](../boleto-registro/src/test/java/br/com/caixa/cobranca/registro/integration/RegistroEntregaJmsTest.java) | Ack após commit, reentrega, rejeição, falhas temporárias/permanentes e quarentena |
| [ConsultaBoletoControllerTest](../boleto-consulta/src/test/java/br/com/caixa/cobranca/consulta/integration/ConsultaBoletoControllerTest.java) | Cache, fontes, estados e respostas HTTP |
| [ConsultaEventosTest](../boleto-consulta/src/test/java/br/com/caixa/cobranca/consulta/integration/ConsultaEventosTest.java) | Invalidação, duplicata, ordem e corrida entre leitura/evento |
| [ResilienciaFonteTest](../boleto-consulta/src/test/java/br/com/caixa/cobranca/consulta/clients/ResilienciaFonteTest.java) | Circuitos, recuperação, timeout real, redirecionamentos e correlação |
| [BrokerPersistenceTest](../cobranca-broker/src/test/java/br/com/caixa/cobranca/broker/BrokerPersistenceTest.java) | Journal/reinício, redelivery, filas e assinantes independentes |
| [EventoOutboxTest](../eventos-outbox/src/test/java/br/com/caixa/cobranca/eventos/EventoOutboxTest.java) | Persistência/publicação de eventos e recuperação |
| [CorrelacaoFilterTest](../infraestrutura-http/src/test/java/br/com/caixa/cobranca/http/CorrelacaoFilterTest.java) | Validação do header, respostas e isolamento de contexto |

Há outros testes de inicialização, regras, eventos dos produtores e adaptadores legados nas respectivas pastas de teste. Isso é cobertura de comportamento, não uma quantidade mínima exigida pelo professor.

### 9.2 O PACT em linguagem simples

A consulta diz: “quando peço este boleto, espero estes campos e status”. O registro precisa provar que responde assim.

1. [Teste consumidor](../boleto-consulta/src/test/java/br/com/caixa/cobranca/consulta/contracts/RegistroContractTest.java): exercita o cliente real contra mock PACT e gera contrato.
2. [Teste provedor](../boleto-registro/src/test/java/br/com/caixa/cobranca/registro/contracts/RegistroProviderTest.java): verifica o contrato contra HTTP real do registro com H2.
3. [Arquivo canônico](../shared-contracts/pacts/boleto-consulta-boleto-registro.json): acordo versionado para registrado, rejeitado e ausência.
4. [PactArtifactIT](../boleto-consulta/src/test/java/br/com/caixa/cobranca/consulta/contracts/PactArtifactIT.java): impede que o contrato gerado se afaste do canônico.

Compartilhar DTO ajuda a compilar, mas não prova que uma API responde corretamente; por isso o contrato executável é separado. Não há segundo par PACT nem contrato de mensagem PACT implementado.

### 9.3 Ligação com os nove critérios

| Critério | Parte do sistema que o demonstra |
|---|---|
| 1. Decomposição | Responsabilidades dos três serviços, bases separadas e chamadas por fronteiras |
| 2. Assíncrono | Comando por fila, eventos e garantia de entrega declarada |
| 3. Idempotência/consistência | Chaves, comparação de conteúdo, outboxes, ledger e teste de duplicata |
| 4. Cache | Cache-aside, TTL, invalidação e fallback explícitos |
| 5. Resiliência | Retry/backoff, quarentena, limites HTTP e circuitos |
| 6. Testabilidade | Testes sem Docker e PACT executável |
| 7. Decisões | [ADRs](adr/) com alternativas, custos e limitações |
| 8. Uso crítico de IA | Relato honesto do uso, erros encontrados e validação humana real |
| 9. Execução | Perfil B declarado, instruções e evidência da versão que será demonstrada |

Pesos, caminhos e níveis ainda a concluir ficam na [autoavaliação](../AVALIACAO.md). Criar este guia não conclui automaticamente a reflexão humana, participação ou domínio dos conceitos pelo grupo.

## 10. O que foi construído em cada etapa

| Etapa | Entrega e motivo |
|---|---|
| E0 | Base Maven/Java/Spring, módulos e perfil de execução |
| E1 | Aceitar emissão válida e recusar dados inválidos |
| E2 | Persistir aceite e tratar repetição/conflito sem duplicar |
| E3 | Consulta/cache e fallback após `404` do registro |
| E4 | Outbox transacional do comando; primeiro transporte HTTP provisório |
| E5 | Retry limitado, backoff, isolamento e rejeição de negócio |
| E6 | Contrato PACT consulta ↔ registro |
| E7 | Validação nas duas fronteiras, dados simulados não pagáveis, histórico e ledger |
| E8 | Broker Artemis real em JVM própria, fila durável/quarentena e confirmação; substitui transporte HTTP |
| E9 | Eventos com outbox nos produtores e invalidação coordenada do cache |
| E10 | Timeout/circuit breaker nas fontes e correlação ponta a ponta |
| Organização posterior | Pacotes por tipo de classe e testes agrupados, sem alterar funcionalidades |

Detalhes cronológicos, erros e aprendizados estão em [docs/etapas](etapas/). As etapas não são quinze requisitos do professor. Histórico de versões: E6–E9 foram consolidadas em `529bd5d`, E10 em `0ae4139`, organização de classes em `439e610`; marcos anteriores permanecem no Git.

## 11. O que não existe e o que falta

### Implementado de verdade

APIs Spring, H2 persistido local, Artemis com journal, fila/pub-sub, outboxes, consumidor idempotente, cache Caffeine, circuit breaker, correlação e testes/PACT.

### Simulado ou limitado

- Registradora é Java/H2 local, não integração bancária.
- Campos de pagamento são marcadores deliberadamente não pagáveis.
- Uma instância por serviço; não há prova de alta disponibilidade.
- Testes determinísticos e HTTP local real não comprovam capacidade em produção.
- Não foram medidos p95 < 100 ms, 500 emissões/s ou a proporção 20:1.

### Fora da entrega atual

Sem pagamento/liquidação/baixa, cancelamento, notificação real, SAGA adicional, Kafka, autorização por convênio, autenticação, TLS, auditoria persistida, criptografia comprovada, retenção formal ou replay administrativo auditado.

A orientação docente de seguir apenas a rubrica foi informada pelo usuário e está registrada na [ADR-014](adr/ADR-014-prioridade-core-e-limites-de-escopo.md). Isso não transforma requisitos excluídos em implementados nem autoriza uso real. **Somente demonstração local, em loopback, com dados sintéticos.**

### Ainda falta para fechar

Conforme o [roteiro](../ROTEIRO.md):

- **F1:** conferir a demonstração da versão atual e selecionar evidências existentes.
- **F2:** preencher integrantes/papéis, níveis por critério e relato de uso de IA/validações pessoais.
- **F3:** preparar defesa, conferir acesso ao material de entrega e fechar por último.

Como a produção foi centralizada segundo o relato do usuário, documentar esse processo com honestidade. Os colegas podem estudar, reproduzir cenários e participar da defesa a partir de agora, mas só registrar essas atividades como realizadas depois de acontecerem. Não inventar autoria, participação anterior ou validação manual.

## 12. Perguntas para treinar a defesa

1. **Por que três serviços?** Separar aceite, processamento e leitura; cada um tem responsabilidade própria. Dois atenderiam ao mínimo, mas o corte atual foi justificado; custa três aplicações e contratos.
2. **Por que não retornar “registrado” no POST?** O resultado é assíncrono. `202` confirma o aceite persistido, não a execução futura.
3. **Por que outbox se já temos broker?** Banco local e publicação não são atômicos juntos; outbox preserva a intenção antes do envio.
4. **A fila impede duplicata?** Não. Reentrega é possível; idempotência protege o efeito.
5. **Idempotência da emissão e do consumidor são iguais?** Objetivo semelhante, chaves/fronteiras diferentes: convênio/controle no aceite; UUID/conteúdo no registro.
6. **Por que não ler o H2 do outro serviço?** Propriedade dos dados e independência dos contextos; APIs/mensagens são as fronteiras.
7. **Por que o fallback só acontece após `404`?** Aceite pode existir antes de resultado. Falha técnica não prova ausência e não deve ser mascarada.
8. **TTL sozinho resolve?** Limita defasagem, mas evento final invalida antes; coordenação evita recolocar dado antigo numa corrida.
9. **Rejeição vai para DLQ?** Não; é resultado de negócio. Mensagem inválida ou falha técnica esgotada vai para quarentena.
10. **Por que não SAGA?** Não há processo de negócio selecionado que exija uma compensação adicional; adotar o padrão só pelo nome aumentaria complexidade.
11. **Por que Artemis, não Kafka/RabbitMQ/Docker?** Perfil JVM sem Docker; Artemis fornece fila/pub-sub duráveis e JMS no ambiente escolhido. Não precisamos de log/replay Kafka.
12. **O que aprendemos com a IA?** Cada integrante deve responder com seu uso real, decisões revisadas e verificações pessoais; não decorar um relato de participação inexistente.

**Uma boa resposta de arquitetura:** “Escolhemos X, abrimos mão de Y e aceitamos esse custo porque Z faz sentido neste demonstrador”. Os documentos apoiam a defesa; não substituem a compreensão.
