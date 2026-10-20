# Auto-avaliação — Central de Cobrança e Boletos
Grupo: **3 integrantes confirmados** — <nomes e papéis — PREENCHER>
Tema/domínio: Central que aceita emissões de boleto (202), registra o título numa registradora simulada via outbox + fila Artemis e permite consultar o estado com cache.
Perfil de execução: B (pura-JVM), **sem Docker na entrega**. E8/E9: comandos e eventos no mesmo broker JVM separado.

> Esqueleto criado na E0. Os níveis (0–3) e evidências são preenchidos conforme as etapas avançam.

## Situação da entrega

**Em desenvolvimento, sem autoavaliação final.** O [roteiro revisado](ROTEIRO.md) compara todas as aulas, o exemplo PIX e os critérios do [brief](brief-alunos.md), registra E0–E10 e planeja até E15. Fechamento apenas na E15, depois da validação integrada E14.

Prioridade revisada pelo solicitante em 20/10/2026: **CORE geral**, preservando E0–E10. E11 não implementada/fora da entrega atual; E12 reduzida a preparação operacional local, sem replay auditado ou retenção formal; E14/E15 continuam pendentes. **Requisitos da Sessão 5 não atendidos integralmente:** autorização por convênio, auditoria persistida, criptografia comprovada e retenção formal. Correlação/logs mínimos não substituem esses controles. Redução não é aprovação docente nem garantia de nota: [ADR-014](docs/adr/ADR-014-prioridade-core-e-limites-de-escopo.md).

Os pesos usados são os do brief: **15/15/12/10/12/13/13/5/5 = 100**. A divergência 12/12 no material da Aula 9 está registrada no roteiro para confirmação. Não atribuir nota por funcionalidade apenas planejada.

## Revisão de aderência e mínimo necessário — 20/10/2026

Fontes conferidas: [brief, §§2–4](brief-alunos.md), [guia da banca, §6](../../plano-de-aulas/aula-09-aluno.md), [estado final do exemplo PIX](../projeto-guiado-pix/gabarito/aula-08/ESTADO.md), requisitos do tema e implementação/testes atuais. O exemplo é referência, não uma lista de funcionalidades obrigatórias. E0–E15 são etapas internas de organização, **não quinze entregas exigidas pelo professor**.

**Conclusão:** os seis itens do CORE têm implementação e evidência. Isso não significa entrega final completa nem nível 3 automático: a autoavaliação, a reflexão humana sobre IA e a reprodução da versão final ainda precisam ser concluídas. Não foi identificada necessidade de nova funcionalidade para cobrir o CORE geral; permanecem as lacunas explícitas do tema.

### Critérios gerais: o que está atendido e o que falta comprovar

