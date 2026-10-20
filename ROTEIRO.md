# Roteiro final — somente o que falta

Atualizado em **20/10/2026**, após o usuário informar a orientação do professor: **seguir apenas os critérios de avaliação**. A referência de entrega é o [brief](brief-alunos.md), com seus nove critérios, não a implementação integral das sessões do tema ou do exemplo PIX.

**A implementação técnica necessária já tem evidência. Não há nova funcionalidade planejada.** Falta concluir a comprovação da versão final, a autoavaliação e a preparação da entrega. Isso não significa nota máxima garantida ou banca já aprovada.

## 1. Base pronta — não refazer

- Três serviços, com bases segregadas de emissão e registro.
- Fluxo por fila Artemis, consumidor idempotente e outbox.
- Cache com TTL, fallback e invalidação explícitos.
- Retry/backoff, quarentena/DLQ, timeout e circuit breaker.
- Contrato PACT executável entre consulta e registro.
- ADRs, diagrama e instruções de execução em **perfil B/JVM, sem Docker**.
- Última revalidação em 20/10/2026: `mvn -B clean verify`, **265 testes aprovados**, zero falhas/erros/ignorados, incluindo PACT.

Evidências por critério: [AVALIACAO.md](AVALIACAO.md). O build não substitui a demonstração dos quatro processos. Nenhuma implementação ou teste existente será removido por ultrapassar o mínimo.

## 2. O que falta — três etapas finais

### F1 — Conferir a demonstração da versão final

**Objetivo:** completar a evidência do critério 9 e selecionar o que mostrar na banca, reaproveitando testes e instruções existentes.

- [ ] Seguir o [README](README.md): iniciar broker e três serviços, confirmar prontidão e encerrar corretamente, sem Docker.
- [ ] Demonstrar emissão aceita (`202`), registro e consulta do estado final com dados sintéticos.
- [ ] Demonstrar repetição sem duplicação e conflito de conteúdo (`409`).
- [ ] Conferir consulta/cache e invalidação na transição; usar os testes existentes para evidenciar TTL e a corrida cache/evento, sem ensaio de carga.
- [ ] Selecionar as evidências existentes de rejeição de negócio, retry/quarentena e timeout/breaker, distinguindo erro técnico de ausência (`503` × `404`). Não é necessário repetir manualmente toda a suíte.
- [ ] Registrar comandos, resultados observados e limites da demonstração; corrigir apenas defeitos que bloqueiem os critérios ou a execução.

**Pronto quando:** a versão atual roda conforme as instruções e há uma sequência curta e reproduzível de demonstração. Não criar ferramenta nova, endpoint administrativo ou infraestrutura para essa etapa.

O build limpo já foi revalidado nesta data. Reexecutar antes da entrega se houver alteração de código, dependências ou contratos; não abrir uma nova campanha de testes apenas pela mudança documental do roteiro.

### F2 — Finalizar os documentos obrigatórios e a autoavaliação

Pode ser preparada em paralelo à F1; finalizar as evidências de execução depois dela.

- [ ] Preencher nomes e papéis dos **três integrantes**.
- [ ] Preencher os níveis de 0–3 dos nove critérios na [autoavaliação](AVALIACAO.md), com evidências localizáveis e limitações, sem atribuir pontos por algo apenas planejado.
- [ ] Consolidar uso crítico de IA: o que gerou, quais decisões foram humanas, erros encontrados e o que cada integrante realmente validou pessoalmente. Não apresentar testes do assistente como verificações manuais do grupo.
- [ ] Conferir README, diagrama e ADRs existentes: perfil B, comandos, garantia at-least-once, idempotência, cache e trade-offs. Corrigir somente divergências; não criar ADRs ou documentação sem necessidade.
- [ ] Manter os pesos do brief (**15/15/12/10/12/13/13/5/5**); registrar a divergência do guia da banca (12/12 nos critérios 6/7, soma 98), sem inventar nota.

**Pronto quando:** todos os critérios têm nível justificado e evidência; não restam campos obrigatórios em branco nem alegações de execução ou validação humana não realizadas.

### F3 — Fechar a entrega e preparar a defesa

**Dependências:** F1 e F2 concluídas. O fechamento continua sendo o último passo.

- [ ] Preparar apresentação/demonstração: problema, contextos, fluxo, decisões e uso crítico de IA. Reservar tempo para arguição dentro dos aproximadamente **20 minutos por grupo** do brief; o guia sugere 8–12 minutos de apresentação.
- [ ] Dividir a fala e ensaiar as justificativas de corte, fila × evento, consistência, idempotência, cache, resiliência e contrato.
- [ ] Conferir links e conteúdo do repositório/zip; excluir dados locais e segredos da entrega, sem apagar bases existentes.
- [ ] Garantir que o material entregue esteja acessível no formato escolhido. Se for remoto, fazer push **somente mediante solicitação**, sem presumir que commits locais já foram enviados.
- [ ] Registrar o fechamento somente após os itens anteriores.

**Pronto quando:** entrega acessível, documentos completos e grupo preparado para demonstrar e defender o que foi implementado.

## 3. O que saiu do caminho de conclusão

Não desenvolver nem usar como bloqueio de fechamento:

- E11: autenticação/autorização por convênio, TLS, auditoria persistida e criptografia gerenciada.
- E12: etapa separada de operação, cópia/restauração completa, retenção e replay administrativo/auditado. Início/parada e preservação básica já documentados permanecem na F1.
- Ensaios de p95 < 100 ms, 500 emissões/s e proporção 20:1. São premissas do tema **não medidas**, não capacidades demonstradas.
- Kafka, SAGA adicional, segundo par PACT, notificação real, dashboards ou opcionais adicionais. O grupo tem três integrantes.

Os nove critérios gerais, inclusive resiliência e uso crítico de IA, **continuam válidos**. A orientação docente foi informada pelo usuário; não é aprovação prévia da nota nem prova de implementação dos controles excluídos. O sistema continua demonstrador local, em loopback e com dados sintéticos.

## 4. Substituição do roteiro antigo e histórico

Este roteiro **substitui os portões anteriores**, não apenas acrescenta uma nota:

| Planejamento anterior | Situação atual |
|---|---|
| E0–E10 | Implementadas; histórico e evidências preservados |
| E11 | Não implementada e fora da entrega avaliada; não é pendência |
| E12 revisada | Retirada como etapa; instruções mínimas de execução aproveitadas na F1 |
| E13 | Não aplicável ao grupo de três |
| E14 extensa | Substituída pela conferência enxuta F1, sem carga/backup/compliance |
| E15 | Substituída por F2 e F3, com fechamento por último |

O roteiro anterior está preservado no Git, commit `b11fad9`. As [etapas implementadas](docs/etapas/), [requisitos originais](requisitos/README.md), [validações históricas](docs/validacao-pendente.md) e a [ADR-014 atualizada](docs/adr/ADR-014-prioridade-core-e-limites-de-escopo.md) não são novas obrigações de implementação.

**Controle de versões:** cada etapa final concluída deve ter commit próprio, com resumo e hash informados antes da seguinte. Não fazer push, descartar alterações ou reverter versões sem solicitação. Esta reorganização documental tem commit próprio; não conclui F1, F2 ou F3.
