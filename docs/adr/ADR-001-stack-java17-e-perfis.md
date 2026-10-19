# ADR-001 — Stack: Java 17, Maven multi-módulo e perfis de execução

## Contexto
Precisamos de uma base técnica que **todo o grupo consiga compilar e rodar** (E0). O gabarito PIX usa Java 21, mas a máquina de desenvolvimento tem **JDK 17**. A turma não usa Docker (Plano B). O brief exige multi-módulo com perfis A/B/C.

## Decisão
- **Java 17** + **Spring Boot 3.3.5** (suporta 17+; mesma versão do gabarito).
- **Maven multi-módulo**: pai agregador + `shared-contracts` + 3 serviços.
- **Perfis**: `plano-b-jvm` (padrão), `plano-a-docker`, `plano-c-conceitual`. Na E0 estão vazios; cada etapa adiciona só a infra que passa a usar.
- `boleto-emissor` e `boleto-registro` mantêm bases H2 segregadas. No perfil local de execução, as bases usam arquivos para preservar outbox e registros após reiniciar o serviço; os testes automatizados continuam usando H2 em memória. A consulta não tem base própria (fonte definida na ADR-004).

## Alternativas consideradas
- **Java 21 como o gabarito** — exigiria instalar outro JDK; sem ganho para o escopo. **Rejeitada.**
- **Declarar todos os starters (AMQP, Kafka, Cache) já na E0** — mais fácil depois, mas traz dependências que ainda não explicamos e pode quebrar a resolução no Nexus. **Rejeitada.**
- **Projeto único (monólito)** — não atende o CORE (≥ 2 serviços com bases segregadas). **Rejeitada.**

## Consequências
- Código é compatível com 17 (sem recursos exclusivos do 21).
- Builds dependem do Nexus corporativo (rede do trabalho/VPN).

## Status
Aceita
