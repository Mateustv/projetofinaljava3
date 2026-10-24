# Validação do projeto

> Os comandos abaixo foram executados na raiz de `projeto-final-grupo`. Os passos já validados estão marcados `[x]`.

> **Planejamento vigente em 23/10/2026:** após o usuário informar a orientação docente de seguir apenas os critérios de avaliação, o [roteiro F1–F3](../ROTEIRO.md) substitui os portões anteriores. Referências históricas abaixo a E11/E12/E14/E15 não criam tarefas adicionais: não há etapa de backup completo, carga ou compliance a cumprir. Aquela reorganização foi somente documental, com links/whitespace conferidos; sua evidência era o build limpo com 265 testes da revisão `b11fad9`. Revalidações posteriores estão abaixo; **F1 concluída em 21/10/2026; F2 e F3 concluídas tecnicamente em 23/10/2026** (restando apenas preencher os 2 nomes em `AVALIACAO.md` e realizar o ensaio falado do grupo).

## F2 e F3 — Documentação de negócio, autoavaliação, roteiro de defesa e fechamento — 23/10/2026

- [x] **F2 ([AVALIACAO.md](../AVALIACAO.md)):** Níveis `0–3` preenchidos e justificados para os 9 critérios do [brief](../brief-alunos.md) (pesos `15/15/12/10/12/13/13/5/5 = 100`, registrando a divergência `12/12` soma 98 do guia da Aula 9), com mapa de localização de evidências em código, configurações, testes e ADRs, além da seção de reflexão crítica sobre o uso de IA (separando geração de commodity, julgamento arquitetural humano e 4 bugs reais detectados/corrigidos).
- [x] **Alinhamento ao problema real de negócio bancário:** [README.md](../README.md), [arquitetura.md](arquitetura.md), [guia-do-projeto.md](guia-do-projeto.md), [requisitos/](../requisitos/) e [ADRs 001–014](adr/) revisados para ancorar cada escolha nas 4 pressões do domínio (pico de fim de mês de 500 req/s, risco de cobrança duplicada, trava regulatória Bacen/CIP e leitura 20:1).
- [x] **F3 ([F3-defesa-e-fechamento.md](etapas/F3-defesa-e-fechamento.md)):** Roteiro de apresentação (10 min de exposição + 10 min de arguição), divisão de fala entre os 3 integrantes e respostas-modelo de Nível III (*escolha + sacrifício + contexto*).
- [x] **Conferência de links e higiene do repositório:** Varredura automatizada em todos os arquivos `.md` do repositório (`0` links quebrados); `.gitignore` e `git ls-files` auditados (nenhum banco local `data/`, `target/`, log ou segredo versionado; bases locais preservadas na máquina). Código Java e suíte de **277 testes** inalterados.
- [ ] **Ação exclusiva dos integrantes antes da banca:** Preencher os nomes dos Integrantes 2 e 3 em [AVALIACAO.md](../AVALIACAO.md) e realizar o ensaio falado da equipe.

## F1 — Demonstração final — 21/10/2026

- [x] Quatro JARs da versão `96b8075`, sem Docker, em portas HTTP 8081/8082/8083 e broker 61617, com bancos/journal temporários próprios. Healths `UP`.
- [x] Navegador: emissão `202` → `REGISTRADO`, repetição com mesmo UUID, conflito `409` e confirmação de um único aceite/comando/resultado.
- [x] Cache preenchido com transitório, evento/invalidação, novo preenchimento final e hit; rejeição de negócio sem DLQ; falha com três entregas e quarentena observada duas vezes sem consumo.
- [x] `404` para ausência real com fallback permitido; `503`/circuito aberto quando registro indisponível, sem fallback técnico.
- [x] Registro reiniciado com o mesmo banco: comando pendente processado e consulta voltou a `REGISTRADO`; resultado anterior também preservado após reinício.
- [x] **48 testes selecionados**, zero falhas/erros/ignorados: controller/cache (12), eventos/corrida (14), resiliência HTTP (22). Build completo anterior de 277 testes preservado; código não mudou na F1.
- [x] Sequência de 6–8 minutos, comandos e limitações registrados no [relatório F1](etapas/F1-demonstracao-final.md).
- [x] Processos próprios encerrados pela sessão de execução e portas liberadas. Ctrl+C em consoles ocultas não funcionou neste ambiente; encerramento automatizado não é apresentado como teste de shutdown gracioso. A parada interativa recomendada continua documentada no README.

