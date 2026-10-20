# Roteiro do projeto final — Central de Cobrança e Boletos

> Revisado após a conclusão das aulas.
> Objetivo: concluir o trabalho final, comparando a implementação com **todas as aulas**, a rubrica e os requisitos dos boletos — não apenas chegar à Aula 6.
> **O fechamento será a E15 e só começa depois das entregas técnicas e da validação integrada.**
> Banca prevista no brief: **26/10**. A sequência abaixo é por dependências, não uma promessa de prazo.

## Decisões confirmadas pelo grupo — 20/10/2026

- **Sem Docker durante toda a entrega:** perfil B, com aplicações e broker executados em JVM.
- **Comandos por fila em broker JVM:** E8 substituiu o transporte HTTP provisório por Artemis, preservando outbox e consumidor idempotente.
- **Eventos pelo mesmo broker:** a E9 usa publicação/assinatura, com outbox de eventos e assinaturas independentes.
- **Três integrantes:** nenhum opcional adicional é obrigatório. E13 fica **não aplicável**, preservando a numeração E14/E15.
- **Broker validado na E8:** Apache ActiveMQ Artemis 2.33.0, gerenciado pelo Spring Boot 3.3.5, Jakarta JMS/Java 17 e journal NIO. Escolha/alternativas: [ADR-011](docs/adr/ADR-011-broker-jvm-artemis.md).

Comandos migraram na E8; eventos e invalidação foram entregues na E9. Não há troca silenciosa para HTTP se o broker falhar.

## 1. Fontes e regras deste roteiro

1. [Brief do trabalho final](brief-alunos.md): CORE, opcionais por integrante, estrutura da entrega e rubrica.
2. [Requisitos do tema](requisitos/README.md): [user stories](requisitos/user-stories.md) e sessões de elicitação. Em divergência interna, **a sessão prevalece**, como definido no pacote.
3. [Gabarito final PIX](../projeto-guiado-pix/gabarito/aula-08/ESTADO.md) e incrementos anteriores: referência de padrões, não especificação para copiar.
4. [Planos de aula](../../plano-de-aulas/): teoria, contratos, resiliência e defesa de arquitetura.
5. [ADRs](docs/adr/), código e [validações registradas](docs/validacao-pendente.md): o que efetivamente decidimos e comprovamos.

**Distinção importante:** terminar as aulas não significa ter implementado tudo no projeto. O gabarito também é didático: compilar, ter uma anotação ou conter um mock não prova que o fluxo completo funciona.

### Obrigatório, opcional e restrição de ambiente

- **CORE geral:** pelo menos dois serviços com bases segregadas; fluxo assíncrono com fila e consumidor idempotente; cache com estratégia/invalidação; contrato executável; ADRs/README; execução comprovada em um perfil.
- **Escopo do tema:** as sessões também pedem eventos/outbox do resultado, proteção de acesso, auditoria, retenção formal e reprocessamento restrito. Esses itens não podem continuar escondidos como “opcionais” sem revisão explícita de escopo.
- **Opcionais da rubrica:** um item por integrante acima de três. O grupo tem **três integrantes**, portanto não precisa entregar opcionais adicionais; E13 não se aplica.
- **Sem Docker não significa sem entrega.** E8/E9 usam **broker JVM real**, com persistência, fila, quarentena e pub/sub durável. HTTP E4/E5 fica como histórico, não como equivalente a fila ou fallback.
- Kafka e múltiplos consumer groups não fazem parte da entrega escolhida. **Publicar os eventos acordados na Sessão 4 continua obrigatório no escopo do tema**, usando o broker JVM e sem exigir semântica de log/replay Kafka.
- Pagamento, liquidação, baixa, cancelamento e envio real de e-mail/SMS/push continuam fora do MVP. Não criar funcionalidades novas apenas porque existem no exemplo PIX.

## 2. Onde estamos

