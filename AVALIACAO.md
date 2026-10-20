# Auto-avaliação — Central de Cobrança e Boletos
Grupo: **3 integrantes confirmados** — <nomes e papéis — PREENCHER>
Tema/domínio: Central que aceita emissões de boleto (202), registra o título numa registradora simulada via outbox + fila Artemis e permite consultar o estado com cache.
Perfil de execução: B (pura-JVM), **sem Docker na entrega**. E8/E9: comandos e eventos no mesmo broker JVM separado.

> Esqueleto criado na E0. Os níveis (0–3) e evidências são preenchidos conforme as etapas avançam.

## Situação da entrega

**Em desenvolvimento, sem autoavaliação final.** O [roteiro revisado](ROTEIRO.md) compara todas as aulas, o exemplo PIX e os critérios do [brief](brief-alunos.md), registra E0–E9 e planeja até E15. Fechamento apenas na E15, depois da validação integrada E14.

Pendências principais: breaker/correlação (E10); acesso e auditoria (E11); replay e retenção (E12). E8 entregou fila/quarentena duráveis; E9 adicionou eventos mínimos com intenção atômica e invalidação coordenada de cache, preservando domínio/Pact E7. Finais antigos permanecem preservados sem fatos retroativos inventados. Proteção e retenção são requisitos do tema, não opcionais automaticamente dispensados.

Os pesos usados são os do brief: **15/15/12/10/12/13/13/5/5 = 100**. A divergência 12/12 no material da Aula 9 está registrada no roteiro para confirmação. Não atribuir nota por funcionalidade apenas planejada.

## Evidências por critério
1. Decomposição de domínio — nível: _
   Evidência: (E1) módulos `boleto-*`, bases H2 separadas, `docs/arquitetura.md`, ADR-002/003
2. Comunicação assíncrona — _
   Evidência: (E8) outbox transacional + JMS, fila Artemis persistente, consumidor idempotente; garantia at-least-once. Publicação com consumidor offline, reinício do broker e consumo posterior comprovados em quatro JVMs: [E8](docs/etapas/E8-broker-jvm.md). (E9) três eventos em pub/sub durável e dois assinantes independentes de teste, um offline/reinício: [E9](docs/etapas/E9-eventos-e-cache.md).
3. Idempotência e consistência — _
   Evidência: (E2) emissor persiste o aceite; mesma chave e conteúdo retorna a resposta original sem duplicar; conteúdo diferente retorna 409. (E4) consumidor idempotente. (E7) ledger simulado em transação independente, histórico atômico de novos resultados, resposta perdida aprovada/rejeitada e rollback após flush recuperados: [DominioRegistroTest](boleto-registro/src/test/java/br/com/caixa/cobranca/registro/DominioRegistroTest.java), [ADR-010](docs/adr/ADR-010-contrato-funcional-e-idempotencia-registradora.md).
4. Cache — _
   Evidência: (E3) Caffeine cache-aside; TTL variável por estado (30 s transitório, 10 min final), sem cachear 404; cache hit, fallback e 503. (E9) invalidação final antes do TTL, duplicata/ordem e corrida miss × evento com latches, sem reinstalar estado antigo após ack: [ConsultaEventosTest](boleto-consulta/src/test/java/br/com/caixa/cobranca/consulta/ConsultaEventosTest.java). Uma instância de consulta; p95/expiração sob carga ainda E14.
5. Resiliência — _
   Evidência: (E8) três tentativas de publicação e três entregas de processamento, mínimos 1 s/5 s; isolamento produtor, quarentena Artemis, ack após commit e transferência atômica de inválidos. Rejeição de negócio não entra em DLQ. [ADR-011](docs/adr/ADR-011-broker-jvm-artemis.md). Breaker e replay ainda E10/E12; não atribuir evidência RabbitMQ.
6. Testabilidade — _
   Evidência E6: `mvn -B clean verify`, **60 testes aprovados** (33 emissor, 15 registro, 11 consulta no Surefire e 1 no Failsafe). [Consumidor](boleto-consulta/src/test/java/br/com/caixa/cobranca/consulta/RegistroContractTest.java), [provedor HTTP/H2](boleto-registro/src/test/java/br/com/caixa/cobranca/registro/RegistroProviderTest.java), [pact canônico](shared-contracts/pacts/boleto-consulta-boleto-registro.json) e [comparação do artefato](boleto-consulta/src/test/java/br/com/caixa/cobranca/consulta/PactArtifactIT.java). Prova negativa detectada e restaurada: [E6](docs/etapas/E6-contratos-pact.md). Reprodução integrada permanece na E14.
   Atualização E7: **146 testes aprovados** no build limpo (87 emissor, 47 registro, 11 consulta Surefire + 1 Failsafe), com PACT atualizado. [Matriz e evidências E7](docs/etapas/E7-contrato-funcional.md).
   Atualização E8: **177 testes aprovados** em `clean verify` (12 broker, 93 emissor, 60 registro, 11 consulta Surefire + 1 Failsafe); broker real, reinício/contadores/quarentena, falhas entre transações e migração preservada. [Evidências E8](docs/etapas/E8-broker-jvm.md). Não é teste de carga nem aceite integrado final.
   Atualização E9: **214 testes aprovados** em `clean verify` (12 broker, 17 eventos-outbox, 96 emissor, 65 registro, 23 consulta Surefire + 1 Failsafe). Atomicidade, recuperação, assinantes independentes e invalidação concorrente: [E9](docs/etapas/E9-eventos-e-cache.md). PACT preservado; testes de eventos da consulta usam fontes HTTP simuladas, sem alegar nova prova de quatro JVMs.
7. Decisões arquiteturais — _  → ver `docs/adr/`
8. Uso crítico de IA — _
   Como usamos IA e o que validamos manualmente: (ver `docs/etapas/` — cada etapa registra o que foi gerado/validado)
9. Execução — _
   Como rodar: [README](README.md), quatro JARs/JVMs no perfil B; health e jornada de transporte/reinício verificados na [E8](docs/etapas/E8-broker-jvm.md). Reprodução por outro integrante e jornadas finais ainda E14.

## Opcionais entregues
**Não aplicável:** grupo confirmado com três integrantes, portanto nenhum opcional adicional é obrigatório conforme o brief. E13 registrada como não aplicável; não representa funcionalidade entregue. Permanecem os requisitos do tema e os critérios gerais da rubrica, incluindo uso crítico de IA.
