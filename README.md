# Central de Cobrança e Boletos

Projeto final em grupo — BE-JV-010 (Nível III). Escopo: **CORE**.

## Problema
Empresas emitem cobranças e não sabem se foram apenas recebidas, registradas ou rejeitadas. A Central **aceita** a emissão de forma assíncrona (202), **registra** o título numa registradora simulada por outbox e entrega HTTP assíncrona (transporte provisório, sem broker) e permite **consultar** o estado com cache. Não movimenta dinheiro. Detalhes: [`requisitos/`](requisitos/README.md).

## Arquitetura (resumo)
3 serviços com bases segregadas + `shared-contracts`. Ver [`docs/arquitetura.md`](docs/arquitetura.md) e [`docs/adr/`](docs/adr/).

## Perfil de execução
**Plano B (pura-JVM, sem Docker)** — padrão. Na E4, outbox transacional + HTTP assíncrono substituem provisoriamente o broker; a interface de transporte permite migrar para RabbitMQ quando Docker estiver disponível. Ver [ADR-007](docs/adr/ADR-007-transporte-assincrono.md).

## Como rodar
Requer JDK 17 e Maven, na rede com acesso ao Nexus corporativo.
```bash
mvn -B clean install                               # compila, testa e instala os módulos localmente
mvn -pl boleto-registro spring-boot:run            # terminal 1, 8082 (inicie primeiro)
mvn -pl boleto-emissor spring-boot:run             # terminal 2, 8081
mvn -pl boleto-consulta spring-boot:run            # terminal 3, 8083
```
O `install` inicial é necessário porque os serviços dependem do `shared-contracts` e do POM pai, que precisam estar no repositório Maven local antes de executar cada módulo isoladamente. Para confirmar: `curl.exe -i http://localhost:8081/actuator/health` (repita nas portas 8082 e 8083) → HTTP 200 e `{"status":"UP"}`. Cada serviço grava seu H2 em arquivo na pasta `data/` do próprio módulo; os testes automatizados usam bancos em memória. O emissor guarda comandos pendentes na outbox e tenta entregá-los ao registro em segundo plano.

## Resiliência provisória (E5)
O transporte HTTP faz até **três tentativas totais**, com esperas mínimas de 1 s e 5 s entre falhas temporárias. Falhas permanentes são isoladas imediatamente; após esgotar o retry, o comando fica preservado para análise, sem reenvio automático. `GET http://localhost:8081/interno/outbox/falhas` lista até 20 isolamentos recentes, somente com metadados.

Rejeição de negócio é persistida como `REJEITADO` e confirma o processamento; não é falha de transporte. No serviço de registro, a variável `REGISTRADORA_CENARIO` permite simular `APROVAR` (padrão), `REJEITAR` ou `INDISPONIVEL`. Ver [E5](docs/etapas/E5-resiliencia-outbox.md) e [ADR-008](docs/adr/ADR-008-retry-e-isolamento-outbox.md).

Não há RabbitMQ/DLQ de broker nesta versão. O modelo permite reentregas e exige idempotência; o limite de retry não garante registro eventual durante falhas prolongadas. Não há endpoint de replay: comandos isolados precisam de análise antes de um futuro reprocessamento seguro. Endpoints `/interno/...` devem ficar em rede confiável; autenticação entre serviços não foi implementada.

## Documentação do processo
- [`ROTEIRO.md`](ROTEIRO.md) — etapas E0…E6
- [`docs/etapas/`](docs/etapas/) — o que foi feito e o que aprendemos em cada etapa (inclui [E2](docs/etapas/E2-idempotencia.md), [E3](docs/etapas/E3-consulta-e-cache.md), [E4](docs/etapas/E4-comunicacao-assincrona.md) e [E5](docs/etapas/E5-resiliencia-outbox.md))
- [`docs/validacao-pendente.md`](docs/validacao-pendente.md) — resultados e testes manuais pendentes
- [`AVALIACAO.md`](AVALIACAO.md) — auto-avaliação por critério

## Uso de IA
Ver `AVALIACAO.md` §8 e `docs/etapas/`.