| Marco preservado | Entrega comprovada | Limite que permanece |
|---|---|---|
| E0 — Fundação (`etapa-0`) | Java 17/Spring Boot, Maven, três serviços e bases segregadas | Os perfis Maven declarados ainda não representam três ambientes completos implementados |
| E1 — DDD/emissão (`etapa-1`) | Contextos, decisões e `POST /boletos` validado | Matriz e fronteira de registro completadas na E7; agregado/VOs foram definidos conceitualmente |
| E2 — Idempotência (`etapa-2`) | Aceite persistido; mesma chave/conteúdo repete resposta; conflito 409 | Histórico de novos resultados completado na E7; auditoria completa continua E11 |
| E3 — Consulta/cache (`etapa-3`) | Cache-aside Caffeine, TTL por estado, fallback C e 400/404/503 distintos | TTL provisório; complementado por invalidação na E9 |
| E4 — Assíncrono adaptado (`etapa-4`) | Outbox transacional de comando e consumidor HTTP idempotente | Transporte substituído na E8; outbox de eventos adicionada na E9 |
| E5 — Resiliência HTTP (`etapa-5`) | Três tentativas totais, backoff 1 s/5 s, timeouts do produtor, isolamento e `REJEITADO` | DLQ de broker E8 e breaker HTTP E10; replay continua E12 |
| E6 — Contratos executáveis | PACT consulta ↔ registro: três interações, provedor HTTP/H2 e comparação automática do gerado com o canônico | Atualizado na E7 com dados não pagáveis; autorização ainda E11 |
| E7 — Contrato funcional | Matriz validada nas duas fronteiras, novos dados não pagáveis/histórico e ledger idempotente da registradora simulada | Finais antigos preservados sem inventar dados retroativos; registradora real fora do MVP |
| E8 — Broker JVM | Artemis em processo próprio, fila/quarentena duráveis, confirmação, retry, migração e recuperação após reinício | Eventos complementados na E9; autenticação/TLS E11 e replay auditado E12 |
| E9 — Eventos e cache | Três eventos mínimos, outbox local nos produtores, pub/sub durável e invalidação coordenada com leitura | Uma instância por serviço; correlação complementada na E10, proteção E11 e recuperação E12 |
| E10 — Resiliência e correlação | Timeouts e circuitos independentes nas fontes HTTP; correlação validada em HTTP, comando/outbox/JMS/eventos e consulta | Limites de socket, não SLA global; registradora local; acesso/auditoria E11 e operação E12 |

**Última validação registrada:** `mvn -B clean verify` com **265 testes**, sem falhas/erros/ignorados (19 infraestrutura-http, 12 broker, 17 eventos-outbox, 97 emissor, 69 registro, 50 consulta Surefire e 1 Failsafe). [E10](docs/etapas/E10-resiliencia-e-correlacao.md) cobre circuitos com clock controlado, timeout HTTP real e correlação nas fronteiras persistentes. A [E8](docs/etapas/E8-broker-jvm.md) comprova quatro JVMs para comandos/reinício; os testes posteriores não são nova prova de quatro processos. Não é carga nem substitui E14. Contagens anteriores permanecem no histórico de cada etapa.

Os marcos e tags E0–E5 permanecem intactos. A antiga **“E6 — Fechamento da base” foi substituída**: E6 passa a ser contratos, e o fechamento foi movido para E15.

**Controle de versões:** E6–E9 foram consolidadas no commit `529bd5d`, preservando a versão validada. A partir da E10, cada etapa deve ter seu próprio commit após implementação/testes/documentação, antes de começar a próxima. Informar hash e resumo ao concluir; não fazer push nem reverter/descartar alterações sem solicitação do usuário.

## 3. De/para: aulas e projeto PIX → boletos

As pastas do gabarito não seguem a mesma numeração dos planos de aula:

