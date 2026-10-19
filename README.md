# Central de Cobrança e Boletos

Projeto final em grupo — BE-JV-010 (Nível III). Escopo: **CORE**.

## Problema
Empresas emitem cobranças e não sabem se foram apenas recebidas, registradas ou rejeitadas. A Central **aceita** a emissão de forma assíncrona (202), **registra** o título numa registradora (simulada) por fila e permite **consultar** o estado com cache. Não movimenta dinheiro. Detalhes: [`requisitos/`](requisitos/README.md).

## Arquitetura (resumo)
3 serviços com bases segregadas + `shared-contracts`. Ver [`docs/arquitetura.md`](docs/arquitetura.md) e [`docs/adr/`](docs/adr/).

## Perfil de execução
**Plano B (pura-JVM, sem Docker)** — padrão. Fallbacks: a declarar conforme as etapas.

## Como rodar
Requer JDK 17 e Maven, na rede com acesso ao Nexus corporativo.
```bash
mvn clean verify                                   # compila e roda os testes
mvn -pl boleto-emissor  spring-boot:run            # 8081
mvn -pl boleto-registro spring-boot:run            # 8082
mvn -pl boleto-consulta spring-boot:run            # 8083
```
Checar: `http://localhost:8081/actuator/health` → `{"status":"UP"}`.

## Documentação do processo
- [`ROTEIRO.md`](ROTEIRO.md) — etapas E0…E6
- [`docs/etapas/`](docs/etapas/) — o que foi feito e o que aprendemos em cada etapa
- [`docs/validacao-pendente.md`](docs/validacao-pendente.md) — comandos a rodar na rede corporativa
- [`AVALIACAO.md`](AVALIACAO.md) — auto-avaliação por critério

## Uso de IA
Ver `AVALIACAO.md` §8 e `docs/etapas/`.