| # / peso | Situação na revisão | Evidência conferida / pendência real |
|---|---|---|
| 1 / 15 — Decomposição | Implementado, com evidência | Três serviços; somente emissor e registro têm H2, separados; consulta usa APIs/cache, não acessa tabelas alheias. [Arquitetura](docs/arquitetura.md), [corte e alternativas](docs/adr/ADR-003-corte-tres-servicos.md), configurações [emissor](boleto-emissor/src/main/resources/application.yml) e [registro](boleto-registro/src/main/resources/application.yml). Não é necessário criar banco para a consulta |
| 2 / 15 — Assíncrono | Implementado e testado | [Topologia durável](cobranca-broker/src/main/java/br/com/caixa/cobranca/broker/BrokerApplication.java), [consumidor](boleto-registro/src/main/java/br/com/caixa/cobranca/registro/RegistroCommandListener.java) e [prova entre processos E8](docs/etapas/E8-broker-jvm.md). At-least-once com limites de tentativas, sem promessa de exactly-once ou recuperação automática após isolamento |
| 3 / 12 — Idempotência/consistência | Implementado e testado | [Aceite + outboxes na mesma transação](boleto-emissor/src/main/java/br/com/caixa/cobranca/emissor/EmissaoPersistenceService.java), [consumidor idempotente](boleto-registro/src/main/java/br/com/caixa/cobranca/registro/RegistroBoletoService.java), [duplicata e falhas entre commits/ack](boleto-registro/src/test/java/br/com/caixa/cobranca/registro/RegistroEntregaJmsTest.java). SAGA não é obrigatória sem compensação aplicável |
| 4 / 10 — Cache | Implementado e testado | [Cache-aside/fallback](boleto-consulta/src/main/java/br/com/caixa/cobranca/consulta/ConsultaBoletoService.java), [TTL](boleto-consulta/src/main/java/br/com/caixa/cobranca/consulta/ConsultaCacheConfiguration.java), [invalidação e corrida](boleto-consulta/src/test/java/br/com/caixa/cobranca/consulta/ConsultaEventosTest.java). p95 da premissa do tema não medido; não é limiar numérico da rubrica geral |
| 5 / 12 — Resiliência | Implementado e testado | Retry/backoff/quarentena no broker, timeouts e [breaker por fonte](boleto-consulta/src/main/java/br/com/caixa/cobranca/consulta/RestBoletoFonteClient.java); [falhas temporárias/permanentes](boleto-registro/src/test/java/br/com/caixa/cobranca/registro/RegistroEntregaJmsTest.java) e [recuperação/timeout](boleto-consulta/src/test/java/br/com/caixa/cobranca/consulta/ResilienciaFonteTest.java). Replay administrativo não é item exigido pelo critério |
| 6 / 13 — Testabilidade | Build e contrato revalidados nesta revisão | `mvn -B clean verify`: **BUILD SUCCESS, 265 testes, zero falhas/erros/ignorados**, sem Docker, em 20/10/2026; contrato consumidor/provedor e comparação do pact no Failsafe. Isso não substitui reprodução humana da demo nem mede carga |
| 7 / 13 — Decisões | Documentadas com alternativas | ADRs de [corte](docs/adr/ADR-003-corte-tres-servicos.md), [outbox × SAGA](docs/adr/ADR-005-saga-e-outbox-no-mvp.md), [cache](docs/adr/ADR-006-cache-consulta.md), [broker](docs/adr/ADR-011-broker-jvm-artemis.md) e [redução de escopo](docs/adr/ADR-014-prioridade-core-e-limites-de-escopo.md). Não há quantidade mínima de ADRs nem obrigação de criar novas decisões |
| 8 / 5 — Uso crítico de IA | Evidência parcial; falta consolidação humana | Há registros por etapa, inclusive a [corrida encontrada na E10](docs/etapas/E10-resiliencia-e-correlacao.md). Consolidar o que a IA produziu, decisões do grupo e o que cada pessoa realmente conferiu. Testes executados pelo assistente não são validações manuais feitas pelos integrantes |
| 9 / 5 — Execução | Perfil/instruções e prova E8 existentes; final pendente | [README](README.md) e quatro JVMs na E8. Reproduzir a versão E10 com o roteiro da demo; build desta revisão não é nova execução dos quatro JARs. Reprodução por outro integrante é uma verificação interna recomendada, não um requisito textual separado da rubrica |

O CORE não se resume a “ter os seis itens”: os **nove critérios continuam avaliados**, mesmo com três integrantes e nenhum opcional adicional obrigatório. Em particular, resiliência e uso crítico de IA têm peso próprio. O brief usa 13/13 para critérios 6/7; o guia da banca usa 12/12 e, apesar de afirmar 100, sua tabela soma **98**. Manter os pesos do brief e confirmar a divergência com o docente; não calcular nota garantida.

### O que foi além do mínimo

| Item | Classificação correta | Encaminhamento para evitar trabalho desnecessário |
|---|---|---|
| Três serviços em vez de dois | Acima do mínimo numérico; corte justificado na ADR-003 | Manter. Fundir serviços agora seria retrabalho sem ganho de requisito |
| Três eventos, pub/sub durável e outbox de eventos | A rubrica exige um fluxo por fila, não três eventos; porém a Sessão 4/US-09 pede esses fatos e eles sustentam a invalidação existente | Manter E9, sem acrescentar Kafka, BI/antifraude real ou serviço de notificação |
| Ledger da registradora simulada e histórico de transições | Solução mais robusta que a demonstração mínima de duplicata; ligada a idempotência/consistência e aos requisitos do tema | Manter, sem integrar registradora bancária real |
| Correlação ponta a ponta, header validado e módulo HTTP comum | A observabilidade aparece como opcional, mas `correlation_id` também é pedido no tema | Já entregue; não ampliar para dashboards, tracing distribuído ou plataforma de observabilidade |
| Outboxes reutilizáveis, sincronização cache/evento, testes de falhas e reinício | Profundidade de engenharia, não novos requisitos independentes ou novos serviços de negócio | Não excluir proteções/testes para reduzir contagem de arquivos; não há meta de 265 testes |
| Circuit breaker, retry, DLQ e timeouts | **Não classificar simplesmente como excesso:** estão citados no critério 5, mesmo com “resiliência avançada” na lista de opcionais | Manter E8/E10; não adicionar `@RetryableTopic`, Kafka ou outro mecanismo de retry |
| Cópia/restauração completa, carga/p95, 500 emissões/s e proporção 20:1 | Planejamento operacional interno/premissas do tema, não mínimos expressos no CORE/rubrica | Não apresentar como exigência geral do professor. No roteiro atual são verificações planejadas; eventual redução desses portões deve ser explícita, sem alegar que foram validados |
| Segurança, auditoria persistida, retenção e replay restrito | Não têm critério separado na rubrica geral, mas são requisitos do tema, não funcionalidades inventadas | Continuam não atendidos/fora da entrega atual pela ADR-014, sem aprovação docente presumida |