| Conteúdo do curso | Referência concreta do PIX | Equivalente nos boletos | Situação / próxima entrega |
|---|---|---|---|
| Aula 1 — DDD | [Estado aula-01](../projeto-guiado-pix/gabarito/aula-01/ESTADO.md), emissor/gravador/consulta | Emissão, registro e consulta com bases próprias | E7: matriz funcional, fronteiras e histórico de novos resultados entregues |
| Aula 2 — Consistência/SAGA | [Estado aula-02](../projeto-guiado-pix/gabarito/aula-02/ESTADO.md): chamada síncrona e compensação | Aceite + outbox local e consumidor idempotente | Adaptação consciente na ADR-005; não adicionar SAGA sem operação de negócio que exija compensação |
| Aula 3 — Comunicação | [Plano aula-03](../../plano-de-aulas/aula-03-aluno.md): comando × evento | `RegistrarBoletoCommand` × fatos `EmissaoAceita`, `BoletoRegistrado`, `RegistroRejeitado` | Comando E8 e eventos E9 entregues pelo mesmo broker, com topologias distintas |
| Aula 4 — Cache | [Estado aula-03](../projeto-guiado-pix/gabarito/aula-03/ESTADO.md): cache, fonte e ausência | Caffeine + registro → fallback emissor | E9 invalida estado final e cobre corrida com leitura; expiração/carga integradas E14 |
| Aula 5 — Assíncrono | [Estado aula-04](../projeto-guiado-pix/gabarito/aula-04/ESTADO.md), publisher/listener | Outbox de comando + consumidor de registro | E8: fila Artemis durável substituiu HTTP provisório |
| Aula 6 — RabbitMQ | [Estado aula-05](../projeto-guiado-pix/gabarito/aula-05/ESTADO.md), exchange/binding, ack, retry/DLQ | Entrega confirmada, retry, quarentena e reentrega | E8: padrões validados na topologia Artemis, sem exigir RabbitMQ |
| Aula 7 — Eventos/Kafka | [Estado aula-06](../projeto-guiado-pix/gabarito/aula-06/ESTADO.md), evento e notificação | Eventos mínimos após aceite e resultado; assinantes independentes | E9 entregue pelo broker JVM; Kafka/múltiplos grupos fora do escopo escolhido |
| Aula 8 — Resiliência | [Estado aula-07](../projeto-guiado-pix/gabarito/aula-07/ESTADO.md), `@RetryableTopic` e breaker | Proteção das dependências remotas; falha não vira sucesso | E10 entregue: limites HTTP, circuitos independentes, correlação e recuperação de circuito; replay operacional E12 |
| Aula 8 — PACT | [Estado aula-08](../projeto-guiado-pix/gabarito/aula-08/ESTADO.md), consumidor consulta e provedor gravador | Consumidor consulta e provedor registro | E6 entregue: geração, verificação e proteção contra artefato desatualizado |
| Aula 9 — Defesa | [Guia da banca](../../plano-de-aulas/aula-09-aluno.md) | Demo, autoavaliação, decisões e uso crítico de IA | Apenas depois dos testes de aceite — E15 |

### O que não copiar mecanicamente

- No PIX, o comprovante é essencialmente imutável; nos boletos, o estado transitório muda. Cache precisa cobrir essa transição.
- O gabarito final verifica **consulta ↔ gravador**, apesar de o brief do exemplo mencionar emissor ↔ gravador. Usaremos o par HTTP que existe de fato: **consulta ↔ registro**.
- DTO compartilhado não substitui PACT. O cliente real precisa gerar o contrato e o provedor real precisa verificá-lo.
- O exemplo de gateway tem fallback que apenas registra log. Na nossa implementação, uma falha que ainda exige retry não poderá ser absorvida como sucesso.
- Não recriar a SAGA síncrona que o próprio exemplo depois substitui por mensageria.
- `@RetryableTopic` depende do caminho Kafka. Implementar retry/quarentena no broker JVM escolhido, sem alegar uso de Kafka ou dessa anotação.
- Não trocar Java 17 por 21, Caffeine por Redis ou acrescentar um serviço de notificação sem necessidade do domínio/entrega.

## 4. De/para: critérios da avaliação → evidência e lacuna

Pesos abaixo seguem o **brief do trabalho final**, somando 100. O material da Aula 9 traz 12 para os critérios 6 e 7, divergindo dos 13/13 do brief; este roteiro usa o brief e registra a diferença para confirmação com o docente, sem inventar uma nota.

