# Central de Cobrança e Boletos

Projeto final em grupo — BE-JV-010 (Nível III). Escopo: **CORE**.

## Comece por aqui

Para entender o projeto sem ter acompanhado sua construção, leia o [guia didático do projeto](docs/guia-do-projeto.md): problema, conceitos, fluxo completo, função das classes, tecnologias, dados, testes e perguntas para a defesa. O guia distingue implementação, simulação e pendências; este README mantém os comandos de execução.

Para experimentar visualmente, use o **[laboratório web do fluxo real](docs/painel-demonstracao.md)**: emissão, idempotência/409, registro/rejeição, eventos, cache e retry/quarentena na tela. É opcional e local, solicitado para estudo; não adiciona obrigação à rubrica. O perfil `demonstracao` deve estar ativo nos três serviços; após iniciá-los conforme o guia, abra **http://127.0.0.1:8083/demonstracao**. Não há animações presumindo sucesso.

## Situação e próximos passos

E0–E10 implementadas; a base técnica dos critérios tem evidência. Em 20/10/2026, o usuário informou a orientação do professor de **seguir apenas os critérios de avaliação**. O [novo roteiro](ROTEIRO.md) contém somente três etapas restantes: **F1 — conferir a demonstração final; F2 — concluir documentos/autoavaliação; F3 — fechar a entrega e preparar a defesa**. Não há nova funcionalidade obrigatória planejada; o painel é apoio didático solicitado à parte.

**O fechamento continua por último, na F3.** E11/E12 e carga/backup completo não são mais portões de conclusão. Os requisitos originais do tema permanecem registrados, mas os controles da Sessão 5 e replay auditado **não foram implementados nem serão apresentados como feitos**. A orientação docente relatada define o escopo, não garante nota nem conformidade institucional: [ADR-014](docs/adr/ADR-014-prioridade-core-e-limites-de-escopo.md). Grupo com **três integrantes**, sem opcionais adicionais obrigatórios.

## Problema
Empresas emitem cobranças e não sabem se foram apenas recebidas, registradas ou rejeitadas. A Central **aceita** a emissão de forma assíncrona (202), **registra** o título numa registradora simulada por outbox e fila durável Artemis e permite **consultar** o estado com cache. Não movimenta dinheiro. Detalhes: [`requisitos/`](requisitos/README.md).

## Arquitetura (resumo)
3 serviços com bases segregadas + `shared-contracts` + infraestrutura reutilizável `eventos-outbox`/`infraestrutura-http` + broker JVM em processo próprio. Ver [`docs/arquitetura.md`](docs/arquitetura.md) e [`docs/adr/`](docs/adr/).

## Onde encontrar as classes

