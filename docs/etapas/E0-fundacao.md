# E0 — Fundação (esqueleto Maven)

**Status:** código escrito · **build ainda NÃO validado** (sem acesso ao Nexus; ver [`../validacao-pendente.md`](../validacao-pendente.md)).
**Referência no gabarito:** `projeto-guiado-pix/gabarito/aula-01` (poms) · **Decisão:** [ADR-001](../adr/ADR-001-stack-java17-e-perfis.md)

## Objetivo
Ter um repositório que compile, com a estrutura exigida pelo brief e **um módulo por microsserviço**, antes de escrever qualquer regra de negócio.

## O que foi criado

```
projeto-final-grupo/
├── pom.xml                  ← pai (agregador)
├── shared-contracts/        ← records compartilhados (vazio por enquanto)
├── boleto-emissor/   :8081  ← H2 própria
├── boleto-registro/  :8082  ← H2 própria
├── boleto-consulta/  :8083  ← sem base ainda
├── docs/{adr, etapas, arquitetura.md}
├── README.md · AVALIACAO.md · ROTEIRO.md
└── requisitos/
```

## Conceitos (o que você precisa saber explicar na banca)

**1. POM pai × módulos.** O `pom.xml` da raiz tem `<packaging>pom</packaging>`: não gera jar, só **agrega** os módulos e centraliza versões. Cada módulo declara `<parent>` apontando para ele e herda `java.version`, o `spring-boot-starter-parent` e o `dependencyManagement`. `mvn verify` na raiz constrói todos na ordem de `<modules>`.

**2. `dependencyManagement` × `dependencies`.** `dependencyManagement` só *declara a versão* (não adiciona a lib). Por isso o `shared-contracts` aparece lá com `${project.version}` e nos módulos sem `<version>`.

**3. Por que `shared-contracts` é pobre.** É o único acoplamento *permitido* entre serviços: só records de mensagens/DTOs. Se começar a ter regra de negócio ali, os serviços deixam de ser independentes (critério 1 da rubrica).

**4. Bases segregadas já na E0.** Cada serviço tem seu `application.yml` com **URL H2 diferente** (`mem:emissor`, `mem:registro`) — nenhum lê a base do outro. É a materialização do ADR de bases segregadas (E1). A consulta ficou sem base de propósito: qual é a sua fonte de dados é a decisão em aberto do glossário §9.

**5. Perfis A/B/C.** `plano-b-jvm` é `activeByDefault`. Estão vazios de propósito: só adicionaremos Caffeine na E3 e RabbitMQ/Qpid na E4/E5, quando formos explicá-los. (Diferente do gabarito, que já os traz todos.)

**6. Smoke test.** `@SpringBootTest` + método vazio prova que o contexto Spring sobe (beans, JPA e H2 configurados). É o menor teste útil e já faz `mvn verify` ter algo para validar.

## Diferenças em relação ao gabarito (e por quê)
| Gabarito | Nosso | Motivo |
|---|---|---|
| Java 21 | Java 17 | JDK instalado é 17 |
| Perfis com Qpid/Kafka/Caffeine já declarados | Perfis vazios | Só entra o que já foi explicado |
| pacote `br.com.caixa.pix` | `br.com.caixa.cobranca` | Nosso domínio |

## Evidência para a rubrica
Critério 1 (início) e 9 (execução): estrutura multi-módulo + README com como rodar.

## Para você pensar
- Por que o `h2` está com `<scope>runtime</scope>`?
- O que aconteceria se a consulta lesse diretamente a base H2 do registro?