| # | Critério / peso | Evidência já existente | O que falta antes do fechamento | Etapas |
|---|---|---|---|---|
| 1 | Decomposição — 15 | Três módulos, H2 segregado, ADR-002/003; E7 matriz/histórico e validação estrutural compartilhada | Provar jornadas completas e limites do perfil | E14 |
| 2 | Assíncrono — 15 | E8: fila/JMS e prova entre processos; E9: fatos mínimos em pub/sub durável, dois assinantes independentes de teste | Aceite integrado final | E14 |
| 3 | Idempotência/consistência — 12 | Chaves, fingerprint, ledger E7; E9: intenção atômica de eventos, rollback e reentrega por mesmo id | Replay operacional e recuperação integrada | E12, E14 |
| 4 | Cache — 10 | TTL, fallback, não cachear 404/erro; E9 invalidação final e corrida de preenchimento testada | Expiração/carga real e isolamento de acesso em cache hit | E11, E14 |
| 5 | Resiliência — 12 | E8: retry/backoff/isolamento/quarentena; E10: limites HTTP, breaker independente e recuperação testada sem retry aninhado | Recuperação operacional e jornadas integradas | E12, E14 |
| 6 | Testabilidade — 13 | E10: PACT preservado, 265 testes sem Docker; clock controlado, HTTP local real, correlação com Artemis/H2 | Manter contratos nas próximas mudanças; jornadas integradas | E11, E14 |
| 7 | ADRs — 13 | ADR-001…013 com alternativas e decisões de cache/resiliência/correlação | Atualizar decisões novas e divergências com requisitos | E11–E14, consolidar E15 |
| 8 | Uso crítico de IA — 5 | Histórico por etapas e commits | Registrar contribuição humana, falhas encontradas, decisões revistas e validações reais | Durante etapas; consolidar E15 |
| 9 | Execução comprovada — 5 | Execução E0–E4 e testes E5 registrados | Reprodução por outro integrante, artefatos finais, demo e instruções sem dependências implícitas | E14, E15 |

Não atribuir nível 3 automaticamente por existir código. A autoavaliação precisa apontar prova executável e as limitações do perfil escolhido.

## 5. Lacunas específicas dos requisitos de boletos

| História / fonte | Estado observado | Ação planejada |
|---|---|---|
| US-01/02 — emissão e idempotência | E7: matriz/validador comum; E8: 202/aceite/outbox preservados e regressão verde no transporte JMS | Manter nas próximas mudanças e jornadas E14 |
| US-03/04 — registro e consulta | E7: novos aprovados com marcadores não pagáveis; histórico local atômico; PACT/projeção pública atualizados | Finais antigos preservados sem fabricar histórico; autorização E11 e jornadas E14 |
| US-05 — cache | E9: evento final invalida; corrida de preenchimento coberta, TTL preservado | Acesso inclusive cache hit E11; p95/expiração e jornada E14 |
| US-06/07 — retry/quarentena | E8: três publicações/entregas por fronteira, 1 s/5 s e quarentena Artemis validados; substitui premissa 1/2/4 | Recuperação restrita/auditada E12, sem reescrever histórico das sessões |
| US-08 — idempotência do consumidor | E7: ledger simulado por UUID em transação independente; perda de resposta/rollback recuperados sem duplicar efeito ou histórico | Não promete idempotência de banco externo real; replay E12 e recuperação integrada E14 |
| US-09 / Sessão 4 — eventos | E9: três eventos mínimos/intenções/assinaturas; E10: correlação HTTP validada até eventos e invalidação | Acesso/recuperação E11/E12; jornada E14 |
| US-10 / Sessão 5 — auditoria | E10: correlação propagada e logs mínimos; não é trilha de auditoria completa | Auditoria de emissão/consulta/transição/replay E11/E12 |
| US-11 / Sessão 5 — proteção | Endpoints internos sem autenticação; cache e base sem isolamento de convênio implementado | Autorização, proteção entre serviços, minimização, gestão de segredos e proteção de armazenamento — E11 |
| US-12 / Sessão 5 — retenção | Hipótese de cinco anos apenas documentada | Formalizar marco, escopo por canal, acesso e validações necessárias — E12; expurgo automático continua fora do MVP |

**Não confundir regra didática com norma institucional.** Metas de carga/latência, retenção e criptografia real exigem validação; não declarar conformidade de produção só por implementar o perfil de demonstração.

## 6. Nova sequência de execução

| Etapa | Entrega | Pré-requisitos reais | Estado |
|---|---|---|---|
| E6 | Contratos executáveis (PACT) | Base E5 | **Concluída — 60 testes no build limpo, incluindo PACT e comparação** |
| E7 | Completar regras e aceite do domínio | Base E5; atualizar contratos E6 se o formato mudar | **Concluída — matriz, simulação/histórico e 146 testes no build limpo** |
| E8 | Broker JVM, fila de comandos e quarentena | Base E7; selecionar e validar tecnologia compatível com o ambiente | **Concluída — Artemis, 177 testes e prova em quatro JVMs** |
| E9 | Eventos confiáveis e invalidação de cache | E7 e E8; adaptar contrato E6 se necessário | Pendente |
| E10 | Resiliência das dependências e correlação | E8; E9 para cobrir também eventos | Pendente |
| E11 | Acesso, privacidade e auditoria | E7 e E10; validar efeitos em contratos E6 | Pendente |
| E12 | Recuperação operacional e retenção | E8, E9 e E11 | Pendente |
| E13 | Opcionais do grupo | Grupo confirmado com três integrantes | **Não aplicável — nenhum opcional obrigatório** |
| E14 | Aceite integrado e reprodução da entrega | E6–E12; E13 já registrada como não aplicável | Pendente |
| E15 | **Fechamento e preparação da banca** | E14 aprovada e portão de fechamento atendido | **Bloqueada pelas entregas anteriores** |

