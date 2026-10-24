# ADR-001 — Stack: Java 17 LTS, Maven Multi-Módulo e Execução Autônoma em JVM

## Contexto

A Central de Cobrança e Boletos foi concebida para operar no ecossistema de TI bancária corporativa, integrando-se a sistemas legados, ERPs de clientes corporativos e câmaras de registro centralizado. 

O parque de infraestrutura institucional de ambientes altamente regulados possui fortes diretrizes de conformidade e segurança:
1. **Versão Homologada:** O **Java 17 LTS** é a plataforma padrão estável, com suporte de longo prazo corporativo e compatibilidade total com os frameworks de mercado e as esteiras de build internas.
2. **Ambiente de Execução Restrito:** Servidores de aplicação on-premise e ambientes corporativos frequentemente possuem restrições severas ao uso de daemons de contêineres (Docker) por políticas de privilégio e auditoria. O sistema deve ser capaz de ser executado, testado e mantido em processos JVM autônomos puros, garantindo portabilidade em servidores físicos, máquinas virtuais ou contêineres sem acoplamento a ferramentas externas de virtualização.
3. **Independência de Componentes:** O sistema precisa de separação estrita de contratos e ciclos de vida de build, preparando a organização para entregas independentes entre equipes.

## Decisão

- **Java 17 LTS** e **Spring Boot 3.3.5** como fundação tecnológica padrão, garantindo conformidade com a especificação Jakarta EE e bibliotecas estáveis de mensageria e resiliência.
- **Maven Multi-Módulo**: arquitetura modular com POM pai agregador, biblioteca isolada de contratos canônicos (`shared-contracts`), módulos de infraestrutura reutilizável e microsserviços autônomos (`boleto-emissor`, `boleto-registro`, `boleto-consulta`, `cobranca-broker`).
- **Execução Pura em JVM (Sem Dependência de Docker):** os microsserviços e o broker executam como processos Java independentes comunicando-se via rede loopback local (`127.0.0.1`), garantindo compatibilidade universal com qualquer servidor que disponha de uma JVM.
- **Segregação de Armazenamento Local:** persistência em bases relacionais dedicadas por serviço, usando H2 em arquivo para persistência de dados e outbox entre reinicializações dos serviços, e H2 em memória em suites de testes automatizados isolados.

## Alternativas consideradas

- **Java 21:** Rejeitada para o lançamento inicial. Embora introduza Virtual Threads, o Java 17 LTS possui homologação consolidada no parque tecnológico e satisfaz com folga os requisitos de latência e concorrência do domínio.
- **Monólito em Projeto Único:** Rejeitada. Acoplaria o código-fonte, misturaria dependências de bibliotecas de mensageria com borda HTTP e impediria a independência de implantação entre os contextos de negócio de emissão e registro.
- **Dependência Mandatória de Docker / Contêineres:** Rejeitada. Criaria atrito imediato de conformidade de segurança e impediria a execução em servidores com restrições a privilégios de virtualização local. A arquitetura em processos Java desacoplados permite migrar para contêineres no futuro sem nenhuma alteração de código.

## Consequências

- **Positivas:**
  - Código determinístico, portável e compatível com as esteiras corporativas e servidores da instituição.
  - Ausência de dependência de privilégios de sistema operacional ou ferramentas pesadas de virtualização.
  - Modularidade clara: cada microsserviço declara apenas suas dependências estritamente necessárias.
- **Custos / Mitigações:**
  - Exige gerenciar o ciclo de vida de múltiplos processos JVM durante testes manuais integrados, mitigado por scripts padronizados de execução e portas locais bem delimitadas.

## Status

Aceita e implementada na fundação da arquitetura.