**Caminho mínimo recomendado para concluir a entrega:** não desenvolver novas funcionalidades; conferir a demonstração integrada da versão atual (emissão, registro/rejeição, consulta/cache, repetição/conflito e falha técnica/quarentena), corrigir apenas bloqueios encontrados e finalizar os documentos obrigatórios, nomes/papéis, reflexão de IA, níveis justificados e defesa. O README já contém início/parada/portas: complementar apenas o que faltar para reproduzir. Este diagnóstico não remove código, não marca E12/E14/E15 como concluídas e não altera silenciosamente os portões já registrados.

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
   Evidência: (E8) três tentativas de publicação e três entregas de processamento, mínimos 1 s/5 s; isolamento produtor, quarentena Artemis, ack após commit e transferência atômica de inválidos. Rejeição de negócio não entra em DLQ. [ADR-011](docs/adr/ADR-011-broker-jvm-artemis.md). (E10) consulta com conexão 2 s/leitura 3 s, circuitos por fonte, limiar/OPEN 10 s/sonda única e recuperação com clock controlado, sem retry extra ou fallback técnico: [ResilienciaFonteTest](boleto-consulta/src/test/java/br/com/caixa/cobranca/consulta/ResilienciaFonteTest.java), [ADR-013](docs/adr/ADR-013-resiliencia-e-correlacao.md). Replay auditado não entregue/fora do escopo atual; não atribuir evidência RabbitMQ ou timeout de registradora HTTP real.
6. Testabilidade — _
   Evidência E6: `mvn -B clean verify`, **60 testes aprovados** (33 emissor, 15 registro, 11 consulta no Surefire e 1 no Failsafe). [Consumidor](boleto-consulta/src/test/java/br/com/caixa/cobranca/consulta/RegistroContractTest.java), [provedor HTTP/H2](boleto-registro/src/test/java/br/com/caixa/cobranca/registro/RegistroProviderTest.java), [pact canônico](shared-contracts/pacts/boleto-consulta-boleto-registro.json) e [comparação do artefato](boleto-consulta/src/test/java/br/com/caixa/cobranca/consulta/PactArtifactIT.java). Prova negativa detectada e restaurada: [E6](docs/etapas/E6-contratos-pact.md). Reprodução integrada permanece na E14.
   Atualização E7: **146 testes aprovados** no build limpo (87 emissor, 47 registro, 11 consulta Surefire + 1 Failsafe), com PACT atualizado. [Matriz e evidências E7](docs/etapas/E7-contrato-funcional.md).
   Atualização E8: **177 testes aprovados** em `clean verify` (12 broker, 93 emissor, 60 registro, 11 consulta Surefire + 1 Failsafe); broker real, reinício/contadores/quarentena, falhas entre transações e migração preservada. [Evidências E8](docs/etapas/E8-broker-jvm.md). Não é teste de carga nem aceite integrado final.
   Atualização E9: **214 testes aprovados** em `clean verify` (12 broker, 17 eventos-outbox, 96 emissor, 65 registro, 23 consulta Surefire + 1 Failsafe). Atomicidade, recuperação, assinantes independentes e invalidação concorrente: [E9](docs/etapas/E9-eventos-e-cache.md). PACT preservado; testes de eventos da consulta usam fontes HTTP simuladas, sem alegar nova prova de quatro JVMs.
   Atualização E10: **265 testes aprovados** em `clean verify` (19 infraestrutura-http, 12 broker, 17 eventos-outbox, 97 emissor, 69 registro, 50 consulta Surefire + 1 Failsafe). Circuito/clock, timeout e redirect em HTTP local real, correlação em MVC/JPA/JMS/cache e compatibilidade idempotente com legados: [E10](docs/etapas/E10-resiliencia-e-correlacao.md). Sem falhas/erros/ignorados, PACT preservado; não é teste de carga nem nova demonstração de quatro JVMs.
7. Decisões arquiteturais — _  → ver `docs/adr/`
8. Uso crítico de IA — _
   Como usamos IA e o que validamos manualmente: (ver `docs/etapas/` — cada etapa registra o que foi gerado/validado)
9. Execução — _
   Como rodar: [README](README.md), quatro JARs/JVMs no perfil B; health e jornada de transporte/reinício verificados na [E8](docs/etapas/E8-broker-jvm.md). Reprodução por outro integrante e jornadas finais ainda E14.

## Opcionais entregues
**Não aplicável:** grupo confirmado com três integrantes, portanto nenhum opcional adicional é obrigatório conforme o brief. E13 não representa funcionalidade entregue. Os nove critérios gerais continuam na autoavaliação; a Sessão 5 permanece na especificação, mas seus requisitos não foram integralmente atendidos por decisão explícita de escopo, não por dispensa automática de opcionais. Não atribuir pontos por controles ausentes.