Nos três serviços, o código está separado por tipo de classe dentro de `src/main/java/br/com/caixa/cobranca/{emissor,registro,consulta}`: `controllers`, `services`, `models`, `repositories`, `dtos`, `clients`, `publishers`, `listeners`, `configs`, `validators`, `exceptions` e `handlers`, somente onde houver classes desse tipo. A classe `*Application` permanece no pacote raiz do serviço. Guia e exemplos: [organização dos pacotes](docs/arquitetura.md#organização-dos-pacotes-java).

Para estudar: comece pelo controller, siga para o service e depois para repository/model ou client/publisher. A consulta não tem entidades/repositórios; seus clientes HTTP ficam em `clients`. Contratos entre serviços continuam em `shared-contracts`, sem duplicação nos serviços.

## Perfil de execução
**Plano B (pura-JVM, sem Docker)** — definitivo para a entrega. Comandos e eventos usam **Apache ActiveMQ Artemis 2.33.0**, Jakarta JMS e armazenamento NIO. E9 adiciona pub/sub durável, outbox nos dois produtores e invalidação de cache. Não há fallback HTTP automático. Ver [ADR-011](docs/adr/ADR-011-broker-jvm-artemis.md), [operação/migração E8](docs/etapas/E8-broker-jvm.md) e [E9](docs/etapas/E9-eventos-e-cache.md). Perfis A/C no Maven são referências conceituais, não infraestruturas operacionais.

## Como rodar
Requer JDK 17 e Maven, na rede com acesso ao Nexus corporativo. Na raiz:
```powershell
mvn -B clean verify
```
Abra **quatro terminais inicialmente na raiz do projeto**:

Terminal 1 — broker, porta 61616:
```powershell
Set-Location .\cobranca-broker
java -jar .\target\cobranca-broker-0.0.1-SNAPSHOT-exec.jar
```
Terminal 2 — registro:
```powershell
Set-Location .\boleto-registro
java -jar .\target\boleto-registro-0.0.1-SNAPSHOT.jar --server.address=127.0.0.1
```
Terminal 3 — emissor:
```powershell
Set-Location .\boleto-emissor
java -jar .\target\boleto-emissor-0.0.1-SNAPSHOT.jar --server.address=127.0.0.1
```
Terminal 4 — consulta:
```powershell
Set-Location .\boleto-consulta
java -jar .\target\boleto-consulta-0.0.1-SNAPSHOT.jar --server.address=127.0.0.1
```

Espere **“Broker pronto”**; confirme os serviços com `curl.exe -i http://127.0.0.1:8081/actuator/health` (repita nas portas 8082 e 8083) → HTTP 200 e `{"status":"UP"}`. Health não prova que uma emissão já terminou; consulte seu UUID até o estado final.

Se 61616 estiver ocupada, use `--port=61617` no comando do broker e configure `$env:BROKER_URL='tcp://127.0.0.1:61617?blockOnDurableSend=true&callTimeout=2000&initialConnectAttempts=1&reconnectAttempts=0'` nos **três** terminais de emissor/registro/consulta, antes de iniciá-los. Essa foi a porta usada na verificação E8 desta máquina; nenhum processo alheio foi encerrado.

O broker grava journal/log em `data\broker`; emissor/registro gravam H2 em `data` dos próprios módulos quando iniciados como acima. Caminhos são relativos ao diretório de execução. Pare os serviços e depois o broker com **Ctrl+C**; preserve os diretórios ao reiniciar. Parâmetros, falhas, migração de pendências HTTP e limites: [E8](docs/etapas/E8-broker-jvm.md).

Para preferir `mvn -pl ... spring-boot:run` nos serviços, execute antes `mvn -B clean install`, instalando pai e dependências locais. Os JARs acima não precisam desse passo adicional.

## Contratos executáveis (E6)

`mvn -B clean verify` executa os testes e o PACT **consulta ↔ registro**, sem Docker ou serviços previamente iniciados. Os testes iniciam brokers reais isolados com journal temporário; nenhum teste depende de broker externo. O consumidor usa o cliente REST real; o provedor sobe HTTP real com H2 em memória; uma comparação no Failsafe impede que o pact gerado divirja do [contrato canônico](shared-contracts/pacts/boleto-consulta-boleto-registro.json). Apenas `mvn test` não executa essa comparação final. Último build limpo, após o painel: **277 testes aprovados**, incluindo PACT; [validações atuais](docs/validacao-pendente.md#painel-web-didático--21102026). Histórico: [E6](docs/etapas/E6-contratos-pact.md), [E7](docs/etapas/E7-contrato-funcional.md), [E8](docs/etapas/E8-broker-jvm.md), [E9](docs/etapas/E9-eventos-e-cache.md) e [E10](docs/etapas/E10-resiliencia-e-correlacao.md).

## Contrato funcional e dados de demonstração (E7)

Novos aprovados têm `linha_digitavel=NAO-PAGAVEL-SIM-LINHA-{uuid}` e `codigo_barras=NAO-PAGAVEL-SIM-BARRAS-{uuid}`. São **marcadores didáticos, não dados bancários válidos e nunca devem ser usados para pagamento**. Rejeitados/transitórios não expõem esses dados.

O registro valida a solicitação antes de chamar a registradora e grava histórico mínimo de novos resultados. A registradora simulada mantém um ledger por UUID em transação independente para recuperar a mesma resposta após perda de resposta/rollback local. Finais antigos continuam como originalmente persistidos, sem reprocessamento ou histórico retroativo inventado. Matriz, garantias e limites: [E7](docs/etapas/E7-contrato-funcional.md) / [ADR-010](docs/adr/ADR-010-contrato-funcional-e-idempotencia-registradora.md).

## Resiliência de comandos (E5 → E8)
A outbox mantém **três tentativas totais de publicação**, com esperas mínimas de 1 s e 5 s; esgotamento preserva o comando no H2, sem reenvio automático. `GET http://localhost:8081/interno/outbox/falhas` lista até 20 isolamentos do **produtor**, somente com metadados; não lista a DLQ do broker.

No registro, falha temporária reverte a transação JMS: o broker faz até **três entregas totais**, com esperas mínimas de 1 s e 5 s, e encaminha o esgotamento para `registro.quarentena`. JSON/envelope/conflito inválido vai imediatamente à quarentena, atomicamente com o ack original. Não há sobreposição de retry de negócio no produtor: publicar no broker e processar no registro são fronteiras distintas.

Rejeição de negócio é persistida como `REJEITADO` e confirma o processamento; não é falha de transporte. No serviço de registro, a variável `REGISTRADORA_CENARIO` permite simular `APROVAR` (padrão), `REJEITAR` ou `INDISPONIVEL`. Histórico HTTP: [E5](docs/etapas/E5-resiliencia-outbox.md); regras vigentes: [E8](docs/etapas/E8-broker-jvm.md).

Há DLQ Artemis, não RabbitMQ. O modelo permite reentregas e exige idempotência; o limite de retry não garante registro eventual durante falhas prolongadas. **Não há endpoint de replay**; reprocessamento restrito/auditado ficou fora da entrega atual, não será apresentado como feito na E12. Broker sem autenticação/TLS; APIs sem autorização por convênio. **Uso exclusivamente local, com dados sintéticos e serviços em loopback; não expor em rede nem usar dados reais.** Criptografia de armazenamento não foi verificada/ativada. [Limites de escopo](docs/adr/ADR-014-prioridade-core-e-limites-de-escopo.md).

## Documentação do processo
- [`ROTEIRO.md`](ROTEIRO.md) — somente pendências F1–F3, substituindo o plano E11–E15; fechamento por último
- [`docs/etapas/`](docs/etapas/) — o que foi feito e o que aprendemos em cada etapa (inclui [E2](docs/etapas/E2-idempotencia.md), [E3](docs/etapas/E3-consulta-e-cache.md), [E4](docs/etapas/E4-comunicacao-assincrona.md) e [E5](docs/etapas/E5-resiliencia-outbox.md))
- [`docs/validacao-pendente.md`](docs/validacao-pendente.md) — resultados e testes manuais pendentes
- [`AVALIACAO.md`](AVALIACAO.md) — auto-avaliação por critério
- [E6 — contratos PACT](docs/etapas/E6-contratos-pact.md) — execução, atualização do artefato e evidências positivas/negativas
- [E7 — contrato funcional](docs/etapas/E7-contrato-funcional.md) — matriz de campos, dados não pagáveis, histórico e recuperação idempotente
- [E8 — broker JVM](docs/etapas/E8-broker-jvm.md) — execução, migração, retry/quarentena e evidências em processos separados
- [E9 — eventos e cache](docs/etapas/E9-eventos-e-cache.md) — três eventos mínimos, outbox local atômica, assinaturas independentes e invalidação coordenada com leitura
- [E10 — resiliência e correlação](docs/etapas/E10-resiliencia-e-correlacao.md) — limites por fronteira, circuitos independentes, header validado e propagação persistida

## Eventos e cache (E9)

Emissor grava `EmissaoAceita` junto ao aceite/comando; registro grava `BoletoRegistrado` ou `RegistroRejeitado` junto ao resultado/histórico. Publicadores independentes confirmam no broker antes de concluir cada intenção. Eventos finais na assinatura durável da consulta invalidam o cache antes do ack, inclusive diante de preenchimento concorrente; TTL continua como proteção. Duplicatas e eventos fora de ordem não projetam nem rebaixam estados.

**Uma instância de cada serviço na demonstração.** Outras instâncias de consulta com cache local precisam de assinatura durável exclusiva, não de consumidores concorrentes na mesma fila. Envelope mínimo sem dados pessoais; E10 propaga correlação HTTP validada ao comando/eventos. Não há backfill de eventos legados nem replay automático de isolados. Retenção/proteção da Sessão 5 não atendidas na entrega atual: [ADR-014](docs/adr/ADR-014-prioridade-core-e-limites-de-escopo.md). Detalhes dos eventos e testes: [E9](docs/etapas/E9-eventos-e-cache.md).

## Resiliência e correlação (E10)

GETs internos da consulta têm limites explícitos de conexão **2 s** e leitura **3 s**, sem redirect automático ou retry de aplicação. Cada fonte tem seu circuito Resilience4j: três falhas técnicas nas últimas três chamadas abrem por **10 s**; depois há uma única sonda. Sucesso/404 válido fecha, falha reabre. Circuito aberto retorna 503, nunca 404 nem registro fictício; fallback ao emissor continua exclusivo de 404 do registro. Cache hit não chama a fonte.

Os três serviços aceitam `X-Correlation-ID` opcional: ausente gera UUID v4; informado exige UUID v4 canônico em minúsculas, variante 2, sem espaços nem headers duplicados. Inválido retorna 400. O header de resposta é preservado inclusive nos erros/cache hit. O mesmo id atravessa comando/outbox/JMS, registro/eventos e chamadas HTTP internas; ele não integra a chave de idempotência. Consultas/reenvios têm seu id de requisição, sem reescrever a correlação da intenção original.

Não há novo processo ou variável obrigatória. Na consulta, `integracoes.connect-timeout-ms` e `integracoes.read-timeout-ms` aceitam apenas valores positivos. Limites de socket não são deadline global nem prova de p95; registradora continua simulada/local. Inventário, exemplo de header, compatibilidade com comandos antigos e evidências: [E10](docs/etapas/E10-resiliencia-e-correlacao.md) / [ADR-013](docs/adr/ADR-013-resiliencia-e-correlacao.md).

## Uso de IA
Ver `AVALIACAO.md` §8 e `docs/etapas/`.
