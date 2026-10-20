# Central de Cobrança e Boletos

Projeto final em grupo — BE-JV-010 (Nível III). Escopo: **CORE**.

## Situação e próximos passos

E0–E9 implementadas; **o trabalho ainda não está concluído**. O [roteiro final](ROTEIRO.md) faz o de/para de todas as aulas, do exemplo PIX e da rubrica, e planeja até a E15. Próxima implementação: **E10 — resiliência e correlação ponta a ponta**. Depois vêm acesso/auditoria e recuperação operacional.

O **fechamento ficou para E15**, somente após os testes de aceite integrado da E14 e a conclusão dos requisitos aplicáveis. A [rastreabilidade das histórias](requisitos/user-stories.md) e a [autoavaliação](AVALIACAO.md) distinguem o que já tem evidência do que ainda está planejado. Eventos, auditoria, proteção e retenção fazem parte do tema. O grupo tem **três integrantes**, sem opcionais adicionais obrigatórios; E13 não se aplica.

## Problema
Empresas emitem cobranças e não sabem se foram apenas recebidas, registradas ou rejeitadas. A Central **aceita** a emissão de forma assíncrona (202), **registra** o título numa registradora simulada por outbox e fila durável Artemis e permite **consultar** o estado com cache. Não movimenta dinheiro. Detalhes: [`requisitos/`](requisitos/README.md).

## Arquitetura (resumo)
3 serviços com bases segregadas + `shared-contracts` + infraestrutura reutilizável `eventos-outbox` + broker JVM em processo próprio. Ver [`docs/arquitetura.md`](docs/arquitetura.md) e [`docs/adr/`](docs/adr/).

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

`mvn -B clean verify` executa os testes e o PACT **consulta ↔ registro**, sem Docker ou serviços previamente iniciados. Os testes E8/E9 iniciam brokers reais isolados com journal temporário; nenhum teste depende de broker externo. O consumidor usa o cliente REST real; o provedor sobe HTTP real com H2 em memória; uma comparação no Failsafe impede que o pact gerado divirja do [contrato canônico](shared-contracts/pacts/boleto-consulta-boleto-registro.json). Apenas `mvn test` não executa essa comparação final. Último build limpo E9: **214 testes aprovados**, incluindo PACT. Histórico: [E6](docs/etapas/E6-contratos-pact.md), [E7](docs/etapas/E7-contrato-funcional.md), [E8](docs/etapas/E8-broker-jvm.md) e [E9](docs/etapas/E9-eventos-e-cache.md).

## Contrato funcional e dados de demonstração (E7)

Novos aprovados têm `linha_digitavel=NAO-PAGAVEL-SIM-LINHA-{uuid}` e `codigo_barras=NAO-PAGAVEL-SIM-BARRAS-{uuid}`. São **marcadores didáticos, não dados bancários válidos e nunca devem ser usados para pagamento**. Rejeitados/transitórios não expõem esses dados.

O registro valida a solicitação antes de chamar a registradora e grava histórico mínimo de novos resultados. A registradora simulada mantém um ledger por UUID em transação independente para recuperar a mesma resposta após perda de resposta/rollback local. Finais antigos continuam como originalmente persistidos, sem reprocessamento ou histórico retroativo inventado. Matriz, garantias e limites: [E7](docs/etapas/E7-contrato-funcional.md) / [ADR-010](docs/adr/ADR-010-contrato-funcional-e-idempotencia-registradora.md).

## Resiliência de comandos (E5 → E8)
A outbox mantém **três tentativas totais de publicação**, com esperas mínimas de 1 s e 5 s; esgotamento preserva o comando no H2, sem reenvio automático. `GET http://localhost:8081/interno/outbox/falhas` lista até 20 isolamentos do **produtor**, somente com metadados; não lista a DLQ do broker.

No registro, falha temporária reverte a transação JMS: o broker faz até **três entregas totais**, com esperas mínimas de 1 s e 5 s, e encaminha o esgotamento para `registro.quarentena`. JSON/envelope/conflito inválido vai imediatamente à quarentena, atomicamente com o ack original. Não há sobreposição de retry de negócio no produtor: publicar no broker e processar no registro são fronteiras distintas.

Rejeição de negócio é persistida como `REJEITADO` e confirma o processamento; não é falha de transporte. No serviço de registro, a variável `REGISTRADORA_CENARIO` permite simular `APROVAR` (padrão), `REJEITAR` ou `INDISPONIVEL`. Histórico HTTP: [E5](docs/etapas/E5-resiliencia-outbox.md); regras vigentes: [E8](docs/etapas/E8-broker-jvm.md).

Há DLQ Artemis, não RabbitMQ. O modelo permite reentregas e exige idempotência; o limite de retry não garante registro eventual durante falhas prolongadas. Não há endpoint de replay: comandos isolados/quarentenados precisam de análise antes de reprocessamento seguro na E12. Broker somente em loopback, sem autenticação nesta etapa; endpoints `/interno/...` devem ficar em rede confiável. Não expor fora da máquina antes da E11.

## Documentação do processo
- [`ROTEIRO.md`](ROTEIRO.md) — histórico E0–E9, de/para completo e etapas até E15; fechamento por último
- [`docs/etapas/`](docs/etapas/) — o que foi feito e o que aprendemos em cada etapa (inclui [E2](docs/etapas/E2-idempotencia.md), [E3](docs/etapas/E3-consulta-e-cache.md), [E4](docs/etapas/E4-comunicacao-assincrona.md) e [E5](docs/etapas/E5-resiliencia-outbox.md))
- [`docs/validacao-pendente.md`](docs/validacao-pendente.md) — resultados e testes manuais pendentes
- [`AVALIACAO.md`](AVALIACAO.md) — auto-avaliação por critério
- [E6 — contratos PACT](docs/etapas/E6-contratos-pact.md) — execução, atualização do artefato e evidências positivas/negativas
- [E7 — contrato funcional](docs/etapas/E7-contrato-funcional.md) — matriz de campos, dados não pagáveis, histórico e recuperação idempotente
- [E8 — broker JVM](docs/etapas/E8-broker-jvm.md) — execução, migração, retry/quarentena e evidências em processos separados
- [E9 — eventos e cache](docs/etapas/E9-eventos-e-cache.md) — três eventos mínimos, outbox local atômica, assinaturas independentes e invalidação coordenada com leitura

## Eventos e cache (E9)

Emissor grava `EmissaoAceita` junto ao aceite/comando; registro grava `BoletoRegistrado` ou `RegistroRejeitado` junto ao resultado/histórico. Publicadores independentes confirmam no broker antes de concluir cada intenção. Eventos finais na assinatura durável da consulta invalidam o cache antes do ack, inclusive diante de preenchimento concorrente; TTL continua como proteção. Duplicatas e eventos fora de ordem não projetam nem rebaixam estados.

**Uma instância de cada serviço na demonstração.** Outras instâncias de consulta com cache local precisam de assinatura durável exclusiva, não de consumidores concorrentes na mesma fila. Envelope mínimo sem dados pessoais; correlação HTTP completa ainda E10. Não há backfill de eventos legados nem replay automático de isolados. Retenção/proteção continuam E11/E12. Detalhes e testes: [E9](docs/etapas/E9-eventos-e-cache.md).

## Uso de IA
Ver `AVALIACAO.md` §8 e `docs/etapas/`.
