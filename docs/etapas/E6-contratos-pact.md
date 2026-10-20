# E6 — Contratos executaveis com PACT

## Conceito

O consumidor declara o que precisa receber por HTTP, e o provedor demonstra que consegue responder conforme esse acordo. Aqui, consulta e consumidor; registro e provedor. O servidor mock e usado somente no lado consumidor; o lado provedor usa o Spring Boot real e o H2.

## Implementacao

| Parte | Evidencia |
|---|---|
| Consumidor e cliente real | [RegistroContractTest](../../boleto-consulta/src/test/java/br/com/caixa/cobranca/consulta/RegistroContractTest.java) |
| Contrato unico versionado | [boleto-consulta-boleto-registro.json](../../shared-contracts/pacts/boleto-consulta-boleto-registro.json) |
| Provedor HTTP com H2 isolado | [RegistroProviderTest](../../boleto-registro/src/test/java/br/com/caixa/cobranca/registro/RegistroProviderTest.java) |
| Deteccao de pact desatualizado | [PactArtifactIT](../../boleto-consulta/src/test/java/br/com/caixa/cobranca/consulta/PactArtifactIT.java) |
| Decisao e alternativas | [ADR-009](../adr/ADR-009-contrato-consulta-registro.md) |

Tres interacoes:

1. Boleto registrado: HTTP 200, UUID, estado, instantes e identificador da registradora.
2. Boleto rejeitado: HTTP 200, codigo/motivo de rejeicao; campos de pagamento/registro nulos.
3. Boleto inexistente: HTTP 404, convertido pelo cliente em `BoletoNaoEncontradoException`, nao em sucesso ou falha tecnica.

O consumidor verifica a desserializacao dos nomes snake_case e todos os campos da projecao. Datas/UUIDs de exemplo sao fixos para reproduzir os estados. O contrato inclui os nulos do JSON atual; isso nao significa exigir a omissao das chaves.

**Limite intencional:** na E5, a aprovacao ainda nao preenche linha digitavel/codigo de barras. A E6 registra essa realidade sem inventar funcionalidade; completar dados didaticos nao pagaveis e atualizar o contrato e tarefa da E7. O fallback no emissor tambem nao e um segundo par PACT nesta etapa.

**Atualizacao E7:** esse complemento foi implementado e os dois lados/artefato foram atualizados juntos para marcadores nao pagaveis. Os resultados abaixo continuam sendo o historico da E6; estado atual em [E7](E7-contrato-funcional.md).

## Como executar

Na raiz do projeto, com JDK 17 e Maven:

```powershell
mvn -B clean verify
```

Nao precisa iniciar os servicos, Docker, broker de mensagens ou Pact Broker. Os servidores HTTP dos testes usam portas aleatorias, e o provedor usa `jdbc:h2:mem:registro-pact`, sem tocar nos bancos locais de demonstracao.

Para verificar apenas o par e sua comparacao, incluindo dependencias locais do reactor:

```powershell
mvn -B -pl boleto-registro,boleto-consulta -am '-Dtest=RegistroContractTest,RegistroProviderTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dit.test=PactArtifactIT' '-Dfailsafe.failIfNoSpecifiedTests=false' verify
```

Surefire gera `boleto-consulta/target/pacts/boleto-consulta-boleto-registro.json`. O canonico e copiado para os recursos de teste pelo Maven. Failsafe compara os dois no `verify`; **apenas `mvn test` nao executa essa ultima protecao**.

## Como atualizar o contrato

1. Alterar os cenarios do consumidor conforme o novo acordo e implementar a resposta do provedor.
2. Gerar novamente pelo consumidor, sem reutilizar interacoes antigas:

```powershell
mvn -B -pl boleto-consulta -am '-Dtest=RegistroContractTest' '-Dsurefire.failIfNoSpecifiedTests=false' clean test
```

3. Revisar o JSON gerado e atualizar o canonico em `shared-contracts/pacts` com esse artefato, sem duas copias mantidas independentemente.
4. Rodar `mvn -B clean verify`. Se o canonico ficar antigo, `PactArtifactIT` falha; se o provedor nao cumprir o canonico, `RegistroProviderTest` falha.

Nao remover o teste nem relaxar matchers apenas para obter build verde. Atualizar a ADR/requisitos quando a mudanca alterar o comportamento acordado.

## Evidencias executadas — 20/10/2026

- Geracao pelo consumidor: **3 testes aprovados**, usando o cliente REST real.
- Verificacao direcionada: **3 interacoes do provedor + 3 testes do consumidor + 1 comparacao aprovados**.
- Prova negativa: alterado temporariamente somente o status do inexistente no canonico, de 404 para 200.
- Com Maven `-fae`, o provedor falhou com `expected status of 200 but was 404`, e `PactArtifactIT` tambem falhou pela divergencia com o gerado. O consumidor continuou honrando seu contrato 404.
- Status restaurado para 404, seguido de **`mvn -B clean verify`: BUILD SUCCESS**.
- **60 testes aprovados:** 33 emissor; 15 registro (incluindo 3 interacoes PACT); 11 consulta no Surefire; 1 comparacao no Failsafe. Zero falhas, erros ou testes ignorados.

A prova negativa foi removida; o artefato persistido esta correto. Nao houve alteracao do codigo de producao, nem necessidade de processo de teste permanente.

## Uso de IA e limites

A IA implementou os testes/configuracao e executou a validacao. A escolha humana foi priorizar o CORE, manter Java 17 e nao depender de Docker. O risco de copiar um pact desatualizado foi tratado com comparacao automatica; a prova negativa verificou a falha em vez de confiar apenas no primeiro build verde.

O build pode emitir avisos de bibliotecas de teste (Commons Logging/JSONObject duplicado), do agente JVM e do PACT antes de terminar as tres interacoes. Eles nao impediram a verificacao completa; resultados nao sao publicados externamente. Isso nao e evidencia de mensageria, seguranca, carga ou jornada integrada da E14.

**Proxima etapa: E7 — completar o contrato funcional do dominio. Fechamento somente na E15.**