A F1 conclui a conferência técnica pelo assistente, não a autoavaliação, ensaio pessoal ou entrega. F2/F3 permanecem abertas. Evidências/UUIDs completos, recuperação e condições de encerramento estão no relatório.

## Painel web didático — 21/10/2026

Recurso opcional solicitado pelo grupo para enxergar os fluxos, não novo critério. Comandos, interpretação e limites: [laboratório web](painel-demonstracao.md).

- [x] `mvn -B clean verify`: **BUILD SUCCESS, 277 testes**, zero falhas/erros/ignorados, incluindo PACT. São os 265 anteriores mais 12 verificações de rastros, perfil, proxy HTTP, cenários e quarentena.
- [x] Rastros desativados não acumulam dados; janela máxima de 2.000 por JVM; filtro por UUID; callback só afirma commit depois dele, nunca após rollback.
- [x] Recursos/endpoints ausentes no perfil normal; página/JS/CSS e diagnóstico disponíveis no perfil `demonstracao`.
- [x] Proxy preserva status/corpo/correlação de `202`, `409`, `400` e `503`; falha de transporte retorna `503` explícito; destinos não locais são recusados; erros de fontes não são tratados como janelas vazias bem-sucedidas.
- [x] Prefixos de rejeição/falha só mudam cenário no modo de demonstração; aprovação explícita não depende do cenário padrão.
- [x] Browse em broker real confirma mensagem sem consumir, repetindo a observação; limite de 200 mensagens é informado.
- [x] `node --check` do JavaScript e conferência de links locais/catálogo dos **86 arquivos Java de produção**.
- [x] Broker e três JARs reais, com perfil `demonstracao`, journal e H2 temporários isolados. Portas 61617 e 18181/18182/18183; os três healths responderam `UP`.
- [x] Navegador: aprovação `202` → `REGISTRADO`; UUID `7148f900-1289-4e63-a765-b3a5bfbcde77`. Rastros reais de commit, publicação, consumo, evento e cache. A primeira consulta preencheu `AGUARDANDO_REGISTRO`; após evento/invalidação houve preenchimento `REGISTRADO` e hits posteriores.
- [x] Repetição retornou mesmo UUID; conflito retornou `409`. Diagnóstico confirmou **um aceite, um comando publicado e um resultado persistido**, com rastros de idempotência/conflito.
- [x] Navegador: rejeição `REJEITADO`, código `SIM-REJ-001`, sem mensagem na quarentena; UUID `5305bf89-b793-405e-aaaf-0f96d2315a15`.
- [x] Navegador: falha com entregas **1, 2 e 3**, três falhas e mensagem do UUID `62ccaf93-0758-46c7-922d-356906891e70` observada na quarentena; estado continuou `AGUARDANDO_REGISTRO`.
- [x] Navegador: JSON malformado informado antes do envio e convênio inválido retornou `400`.
- [x] Segunda execução real, deliberadamente sem JVM de registro: emissão aceita, consulta `503`, três falhas HTTP e `CIRCUITO_ABERTO`; **nenhum fallback indevido**, diagnóstico explicitamente indisponível. UUID `12f70f4c-38b0-4b50-acd9-4d24ea616210`.
- [x] Layout conferido em desktop e viewport 390 × 844; sem overflow horizontal. Acompanhamento pausado pelos controles.
- [x] JVMs de validação encerradas, sem processos de demonstração deixados ativos. Não foram usados/apagados bancos existentes do grupo.

Execução feita pelo assistente com dados sintéticos: não atribuir validação humana aos integrantes. Não mede carga, p95, capacidade produtiva, segurança ou auditoria; não encerra F1–F3.

## Organização dos pacotes antes da F1 — 20/10/2026