A numeração organiza a conversa; atividades independentes podem ocorrer antes. Tamanho do grupo, broker e destino de comandos/eventos já foram decididos. E6 foi validada sem broker; E8 adicionou a infraestrutura real. Não criar tags de etapa concluída enquanto sua prova de aceite estiver pendente.

### E6 — Contratos executáveis (CORE, concluída)

**Referência:** [PACT do exemplo](../projeto-guiado-pix/gabarito/aula-08/ESTADO.md), [teoria](../../plano-de-aulas/aula-08-contract-aluno.md). Critérios 6 e 7.

- [x] Consumidor `boleto-consulta` testa seu cliente REST real contra o mock PACT do `boleto-registro`.
- [x] Pact cobre `REGISTRADO`, `REJEITADO`, ausência 404 e campos snake_case; especifica campos preenchidos/nulos do contrato atual por estado.
- [x] Provedor verifica o mesmo pact contra o controller real com H2 e estados preparados, sem exigir broker externo.
- [x] Artefato canônico único em `shared-contracts/pacts`, copiado somente para recursos de teste; `PactArtifactIT` impede divergência do gerado no consumidor.
- [x] Geração e verificação passaram em build limpo, sem depender da ordem entre registro/consulta nem de arquivo sobrando em `target`.
- [x] Quebra controlada de 404 para 200 detectada no provedor e na comparação; contrato restaurado e build verde.
- [x] [ADR-009](docs/adr/ADR-009-contrato-consulta-registro.md) e [execução/evidências E6](docs/etapas/E6-contratos-pact.md). Segundo par não é obrigatório para o grupo de três.

**Aceite atendido:** build limpo gera/valida contrato, passa sem Docker e detecta incompatibilidade real. Atualização dos dados simulados feita na E7; manter contrato ao adicionar autorização na E11.

### E7 — Completar o contrato funcional do domínio (concluída)

**Fontes:** US-01…04/08, Sessões 2–4. Critérios 1 e 3.

- [x] [Matriz de validação e evidências](docs/etapas/E7-contrato-funcional.md): obrigatórios/condicionais, formatos e limites; sem inventar regras não acordadas.
- [x] Novos aprovados persistem `NAO-PAGAVEL-SIM-LINHA-{uuid}` e `NAO-PAGAVEL-SIM-BARRAS-{uuid}`, sem conexão ou formato bancário pagável.
- [x] Persistência, projeção pública e PACT consumidor/canônico/provedor atualizados juntos; rejeição/transitório sem dados de pagamento.
- [x] Aceite original no emissor; novos resultados no registro com histórico de origem/transição final na mesma transação, sem duplicar na reentrega.
- [x] Mesmo validador estrutural nas duas fronteiras; envelope consistente e UUID v4 antes de chamar a registradora.
- [x] Ledger durável por UUID em transação independente na simulação: perda de resposta aprovada/rejeitada e rollback local recuperados, sem prometer atomicidade distribuída.
- [x] Glossário/user stories/ADRs harmonizados: retry 1 s/5 s, dados didáticos e 202 após commit local de aceite/outbox. [ADR-010](docs/adr/ADR-010-contrato-funcional-e-idempotencia-registradora.md).

**Aceite atendido para novos processamentos E7:** estados finais completos no contrato simulado, histórico e idempotência testados; PACT verde em build limpo com 146 testes. Registros finais antigos são preservados sem reprocessamento nem histórico inventado; auditoria completa e recuperação operacional continuam E11/E12.

### E8 — Broker JVM, fila de comandos e quarentena (concluída)

