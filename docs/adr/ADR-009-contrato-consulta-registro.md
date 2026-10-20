# ADR-009 — Contrato executavel entre consulta e registro

## Contexto

O CORE exige um contrato executavel entre dois servicos. Compartilhar o record `BoletoConsulta` nao prova compatibilidade HTTP: caminho, status, nomes snake_case, datas e campos condicionais podem divergir.

O registro vem antes da consulta no reactor Maven. Verificar um arquivo em `boleto-consulta/target` diretamente dependeria de ordem de execucao ou de residuos de builds anteriores. O grupo precisa executar sem Docker, sem Pact Broker e sem enviar contratos a servicos externos.

## Decisao

- Usar Pact JVM 4.6.14 em escopo de teste, com especificacao V3, no par consumidor `boleto-consulta` / provedor `boleto-registro`.
- O consumidor exercita `RestBoletoFonteClient` real contra servidor PACT local em porta aleatoria. O ObjectMapper vem do contexto JSON do Spring, incluindo snake_case e Java Time.
- O provedor inicia a aplicacao real em porta aleatoria, com H2 em memoria exclusivo e estados preparados pelo repository, sem mockar controller ou service.
- Versionar um unico contrato canonico em [shared-contracts/pacts](../../shared-contracts/pacts/boleto-consulta-boleto-registro.json). Maven o copia apenas para os recursos de teste dos dois modulos; nao entra nos JARs de producao.
- O consumidor gera seu pact em `boleto-consulta/target/pacts` no Surefire. Depois, `PactArtifactIT` executa no Failsafe e compara o arquivo gerado com o canonico usado pelo provedor.
- A comparacao preserva requests, responses, matchers, estados, nomes e versao da especificacao. Ignora apenas metadados da ferramenta e ordem das interacoes. Arquivo ausente ou diferenca de contrato falha o build.
- `mvn clean verify` e o aceite completo: verificacao do provedor + geracao pelo consumidor + comparacao do artefato. Nao basta executar apenas `mvn test`.
- Telemetria PACT desabilitada nos processos de teste; publicacao de resultados do provedor desabilitada. Nenhum Pact Broker e necessario.

## Escopo do contrato E6

GET interno por UUID: `REGISTRADO` 200, `REJEITADO` 200 e inexistente 404. Inclui os campos snake_case e valores nulos condicionais.

Na entrega inicial E6, o contrato retratava a implementacao E5: linha digitavel/codigo de barras ainda nulos no registrado. **Na E7, consumidor, canonico e provedor foram atualizados juntos** para os marcadores `NAO-PAGAVEL-SIM-LINHA-{uuid}` / `NAO-PAGAVEL-SIM-BARRAS-{uuid}`. O rejeitado permanece sem dados de pagamento. `AGUARDANDO_REGISTRO` vem do fallback emissor, fora deste par. Detalhes: [ADR-010](ADR-010-contrato-funcional-e-idempotencia-registradora.md).

## Alternativas consideradas

- **DTO compartilhado ou mock unitario:** insuficiente para verificar a fronteira HTTP real.
- **Provedor lendo somente `target` do consumidor:** arquivo efemero; exige acoplar a ordem/artefatos de build entre os modulos.
- **Copias de pact independentes em cada modulo:** risco de consumidor e provedor passarem com contratos diferentes.
- **Pact Broker externo:** desnecessario para este grupo/ambiente; exigiria operacao e configuracao adicionais.

## Consequencias

Mudancas de API exigem atualizacao deliberada do canonico e revisao dos testes. Build limpo e verde impede divergencia silenciosa entre a geracao e o arquivo verificado, independentemente da ordem entre registro e consulta.

Essa verificacao nao substitui autorizacao, testes de carga, eventos, mensageria ou jornadas completas dos tres processos; essas entregas permanecem nas etapas seguintes.

## Status

**Implementada e validada em 20/10/2026.** A quebra controlada de 404 para 200 falhou no provedor e na comparacao do artefato; restaurado o contrato, o build limpo passou com 60 testes. Evidencias: [E6](../etapas/E6-contratos-pact.md).
