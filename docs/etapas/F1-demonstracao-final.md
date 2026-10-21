# F1 — Conferência da demonstração final

**Data:** 21/10/2026. **Versão de código conferida:** `96b8075`.

**Objetivo:** comprovar a execução da versão final e selecionar uma sequência curta para a banca, sem criar funcionalidades, ensaio de carga ou novas obrigações.

As verificações abaixo foram executadas **pelo assistente**, com dados sintéticos. Não são um ensaio nem validação pessoal dos três integrantes. Nomes, papéis e reflexão humana sobre IA continuam na F2; entrega e defesa, na F3. Não há garantia de nota.

## 1. Preparação e comandos

O [README](../../README.md#como-rodar) descreve a execução normal. Para a demonstração visual, seguir os quatro terminais do [laboratório web](../painel-demonstracao.md#1-iniciar):

1. Iniciar broker JVM em loopback, porta **61617**, com journal próprio.
2. Configurar o mesmo `BROKER_URL` nos três serviços e iniciar **registro, emissor e consulta**, todos no perfil `demonstracao`.
3. Esperar “Broker pronto” e HTTP `200`/`UP` nos três healths.
4. Abrir **http://127.0.0.1:8083/demonstracao**.

Comandos de conferência em outro terminal:

```powershell
curl.exe -i http://127.0.0.1:8081/actuator/health
curl.exe -i http://127.0.0.1:8082/actuator/health
curl.exe -i http://127.0.0.1:8083/actuator/health
```

Nesta execução foram usadas as portas HTTP documentadas **8081/8082/8083**, broker 61617 e quatro JARs reais, sem Docker. Para preservar os bancos do grupo, apenas os caminhos de dados foram substituídos por diretórios temporários exclusivos da sessão:

```powershell
# No comando do broker: substituir --data pelo diretorio temporario exclusivo.
# No comando de cada produtor, acrescentar a URL de seu proprio banco:
'--spring.datasource.url=jdbc:h2:file:C:\caminho-exclusivo-f1\emissor;DB_CLOSE_ON_EXIT=FALSE'
'--spring.datasource.url=jdbc:h2:file:C:\caminho-exclusivo-f1\registro;DB_CLOSE_ON_EXIT=FALSE'
```

Os caminhos acima são exemplos para quem também precisar isolar dados, não comandos avulsos que iniciam serviços. Na demonstração do grupo, os caminhos já definidos no guia podem ser mantidos. Não apagar bases/journal para repetir cenários: gerar novo controle.

## 2. Resultados observados

| Verificação | Resultado e evidência da execução |
|---|---|
| Prontidão | “Broker pronto” em 61617; os três `GET /actuator/health` retornaram `200`/`UP` |
| Aprovação | POST `202`, depois consulta `200`/`REGISTRADO`; UUID `e6c383a5-d16f-4d65-a508-49fa66f67ad2` |
| Idempotência | Repetição retornou `202` com o mesmo UUID; diagnóstico contou **um** `ACEITE_PERSISTIDO`, **um** `COMANDO_PUBLICADO` e **um** `RESULTADO_PERSISTIDO`, além de `IDEMPOTENCIA` |
| Conflito | Mesmo convênio/controle com valor diferente retornou `409`, gerou `CONFLITO` e preservou o resultado anterior |
| Cache na transição | Para o UUID aprovado, houve preenchimento `AGUARDANDO_REGISTRO`, evento final, `CACHE_INVALIDADO`, novo preenchimento `REGISTRADO` e `CACHE_HIT` posterior |
| Rejeição | UUID `2536edb5-baf0-4a68-b213-eb839b90daad`: `REJEITADO`, código `SIM-REJ-001`, sem mensagem desse UUID na quarentena |
| Retry e quarentena | UUID `bebb21fa-ea8b-441b-a39d-1b1063fa7f93`: entregas **1/2/3**, três falhas; intervalos observados de **1.032 ms e 5.012 ms** entre entregas, sem alterar o backoff |
| Consulta da quarentena | Duas observações sucessivas encontraram a mensagem do UUID de falha; browse não a consumiu. Consulta do boleto continuou `AGUARDANDO_REGISTRO`, não virou rejeição |
| Ausência real | UUID inexistente `de30b815-18fa-4acf-90e9-86e75e0f09bf`: `404`, com fallback após o `404` da fonte de registro; ausência não é indisponibilidade |
| Indisponibilidade | Com a JVM de registro desligada, novo aceite `51c45d20-0a98-40a0-be1a-671e5b07847d`: consulta `503`, três `HTTP_FONTE_FALHOU`, depois `CIRCUITO_ABERTO`, **sem `FALLBACK`**; diagnóstico da fonte explicitamente indisponível |
| Recuperação | Registro reiniciado com o mesmo banco; health voltou a `UP`, consumiu comando pendente e a consulta do UUID anterior retornou `REGISTRADO`, com `HTTP_FONTE_OK` após recuperação do circuito |
| Persistência após reinício | O UUID aprovado `e6c383a5-d16f-4d65-a508-49fa66f67ad2` continuou `REGISTRADO` depois de reiniciar os processos com os mesmos diretórios |
| Encerramento | Todos os processos próprios foram encerrados pela sessão de execução; portas 8081/8082/8083/61617 liberadas, sem JVM de demonstração deixada ativa |

Os UUIDs identificam **esta execução isolada**, não dados disponíveis no computador de quem repetir a demonstração. Os resultados sintéticos de registro não são boletos pagáveis.

**Condição da parada automatizada:** a tentativa de enviar Ctrl+C às consoles ocultas do Windows não encerrou as JVMs neste ambiente. A automação encerrou apenas as árvores de processos que ela própria iniciou; não se apresenta isso como teste de shutdown gracioso. O reinício acima comprovou recuperação dos dados desta execução, não uma campanha de durabilidade contra falhas. Nos terminais interativos do grupo, manter a instrução **Ctrl+C nos serviços e depois no broker**, preservando bancos/journal. Nenhum processo alheio foi encerrado, nem foi criado endpoint de shutdown no sistema.

## 3. Evidências automatizadas selecionadas

O build completo do código conferido já havia passado com **277 testes**, incluindo PACT, na entrega do painel. Como a F1 não muda código/dependências/contratos, não foi aberta outra campanha completa. Foram reexecutadas as três classes diretamente relevantes para as evidências menos visíveis na tela:

```powershell
mvn -B -pl boleto-consulta -am test '-Dtest=ConsultaBoletoControllerTest,ConsultaEventosTest,ResilienciaFonteTest' '-Dsurefire.failIfNoSpecifiedTests=false'
```

**Resultado:** `BUILD SUCCESS`, **48 testes**, zero falhas/erros/ignorados.

| Evidência | Teste existente e o que realmente comprova |
|---|---|
| TTL | [cacheCaffeineAplicaExpiracaoVariavelPorEstado](../../boleto-consulta/src/test/java/br/com/caixa/cobranca/consulta/integration/ConsultaBoletoControllerTest.java): lê a política do cache real e exige restante de 29–30 s no transitório e 599–600 s no registrado. Não exige esperar dez minutos na banca |
| Regra de TTL por estado | [cacheUsaTtlCurtoParaEstadoTransitorioELongoParaEstadoFinal](../../boleto-consulta/src/test/java/br/com/caixa/cobranca/consulta/integration/ConsultaBoletoControllerTest.java): aguardando usa TTL curto; registrado e rejeitado usam longo |
| Corrida entre miss e evento | [missEmAndamentoNaoReinstalaTransitorioDepoisDoAckDoEvento](../../boleto-consulta/src/test/java/br/com/caixa/cobranca/consulta/integration/ConsultaEventosTest.java): bloqueia leitura em voo, entrega evento por broker real, libera leitura e verifica que o ack só ocorre depois da invalidação coordenada; cache fica vazio e a consulta seguinte obtém o final |
| Timeout de leitura | [timeoutDeLeituraRealTemLimiteENaoRepeteRequisicao](../../boleto-consulta/src/test/java/br/com/caixa/cobranca/consulta/clients/ResilienciaFonteTest.java): servidor HTTP real retém resposta; cliente com orçamento de teste de 150 ms lança `SocketTimeoutException`, termina em menos de 2 s e faz somente uma chamada |
| Circuito e recuperação | [ResilienciaFonteTest](../../boleto-consulta/src/test/java/br/com/caixa/cobranca/consulta/clients/ResilienciaFonteTest.java): três falhas, OPEN por 10 s, uma sonda half-open, reabertura/recuperação, circuitos independentes e nenhuma tentativa/fallback técnico extra |
| Ausência e negócio | A mesma classe verifica que `404` e rejeição de negócio contam como disponibilidade, não como falha técnica do circuito |

O orçamento reduzido do teste de timeout não altera os limites normais das fontes: **2 s conexão e 3 s leitura**. O proxy diagnóstico do painel tem limites próprios; não confundir os dois clientes.

## 4. Sequência curta para a banca

Reservar aproximadamente **6–8 minutos** para esta parte, adaptando ao tempo total da apresentação/arguição. Iniciar as JVMs e conferir health **antes** da fala, não gastar a apresentação compilando.

| Ordem | Ação | O que explicar/mostrar |
|---|---|---|
| 1 — contexto | Mostrar o fluxo no painel | “O emissor aceita; o registro processa pela fila; a consulta lê o estado. Não há pagamento real.” |
| 2 — caminho feliz | Selecionar **Aprovar registro** e emitir | POST `202` não é registro. Mostrar UUID, commit do aceite/outboxes, publicação JMS, consumo e resultado final `REGISTRADO` |
| 3 — consistência | **Repetir o mesmo pedido**, depois **Testar conflito 409** | Repetição mantém UUID; conteúdo diferente na mesma chave retorna `409`. Não foi criada outra operação de negócio |
| 4 — cache | **Consultar (ver cache)** | Mostrar hit, evento final e invalidação na linha do tempo. Se não capturar aguardando, não inventar a transição; apontar os testes de TTL/corrida acima |
| 5 — negócio | Selecionar **Rejeitar por regra de negócio** e emitir | `REJEITADO` com motivo não é `503`, não exige retry/DLQ |
| 6 — transporte | Selecionar **Falhar: retry e quarentena** e emitir | Mostrar três entregas, backoff, falhas e confirmação da mensagem na quarentena. O boleto pode continuar aguardando |
| 7 — resiliência | Apontar evidências de timeout/breaker/`404` × `503` | Mostrar os resultados registrados nesta F1 e os testes selecionados. Não é necessário interromper a fonte ao vivo para repetir toda a campanha |
| 8 — limites | Pausar acompanhamento | “Pausar a página não interrompe backend. Registradora é simulada, dados sintéticos, entrega at-least-once com idempotência; não exactly-once.” |

Para demonstrar indisponibilidade ao vivo, usar apenas o terminal do **próprio registro**: Ctrl+C, emitir um pedido novo, observar consultas `503` fora do cache e, após três falhas, `CIRCUITO_ABERTO`. Reiniciar com **o mesmo banco** e esperar health/sonda. Um boleto já em cache pode continuar retornando `200`, portanto não serve para comprovar indisponibilidade da fonte.

## 5. Limites e continuidade

- Health `UP` não comprova término do processamento.
- Consumo pode acontecer antes de a tela consultar; horários ordenados não são prova causal global nem confirmação do ack JMS.
- Os últimos 2.000 rastros por JVM são memória de diagnóstico, não auditoria. Reiniciar perde rastros, não o estado persistido.
- Browse limitado a 200 mensagens: ausência pode ser inconclusiva. Não há replay administrativo.
- Nenhuma medição de p95, carga, capacidade produtiva, conformidade, autenticação, TLS ou criptografia foi feita/exigida nesta F1.
- Não houve mudança de código ou correção funcional necessária. F2 e F3 permanecem abertas; a execução aqui não deve ser atribuída aos colegas.

Índice de [validações](../validacao-pendente.md), [roteiro vigente](../../ROTEIRO.md) e [autoavaliação](../../AVALIACAO.md).