**Referência:** [RabbitMQ do exemplo](../projeto-guiado-pix/gabarito/aula-05/ESTADO.md). Critérios 2 e 5.

- [x] Artemis 2.33.0/Jakarta JMS resolvido no ambiente; Java 17/Boot 3.3, persistência, confirmação, quarentena e duas assinaturas duráveis validados. [ADR-011](docs/adr/ADR-011-broker-jvm-artemis.md) registra escolha e alternativas.
- [x] Broker em JVM própria/loopback, separado dos três serviços; início/parada, porta, dados e prontidão documentados na [E8](docs/etapas/E8-broker-jvm.md).
- [x] Fila ANYCAST durável, mensagens persistentes, consumidor transacional e ack JMS após commit JPA; quarentena durável para erro permanente/esgotamento.
- [x] Aceite + outbox preservados. `entregue_em` só após commit JMS; fila ausente falha e ausência de binding tem encaminhamento à quarentena.
- [x] Um único publisher JMS ativo; adaptador HTTP sem bean e endpoint de comando desativado por padrão. Pendentes preservam UUID/contadores/backoff; isolados/entregues antigos não são reiniciados.
- [x] JSON/envelope/conflito e corpo não textual vão para quarentena; temporários têm redelivery; comandos bons continuam. Transferência à quarentena e ack original atômicos.
- [x] Três tentativas de publicação na outbox e três entregas no consumidor; mínimos 1 s/5 s. Fronteiras separadas, sem retry de negócio no produtor.
- [x] Perfil B executável com JARs; perfis A/C explicitamente conceituais, sem Docker nem fallback automático.
- [x] Testes de broker/reinício, indisponibilidade, confirmação perdida e falha após commit antes de ack; regressão/Pact e prova entre processos aprovados.

**Aceite atendido:** fluxo por fila real sem Docker, persistência/reentrega/quarentena comprovadas, 177 testes verdes e execução em quatro JVMs com reinício do broker. HTTP fica como histórico, não equivalente a fila. Eventos e invalidação são a próxima E9; E14/E15 continuam pendentes.

### E9 — Eventos de domínio confiáveis e invalidação de cache

**Referência:** [Eventos do exemplo](../projeto-guiado-pix/gabarito/aula-06/ESTADO.md), Sessões 3/4, US-05/09. Critérios 2, 3 e 4.

- [x] Contratos `EmissaoAceita`, `BoletoRegistrado`, `RegistroRejeitado` com os sete campos mínimos validados; sem nome/documento/endereço/valor.
- [x] Aceite + comando + intenção no emissor; resultado + histórico + intenção final no registro, em suas respectivas transações. Falha após flush reverte estado/intenção; ledger externo simulado permanece independente.
- [x] Publicador separado, confirmação JMS antes de marcar publicado, três tentativas/1 s/5 s e isolamento persistido. Confirmação local perdida repete o mesmo id, tolerado pelo consumidor.
- [x] MULTICAST no mesmo broker, assinatura durável da consulta e segundo assinante de teste offline; independência e reinício comprovados sem notificação real.
- [x] Consulta invalida após evento final e mantém TTL; não projeta dados incompletos nem rebaixa por evento fora de ordem.
- [x] Duplicata, falha de publicação, erro antes do ack e corrida determinística “miss em andamento × evento” testados; o preenchimento em voo é removido antes do ack.
- [x] Restrição: uma instância de cada serviço. Múltiplas consultas com cache local exigem assinaturas exclusivas por instância; mesma fila não difunde invalidação.
- [x] ADR-006 complementada pela ADR-012; execução, migração e limites na [E9](docs/etapas/E9-eventos-e-cache.md). Sem notificação efetiva, baixa ou liquidação.

**Aceite atendido:** intenção atômica ao estado, publicação independente com recuperação/isolamento, assinante independente e invalidação final coordenada com leitura. Eventos legados não são inventados. Correlação HTTP complementada na E10; proteção E11, recuperação E12 e aceite integrado E14 continuam pendentes.

### E10 — Resiliência das dependências e correlação ponta a ponta

**Referência:** [Resiliência do exemplo](../projeto-guiado-pix/gabarito/aula-07/ESTADO.md), Aula 8. Critério 5; base para observabilidade.