- [x] Classes dos três serviços separadas por tipo, com ponto de entrada no pacote raiz e contratos compartilhados preservados; ver [guia dos pacotes](arquitetura.md#organização-dos-pacotes-java).
- [x] Testes organizados em `integration`, `contracts` e pacotes de unitários por tipo, mantendo os testes de inicialização na raiz.
- [x] Imports e links para arquivos movidos atualizados. O construtor com `Clock` do dispatcher e os auxiliares de configuração HTTP/cache usados de outros pacotes passaram a públicos, sem alterar lógica ou configuração Spring.
- [x] `mvn -B clean verify`: **BUILD SUCCESS, 265 testes**, zero falhas/erros/ignorados (19 infraestrutura-http, 12 broker, 17 eventos-outbox, 97 emissor, 69 registro, 50 consulta Surefire + 1 Failsafe); PACT consumidor/provedor e comparação canônica preservados.

Essa foi a revalidação funcional da reorganização, posterior à revisão documental citada acima. Naquela mudança não houve nova execução de quatro JARs, carga ou conclusão da F1. Não foram alterados endpoints, DTOs compartilhados, nomes de tabelas/destinos, orçamentos de retry nem fronteiras transacionais.

## Validação da E0

- [x] **1. Ambiente**
    ```powershell
    java -version
    mvn -v
    ```
    Resultado: Java 17.0.12 e Maven 3.9.10.
- [x] **2. Compilar, testar e instalar os módulos no repositório Maven local**
    ```powershell
    mvn -B clean install
    ```
    Resultado: `BUILD SUCCESS` nos 4 módulos; 17 testes aprovados. O `install` prepara as dependências locais para iniciar cada serviço separadamente.
- [x] **3. Subir o emissor e verificar o health** (em terminais separados)
    ```powershell
    mvn -pl boleto-emissor spring-boot:run
    curl.exe -i http://localhost:8081/actuator/health
    ```
    Resultado: emissor iniciou; HTTP 200 e `{"status":"UP"}`.
- [x] **4. Subir o registro e verificar o health**
    ```powershell
    mvn -pl boleto-registro spring-boot:run
    curl.exe -i http://localhost:8082/actuator/health
    ```
    Resultado: registro iniciou; HTTP 200 e `{"status":"UP"}`.
- [x] **5. Subir a consulta e verificar o health**
    ```powershell
    mvn -pl boleto-consulta spring-boot:run
    curl.exe -i http://localhost:8083/actuator/health
    ```
    Resultado: consulta iniciou; HTTP 200 e `{"status":"UP"}`. Os três processos foram parados após os testes.

## Validação da E1

- [x] **6. Testes** (a E0 e a E1 rodam juntas no `verify` do passo 2; os testes novos estão no emissor):
    ```powershell
    mvn -B -pl boleto-emissor -am test
    ```
    Resultado: `BUILD SUCCESS`; `EmissaoValidatorTest` (11), `BoletoControllerTest` (3) e `EmissorApplicationTests` (1) verdes.
- [x] **7. POST válido → 202**: resposta HTTP 202 com `identificador`, `status:"AGUARDANDO_REGISTRO"` e `data_hora_recebimento`.
- [x] **8. POST inválido → 400** com convênio `"1"` e valor `0`: resposta HTTP 400 com erros de `convenio` e `valor_nominal`.
- [x] **9. JSON malformado → 400**: resposta HTTP 400.

### Corpos para os testes HTTP 7 e 8 (PowerShell)

Defina o corpo válido uma vez e envie-o:

```powershell
$body = @'
{
  "convenio":"123456","numero_controle_cliente":"NF-0001",
  "beneficiario":{"nome":"Empresa X","tipo_documento":"CNPJ","numero_documento":"12345678000195",
    "endereco":{"logradouro":"Rua A, 1","cidade":"Brasilia","uf":"DF","cep":"70000000"}},
  "pagador":{"nome":"Maria","tipo_documento":"CPF","numero_documento":"12345678909",
    "endereco":{"logradouro":"Rua B, 2","cidade":"Brasilia","uf":"DF","cep":"70000000"}},
  "valor_nominal":150.00,"data_emissao":"2026-10-19","data_vencimento":"2026-11-19","especie":"DM"
}
'@
curl.exe -i -X POST http://localhost:8081/boletos -H "Content-Type: application/json" --data-binary $body
```

Para o teste inválido, altere os dois campos e envie de novo:

```powershell
$bodyInvalido = $body.Replace('"convenio":"123456"', '"convenio":"1"').Replace('"valor_nominal":150.00', '"valor_nominal":0')
curl.exe -i -X POST http://localhost:8081/boletos -H "Content-Type: application/json" --data-binary $bodyInvalido
```

> Nota histórica da E1: naquele momento o POST ainda não persistia o aceite. A persistência e leitura interna foram adicionadas na E2; o fluxo de consulta com fallback foi adicionado na E3.

## Validação da E2

- [x] **10. Testes automatizados de persistência e idempotência**
    ```powershell
    mvn -B -pl boleto-emissor -am test
    ```
    Resultado: `BUILD SUCCESS`; 20 testes do emissor passaram, sem falhas. Inclui repetição igual, conflito `409`, escala decimal equivalente, concorrência, uma linha por chave e consulta interna.
- [x] **11. Repetir emissão → mesmo aceite:** dois POSTs iguais retornaram `202` com o mesmo UUID e a mesma data/hora.
- [x] **12. Mesma chave e conteúdo diferente → conflito:** alterar o valor mantendo convênio e número de controle retornou `409` com `CHAVE_IDEMPOTENCIA_CONFLITANTE`.
- [x] **13. Consultar aceite pelo endpoint interno:** `GET /interno/boletos/{identificador}` retornou `200` e o aceite original.
- [x] **14. Identificador não encontrado:** consulta interna com UUID válido inexistente retornou `404`.
- [x] **15. Build completo após E2**
    ```powershell
    mvn -B clean verify
    ```
    Resultado: `BUILD SUCCESS` nos 4 módulos, 22 testes aprovados no total (20 no emissor).

## Se algo falhar

Cole o trecho `[ERROR]` completo. Pontos prováveis: versão do JDK, `spring-boot-starter-parent` ou `h2` não encontrados no Nexus (então avaliaremos o Plano C), porta ocupada.

## Histórico

| Data  | Etapa   | Resultado |
| ----- | ------- | --------- |
| 19/10 | E0 | Java 17.0.12/Maven 3.9.10; build e 17 testes aprovados; health dos 3 serviços HTTP 200. |
| 19/10 | E1 | 15 testes do emissor aprovados; POST válido 202, POST inválido 400 e JSON malformado 400. |
| 19/10 | ADR-004 | Opção C discutida e aceita pelo grupo; leitura interna implementada em E2 e fallback em E3. |
| 19/10 | E2 | 20 testes do emissor; 22 no build completo. Repetição 202 estável, conflito 409, leitura interna 200 e ausente 404. |
| 19/10 | E3 | 8 testes de consulta; 29 no build completo. Fallback 200, cache hit, ausência 404, UUID inválido 400 e indisponibilidade 503 rastreável. |
| 19/10 | E4 | 22 testes do emissor, 5 do registro e 8 da consulta; 35 no build completo. Emissão HTTP 202, entrega após reinício do emissor e consulta interna 200/REGISTRADO. |
| 20/10 | E5 HTTP | 53 testes: 33 do emissor, 12 do registro e 8 da consulta. Retry limitado, isolamento persistido, rejeição de negócio e compatibilidade com schema E4; broker RabbitMQ/DLQ não implementados. |

## Validação da E3

- [x] **16. Testes automatizados da consulta/cache**
    ```powershell
    mvn -B -pl boleto-consulta -am test
    ```
    Resultado: `BUILD SUCCESS`; 8 testes da consulta aprovados, incluindo política Caffeine de expiração variável.
- [x] **17. Build completo**
    ```powershell
    mvn -B clean verify
    ```
    Resultado: `BUILD SUCCESS` nos 4 módulos, 29 testes aprovados.
- [x] **18. Fallback real (emissor, registro e consulta ligados):** POST válido no emissor e consulta pelo UUID no serviço de consulta. Resultado: HTTP 200, `AGUARDANDO_REGISTRO`.
- [x] **19. UUID existente no cache:** repetir o GET devolveu HTTP 200. O teste automatizado confirmou que a segunda leitura não chamou as fontes novamente.
- [x] **20. UUID válido inexistente:** consulta retornou HTTP 404. Teste automatizado confirmou que 404 não fica no cache.
- [x] **21. UUID malformado:** `/boletos/nao-e-uuid` retornou HTTP 400.
- [x] **22. Falha técnica:** com o serviço de registro indisponível, a consulta retornou HTTP 503 com `correlation_id` e `X-Correlation-ID`, nunca 404.

> Contexto histórico da E3: naquele marco, o serviço de registro ainda não tinha endpoint/dados de leitura; a E4 implementou o consumidor, a persistência e a leitura de `REGISTRADO`.

## Validação da E4

- [x] Testes direcionados do produtor/outbox e consumidor:
    ```powershell
    mvn -B -pl boleto-emissor,boleto-registro -am test
    ```
    Resultado: `BUILD SUCCESS`; 22 testes do emissor e 5 do registro passaram.
- [x] O emissor grava aceite e mensagem outbox na mesma transação; reenvio idempotente não cria uma segunda mensagem.
- [x] O consumidor registra o comando uma vez, responde de forma idempotente à reentrega e rejeita conteúdo conflitante com `409`.
- [x] Falha de transporte mantém o comando pendente para nova tentativa; sucesso remove o payload da outbox.
- [x] Validação manual entre processos: `POST /boletos` retornou `202`/`AGUARDANDO_REGISTRO`; após o despachante, `GET /interno/boletos/{id}` retornou `200`/`REGISTRADO` e `SIM-{id}`.
- [x] Reinício durante indisponibilidade: aceitei um boleto com o registro desligado, reiniciei o emissor, subi o registro e confirmei a entrega posterior do comando persistido.
- [x] `mvn -B clean verify`: `BUILD SUCCESS`; 35 testes nos quatro módulos.

## Validação da E5 provisória por HTTP

- [x] Testes de retry com relógio controlado: três tentativas totais, espera de 1 s e depois 5 s; JSON inválido/nulo isolado imediatamente.
- [x] Testes de integração do produtor com cliente HTTP real, servidor local de teste e H2: erro 503, backoff persistido, recuperação, esgotamento e ausência de novos envios após isolamento.
- [x] Falhas permanentes HTTP 400/409 não repetidas; HTTP 408/429 retentáveis; HTTP 302 não confirma a entrega.
- [x] Lote de 20 mensagens envenenadas isolado sem impedir a entrega da próxima mensagem boa.
- [x] `GET /interno/outbox/falhas` retorna somente metadados; logs não incluem conteúdo livre de exceções.
- [x] Registro simulado com aprovação, rejeição e indisponibilidade. Rejeição é persistida e idempotente, sem dados de pagamento; falha técnica retorna 503 e não deixa registro parcial.
- [x] Compatibilidade do H2: teste de rejeição começa com tabela no formato E4, cujos campos de confirmação eram `NOT NULL`.
- [x] Reentregas concorrentes não duplicam registro; falha na gravação da outbox faz rollback do aceite.
- [x] `mvn -B clean verify`: `BUILD SUCCESS`; 53 testes, sem falhas (33 emissor, 12 registro, 8 consulta).
- [ ] Validação com RabbitMQ, exchanges/bindings e DLQ reais — fora do escopo HTTP aprovado.

Os testes HTTP desta E5 usam servidor local controlado, não os três serviços implantados em processos separados. Os cenários manuais de E4 continuam registrados acima; o roteiro manual atualizado de E5 está em [E5-resiliencia-outbox.md](etapas/E5-resiliencia-outbox.md).

## Validação da E6 — PACT consulta ↔ registro (20/10/2026)

- [x] Consumidor: três cenários contra mock HTTP PACT, com `RestBoletoFonteClient` real, snake_case, UUID/datas e campos condicionais.
- [x] Provedor: três interações contra aplicação Spring Boot real em porta aleatória, com estados no H2 em memória exclusivo.
- [x] Um único pact canônico versionado em [shared-contracts/pacts](../shared-contracts/pacts/boleto-consulta-boleto-registro.json); `PactArtifactIT` compara o gerado no consumidor com esse mesmo contrato usado pelo provedor.
- [x] Verificação direcionada: 3 testes do consumidor, 3 interações do provedor e 1 comparação, todos aprovados.
- [x] Prova negativa: alterado temporariamente no canônico o status do inexistente de 404 para 200. Execução com `-fae` falhou no provedor (`expected status of 200 but was 404`) e na comparação do artefato. Alteração removida.
- [x] Após restauração, `mvn -B clean verify`: **BUILD SUCCESS**, **60 testes**, zero falhas/erros/ignorados: 33 emissor, 15 registro, 11 consulta no Surefire e 1 no Failsafe.
- [x] Execução sem Docker, broker de mensagens, Pact Broker ou serviços externos iniciados previamente. Testes encerram seus recursos; bancos de demonstração não foram usados. Telemetria PACT e publicação de resultados desabilitadas.

Comandos e atualização do contrato: [E6-contratos-pact.md](etapas/E6-contratos-pact.md). `mvn test` não executa a comparação Failsafe; o portão completo é `clean verify`.

Pendências mantidas: dados simulados/histórico E7, broker JVM E8, eventos/invalidação E9 e demais etapas do [roteiro](../ROTEIRO.md). Este resultado não encerra a validação integrada da E14 nem autoriza fechamento antecipado.

## Validação da E7 — contrato funcional (20/10/2026)

- [x] Matriz de campos consolidada, com 54 novos casos de ausência/formato/limite/condicionais no emissor; mesma validação estrutural reutilizada pelo registro.
- [x] 19 casos de comando sem obrigatório e UUID fora do contrato recusados antes de chamar a registradora; erro não grava resultado nem operação simulada.
- [x] Novos aprovados persistem/expõem marcadores não pagáveis; rejeição e transitório sem dados de pagamento. PACT consumidor/canônico/provedor e projeção pública atualizados.
- [x] Histórico mínimo de origem/transição final persistido junto com o novo resultado; reentrega não duplica histórico nem chama a registradora de novo.
- [x] Ledger simulado em transação independente: resposta perdida na aprovação/rejeição, falha local e rollback após flush recuperam o mesmo resultado, sem segundo efeito externo.
- [x] Mesmo UUID/conteúdo diferente conflita mesmo sem resultado local; resposta salva não é recalculada quando o cenário muda.
- [x] Finais legados preservados sem reprocessamento ou fatos retroativos inventados; teste com schema E4 continua verde.
- [x] Inserção exclusiva no ledger com UUID atribuído (versão JPA inicialmente nula); chamadas concorrentes com conteúdo diferente não sobrescrevem a operação vencedora.
- [x] `mvn -B clean verify`: **BUILD SUCCESS**, **146 testes**, zero falhas/erros/ignorados: 87 emissor, 47 registro, 11 consulta Surefire e 1 Failsafe.

Detalhes e matriz: [E7](etapas/E7-contrato-funcional.md). Sem Docker, processos permanentes ou alteração dos bancos de demonstração. Nenhum teste de carga/broker/jornada completa dos três processos foi executado nesta etapa.

Próxima entrega: E8, seleção/validação e integração de broker JVM para comandos; eventos E9, auditoria E11 e operação E12 permanecem pendentes. Fechamento E15 continua bloqueado até o aceite integrado.

## Validação da E8 — Artemis em JVM (20/10/2026)

- [x] Dependências Artemis 2.33.0/Jakarta JMS disponíveis; Java 17/Windows e Spring Boot 3.3.5. Broker standalone com journal NIO e filas ANYCAST duráveis.
- [x] TCP real em porta isolada e validação explícita de argumentos. Ativação real exigida antes de anunciar prontidão; conflito de porta diagnosticado sem encerrar processo alheio.
- [x] Commit JMS antes de concluir a outbox; destino/fila ausente não confirma envio. Publicação sem binding tem encaminhamento à quarentena.
- [x] Mensagem confirmada e quarentena sobrevivem a reinício. Contador de entrega persistido não zera; commit/rollback e mínimos de redelivery 1 s/5 s verificados.
- [x] Duas assinaturas MULTICAST duráveis, uma offline, recebem independentemente e preservam a pendência após reinício. Capacidade para E9, não eventos de produção já implementados.
- [x] Outbox JMS/H2: indisponibilidade, confirmação perdida/duplicata, consumidor offline, reinício e migração sem reiniciar isolados/entregues nem perder contador/backoff.
- [x] Listener real/JMS/JPA: idempotência e histórico/efeito únicos; inválido/conflito/corpo não textual para quarentena; temporário esgota três entregas sem impedir o bom; rejeição de negócio confirma, sem DLQ.
- [x] Falha após commit JPA antes de ack JMS e rollback JPA após efeito externo recuperam sem duplicar efeito/histórico. Falha de envio à quarentena reverte o original, inclusive destino com endereço sem fila.
- [x] `mvn -B clean verify`: **BUILD SUCCESS, 177 testes**, zero falhas/erros/ignorados. Relatórios XML: **12 broker, 93 emissor, 60 registro, 11 consulta Surefire + 1 Failsafe**. PACT e regressão E1–E7 preservados.
- [x] Quatro JVMs/arquivos isolados, porta alternativa 61617: emissão 202 com registro offline, confirmação do broker, reinício com mensagem pendente, início do consumidor e consultas interna/pública `REGISTRADO`. Health dos três serviços HTTP 200/UP; endpoint de comandos HTTP legado 404.
- [x] Processos usados no teste encerrados; nenhuma exclusão de banco de demonstração anterior.

Operação, migração, nomes dos testes e limitações: [E8](etapas/E8-broker-jvm.md) / [ADR-011](adr/ADR-011-broker-jvm-artemis.md). Na máquina, 61616 estava ocupada em estado **Bound**: filtrar apenas Listen não detectava o conflito. Foi usada 61617, sem interferência em processos alheios.

Na E8, a próxima entrega era E9, validada abaixo. Proteção E11, replay/retenção E12, carga/jornadas/reprodução E14 e fechamento E15 continuam pendentes. Essa validação não declara ambiente pronto para produção.

## Validação da E9 — Eventos e invalidação

- [x] Envelopes dos três tipos com exatamente sete campos, status consistente e obrigatórios validados; sem dados pessoais/valor.
- [x] Aceite + comando + intenção atômicos sem broker. Duplicata/conflito não criam outro evento; falha depois de flush reverte tudo.
- [x] Aprovação/rejeição + histórico + intenção atômicos. Falha de intenção/rollback externo desfazem estado e evento, mas preservam ledger independente; reentrega recupera resultado sem duplicação. Final legado não recebe evento inventado.
- [x] Publicação após commit, confirmação JMS, três tentativas/1 s/5 s e isolamento sem bloquear item bom. Falha local após commit JMS repete o mesmo id; ausência da assinatura não marca publicado.
- [x] Dois assinantes duráveis independentes; consulta consome e o outro permanece offline. Reinício preserva a cópia pendente, rollback causa reentrega.
- [x] Listener real da consulta invalida após evento final antes do TTL; duplicatas/ordem não projetam nem rebaixam estado. Outro boleto preserva cache hit.
- [x] Corrida determinística com latches: miss/preenchimento em andamento × evento; invalidar aguarda a leitura, remove preenchimento e só então confirma JMS.
- [x] Falha antes do ack causa reentrega; falha de invalidar esgota três entregas na quarentena sem bloquear bom. JSON/envelope/corpo inválidos preservados na quarentena.
- [x] `mvn -B clean verify`: **BUILD SUCCESS, 214 testes**, zero falhas/erros/ignorados. **12 broker, 17 eventos-outbox, 96 emissor, 65 registro, 23 consulta Surefire + 1 Failsafe**. PACT e regressões anteriores preservados.
- [x] Regressão inicial encontrou configuração JPA importada no main contaminando `@JsonTest`; corrigido por component scan delimitado. Reexecução direcionada confirmou 54 testes de regras JSON e 8 de atomicidade, seguida de regressão limpa completa.

Operação, matriz e restrições: [E9](etapas/E9-eventos-e-cache.md), [ADR-012](adr/ADR-012-eventos-e-invalidacao.md). Testes de eventos usam Artemis/H2 reais, mas fontes HTTP simuladas na consulta; não são nova prova de quatro JVMs. Execução integrada completa e reprodução por outro integrante continuam E14. Uma instância de cada serviço; consultas adicionais com cache local precisariam de assinaturas exclusivas.

Na E9, a próxima entrega era E10, validada abaixo. Sem fechamento antecipado; E15 somente após E14.

## Validação da E10 — Resiliência e correlação

- [x] Inventário de todas as fronteiras: consulta HTTP 2 s conexão/3 s leitura; parâmetros positivos; redirect automático desligado. JMS/retentativas persistidas preservados. Registradora continua local; timeout de registradora externa não é funcionalidade comprovada.
- [x] Circuitos Resilience4j independentes por fonte: janela/minimo de três e 100% de falhas; OPEN 10 s; uma sonda HALF_OPEN. Clock controlado prova bloqueio antes da espera, recuperação depois, sonda única, reabertura por falha e nenhuma chamada remota quando aberto.
- [x] HTTP 4xx/5xx, timeout e resposta inválida nunca viram 404/sucesso; nenhuma tentativa extra de aplicação. Ausência 404 e rejeição 200 são disponibilidade, inclusive para fechar a sonda. Fallback somente depois de 404; cache hit continua disponível com circuito aberto e erro não é cacheado.
- [x] Servidor HTTP local real comprova timeout de leitura e ausência de redirect automático. Limites de socket não são deadline global ou medição de p95/carga.
- [x] Filtro dos três serviços valida/gera UUID v4 e devolve `X-Correlation-ID` em sucesso/erro/cache hit. Valores não canônicos, duplicados ou inválidos são recusados antes do domínio. Contexto/MDC limpos em erro e entre threads.
- [x] POST/MVC real -> JPA aceite/comando/evento -> comando JMS conserva correlação; reenvio com outro header repete mesmo boleto e preserva intenção original.
- [x] Comando JMS -> escopo de registro -> resultado/intenção -> evento JMS preserva correlação. Evento -> escopo de invalidação; contexto limpo depois da entrega. Header JMS divergente do envelope vai para quarentena sem efeito de negócio/cache.
- [x] Correlação excluída da identidade de negócio: mudar apenas o metadado não duplica resultado, ledger ou evento; JSON legado sem campo continua idempotente.
- [x] GETs internos encaminham a mesma correlação às duas fontes no fallback. Erro 503 da consulta tem o mesmo id no header/corpo; cache hit usa id do pedido atual.
- [x] `mvn -B clean verify`: **BUILD SUCCESS, 265 testes**, zero falhas/erros/ignorados: **19 infraestrutura-http, 12 broker, 17 eventos-outbox, 97 emissor, 69 registro, 50 consulta Surefire + 1 Failsafe**. PACT e regressão E1–E9 preservados.

Matriz, operação e limites na [E10](etapas/E10-resiliencia-e-correlacao.md) / [ADR-013](adr/ADR-013-resiliencia-e-correlacao.md). Não é nova prova de quatro JVMs nem auditoria/autorização completa. E10 tem commit próprio sem push; as próximas etapas devem seguir o mesmo padrão.

Na conclusão da E10, a próxima entrega planejada era E11. A revisão de escopo abaixo substitui essa sequência.

## Revisão após E10 — Prioridade ao CORE geral

- [x] Decisão explícita do solicitante em 20/10/2026: priorizar CORE geral, manter funcionalidades E0–E10 e registrar Sessão 5 como não atendida integralmente.
- [x] E11 **não implementada**, fora da entrega atual; sem usuários/papéis, autorização por convênio, auditoria persistida, TLS ou criptografia de armazenamento comprovados. Não foi verificada/ativada criptografia do Windows.
- [x] E12 revisada para preparação operacional local; replay restrito/auditado e retenção formal não atendidos. Não criar replay inseguro nem apresentar backup como política de retenção.
- [x] README/roteiro/autoavaliação/rastreabilidade atualizados; requisitos originais da Sessão 5 preservados e lacunas explícitas na [ADR-014](adr/ADR-014-prioridade-core-e-limites-de-escopo.md).
- [ ] E12 revisada: runbook e verificação de cópia/restauração local com dados sintéticos, sem apagar bancos anteriores.
- [ ] E14: jornadas integradas, build/contratos, cache/falhas/reinício e reprodução do perfil local; registrar controles excluídos como não atendidos, não testes aprovados.
- [ ] E15: fechamento somente depois, com autoavaliação e limitações reais.

Revisão somente documental, sem alteração de código nem nova execução funcional; permanecem as 265 aprovações registradas na E10 (`0ae4139`). Não há dispensa docente ou garantia de nota. Não expor serviços em rede nem usar dados reais.