- [x] Inventário na [E10](docs/etapas/E10-resiliencia-e-correlacao.md): HTTP da consulta 2 s conexão/3 s leitura, JMS preservado, adaptador HTTP antigo inativo 2 s/5 s; registradora local não simula timeout remoto.
- [x] Circuitos Resilience4j independentes por fonte: últimas três chamadas/minimo três/100% de falhas; OPEN 10 s; única sonda HALF_OPEN; sucesso fecha, falha reabre.
- [x] Circuito aberto/falha técnica -> 503, sem fallback indevido/sucesso. 404 e rejeição 200 são disponibilidade, inclusive para recuperar a sonda.
- [x] Sem retry HTTP de aplicação ou redirect; uma chamada por fonte, segunda só após 404. Outboxes/redelivery preservam três tentativas/entregas e 1 s/5 s por fronteira; sem Kafka/`@RetryableTopic`.
- [x] Header UUID v4 validado/gerado nos três serviços; preservado em respostas/erros/cache hit, comando/outbox/JMS, eventos e chamadas HTTP internas. Contexto limpo entre threads/pedidos; metadado excluído da identidade de negócio, com legados cobertos.
- [x] Testes de falhas exatas, clock controlado/limiar/espera/sonda/recuperação, ausência de chamada OPEN, timeout/redirect HTTP reais e correlação nas fronteiras JMS/JPA. `clean verify`: 265 testes e PACT aprovados.

**Aceite atendido:** limites de socket explícitos e rastreabilidade nas fronteiras, sem sucesso falso nem retry adicional de aplicação. Não é deadline global ou registradora HTTP real. Evidências, migração e alternativas na [E10](docs/etapas/E10-resiliencia-e-correlacao.md) / [ADR-013](docs/adr/ADR-013-resiliencia-e-correlacao.md). Próxima E11; E14/E15 continuam pendentes.

### E11 — Controle de acesso, privacidade e auditoria

**Fontes:** Sessão 5 e US-10/11. Requisito do tema, não apenas melhoria estética de logs.

- [ ] Definir identidade e autorização do perfil didático: emissor só acessa seus convênios, atendimento consulta conforme permissão, operação inspeciona/reprocessa falhas.
- [ ] Proteger também os endpoints internos e as comunicações entre serviços; segredos fora do Git e distinção entre identidade do serviço e usuário solicitante.
- [ ] Autorizar antes de responder a partir do cache; cache hit nunca permite acessar boleto de outro convênio.
- [ ] Auditoria persistida para emissão, consulta, transição e operação, com ator, ação, boleto, instante, resultado e correlação. Não usar log livre como substituto automático de trilha.
- [ ] Minimização/mascaramento em logs, erros, eventos e falhas; payload completo apenas onde indispensável e com acesso restrito.
- [ ] Definir proteção de transporte/armazenamento e gestão de chaves aplicáveis ao perfil de demonstração; não declarar conformidade institucional sem validação.
- [ ] Testes de acesso permitido/negado, isolamento por convênio, cache e auditoria, mantendo contratos executáveis.

**Pronto quando:** permissões e trilha são demonstráveis sem expor dados pessoais, e as limitações de produção estão declaradas.

### E12 — Recuperação operacional e retenção

**Fontes:** Sessões 4/5, US-07/10/12.

- [ ] Fluxo restrito e auditado para analisar/reprocessar comandos isolados, depois de corrigir a causa e verificar se o registro já ocorreu.
- [ ] Reprocessamento preserva UUID/idempotência, não cria outro boleto e registra ator, motivo e resultado; impedir disparos concorrentes/indiscriminados.
- [ ] Rejeição de negócio permanece resultado final; replay técnico não converte `REJEITADO` em aprovação.
- [ ] Provar recuperação após reinício, indisponibilidade prolongada, resposta perdida e falha entre commit e confirmação.
- [ ] Formalizar retenção por base/outbox/quarentena/auditoria/cache/eventos e o marco de encerramento. Cinco anos é hipótese a validar, não regra legal confirmada.
- [ ] Definir procedimento de backup/restauração do H2 local e ambiente de demo sem apagar dados para contornar erro.
- [ ] Incluir armazenamento do broker, assinaturas duráveis e quarentena no procedimento de parada/recuperação, sem apagar o journal para contornar mensagens pendentes.
- [ ] Atualizar runbook de falhas e regras divergentes do glossário.

**Pronto quando:** operação consegue recuperar uma entrega com autorização e trilha, sem duplicar efeitos. Expurgo automático e operação bancária real continuam fora do MVP.

### E13 — Opcionais: não aplicável

- [x] Grupo confirmado com **três integrantes**; o brief não exige opcional adicional.
- [x] Nenhum opcional selecionado como obrigação. Kafka com múltiplos grupos, segundo par PACT e SAGA adicional não entram no planejamento.

E13 não gera implementação nem tag de funcionalidade. A numeração foi preservada para não deslocar E14/E15. Eventos, correlação, resiliência e auditoria previstos no tema/rubrica continuam nas respectivas etapas; não foram removidos com a dispensa de opcionais. Nomes e papéis dos integrantes ainda devem ser preenchidos na autoavaliação.

### E14 — Aceite integrado, desempenho e reprodução

- [ ] `mvn clean verify` inclui unitários, integração e PACT no perfil sem Docker, em execução limpa.
- [ ] Testes de mensageria usam broker JVM local com dados/portas isolados, sem Testcontainers ou broker previamente iniciado; encerrar recursos após os testes.
- [ ] Reproduzir início/parada do broker e dos três serviços, com confirmação de prontidão e armazenamento preservado, sem Docker.
- [ ] Jornadas com serviços separados: aceite/consulta imediata, registro, rejeição, repetição, conflito, comando duplicado, ausência e erro técnico.
- [ ] Provar invalidação/expiração do cache e comportamento sob indisponibilidade; 503 rastreável não vira 404.
- [ ] Provar quarentena/replay, recuperação após reinício, eventos duplicados, isolamento de acesso e trilha operacional.
- [ ] Medir p95 de consulta com cache contra a premissa de 100 ms, declarando máquina, dados, aquecimento, concorrência e quantidade de requisições. Não inferir desempenho a partir de teste unitário.
- [ ] Tratar ~500 emissões/s e proporção 20:1 como premissas: medir se aceitas como meta de entrega, ou ratificar explicitamente o limite didático; não declarar capacidade não testada.
- [ ] Um segundo integrante consegue reproduzir instalação/build/subida/demo com instruções e variáveis, sem credenciais pessoais ou resíduo de `target`.
- [ ] Matriz final de requisito → teste → resultado; todos os defeitos bloqueantes corrigidos e ADRs sincronizados. Guardar evidências reais, não marcar testes planejados como executados.

**Pronto quando:** não restam requisitos obrigatórios/selecionados sem aceite ou sem revisão explícita de escopo; há evidência integrada, não só mocks isolados.

### E15 — Fechamento, entrega e defesa (somente por último)

**Portão de entrada — todos devem estar atendidos:**

- [ ] E6–E12 concluídas e E14 aprovada; E13 já registrada como não aplicável pelo grupo de três.
- [ ] Nenhum bloqueio de CORE ou requisito do tema escondido em “próximos passos”; desvios aprovados têm justificativa e evidência do perfil.
- [ ] Comandos e eventos comprovados no broker JVM, incluindo persistência, reentrega e assinaturas duráveis; nenhum passo da entrega depende de Docker.
- [ ] Contratos e build final verdes, fluxos críticos reproduzidos e pendências técnicas resolvidas.

**Só depois:**

- [ ] Consolidar README/arquitetura com diagrama, execução, perfis reais, garantias, limitações e procedimentos.
- [ ] Preencher `AVALIACAO.md`: nomes dos três integrantes, papéis, nível justificado por critério e caminhos/testes/commits; manter opcionais como não aplicáveis.
- [ ] Reflexão honesta sobre IA: o que gerou, decisões humanas, erros encontrados e como foram verificados.
- [ ] Revisar links, divergências documentais, premissas e evidências; manter histórico das etapas anteriores.
- [ ] Preparar demo e defesa dentro dos aproximadamente 20 minutos por grupo previstos no brief, reservando tempo para arguição: problema, contextos, jornada, contratos, falhas/replay e trade-offs.
- [ ] Entrega acessível em repositório/zip sem segredos; verificar push/autenticação se a entrega for pelo remoto, sem presumir que commits locais já foram enviados.
- [ ] Registrar marco final somente após esses aceites.

**Fechamento não substitui desenvolvimento.** Documentação acompanha cada etapa; E15 consolida um sistema já pronto, não encerra o projeto deixando contratos, eventos, proteção ou testes obrigatórios para depois.
