# E7 — Contrato funcional, historico e registradora idempotente

## O que foi entregue

- Mesmo validador estrutural usado pelo emissor e pelo registro, sem dependencia entre seus bancos/servicos.
- Comando invalido e rejeitado antes de chamar a registradora; envelope exige UUID v4, identificadores iguais, estado/instante do aceite e solicitacao completa.
- Aprovacao persiste marcadores de pagamento explicitamente **nao pagaveis**; rejeicao nao expoe esses campos.
- Historico de origem e transicao final persistido junto com o novo resultado local, sem duplicar em reentrega.
- Ledger duravel da simulacao externa em transacao independente; resposta perdida e falha local podem ser recuperadas com o mesmo UUID, sem repetir efeito.
- PACT E6 atualizado e verificado contra esses novos dados.

Decisao e alternativas: [ADR-010](../adr/ADR-010-contrato-funcional-e-idempotencia-registradora.md).

## Matriz de validacao acordada

Fonte: [Sessao 2](../../requisitos/sessao-2-emissao.md). O validador esta em [SolicitacaoBoletoValidator](../../shared-contracts/src/main/java/br/com/caixa/cobranca/contracts/SolicitacaoBoletoValidator.java).

| Campo/regra | Validacao vigente | Evidencia |
|---|---|---|
| Corpo | Obrigatorio | `EmissaoValidatorTest.corpoNuloGeraErro` |
| Convenio | String com exatamente 6 digitos; preserva zeros a esquerda | `EmissaoRegrasTest`: ausente, 5/7 digitos, letras e `000001` |
| Numero de controle | Nao vazio; ate 25 caracteres | `EmissaoRegrasTest`: ausente, 26 invalido e 25 valido |
| Beneficiario e pagador | Pessoa, nome e tipo de documento obrigatorios | `EmissaoRegrasTest`: campos nulos e nome branco |
| Numero de documento | Somente digitos; CPF 11, CNPJ 14 | `EmissaoRegrasTest` / `EmissaoValidatorTest`; nao calcula digitos verificadores |
| Endereco de ambos | Logradouro/cidade nao vazios; UF 2 letras maiusculas; CEP 8 digitos | `EmissaoRegrasTest`: ausencias e formatos |
| Valor nominal | Maior que zero, ate 2 casas, sem arredondar entrada invalida | `EmissaoRegrasTest`: zero, negativo, terceira casa e 0.01 valido |
| Emissao/vencimento | Datas obrigatorias; vencimento igual ou posterior a emissao | `EmissaoRegrasTest` / `EmissaoValidatorTest` |
| Especie | DM ou DS, lista didatica ainda sujeita ao contrato real | `EmissaoValidatorTest` / `EmissaoRegrasTest` |
| Juros/multa | Opcionais; se presentes, tipo FIXO/PERCENTUAL, valor positivo e data inicial obrigatorios | `EmissaoRegrasTest`: cada complemento ausente e valor negativo |
| Desconto | Opcional; se presente, valor positivo e data limite obrigatorios | `EmissaoRegrasTest`: cada complemento ausente e valor negativo |
| Abatimento | Opcional; se presente, valor positivo | `EmissaoRegrasTest` |
| Instrucoes/mensagem | Opcionais; nenhum limite numerico aprovado nas sessoes | Preservados; nao inventamos um limite para a expressao original “texto curto” |
| Erros de entrada | 400 acumula campos/motivos, sem valores pessoais; JSON/tipo de enum/data invalido segue handler existente | `BoletoControllerTest`, `EmissaoValidatorTest` |
| Idempotencia de emissao | Convenio + controle; mesmo conteudo retorna aceite original, conflito 409; aceite + outbox atomicos | `BoletoControllerTest` |
| Fronteira de registro | Envelope consistente + mesma validacao estrutural; erro 400 nao chama registradora nem grava resultado | `DominioRegistroTest`: 19 campos ausentes e UUID fora do contrato; `RegistroComandoControllerTest` |

Nao foram adicionados limites maximos de valor, calendario bancario, verificacao cadastral de CPF/CNPJ, lista oficial de UF, comparacao desconto/valor ou limites de caracteres nao acordados. Isso evita apresentar regras inventadas como negocio aprovado. Precisao/limites adicionais dependem do contrato real.

## Dados retornados por estado

| Estado | Campos preenchidos | Campos de pagamento/rejeicao |
|---|---|---|
| AGUARDANDO_REGISTRO | UUID, estado, recebimento persistido no emissor | Sem dados de pagamento ou rejeicao |
| REGISTRADO novo E7 | UUID, estado, recebimento, `SIM-{uuid}`, instante externo | `linha_digitavel=NAO-PAGAVEL-SIM-LINHA-{uuid}`, `codigo_barras=NAO-PAGAVEL-SIM-BARRAS-{uuid}`; sem rejeicao |
| REJEITADO novo E7 | UUID, estado, recebimento, `SIM-REJ-001` e motivo | Sem linha/barras/id da registradora/data de registro |

As strings contem letras e avisos de simulacao de proposito: **nao sao documentos bancarios, nao devem ser escaneadas/convertidas em codigo de barras nem usadas para pagamento**. Nao ha conexao com instituicao bancaria.

Os campos percorrem resultado da simulacao → entidade JPA → GET interno → cliente REST/projecao publica. O [contrato canonico](../../shared-contracts/pacts/boleto-consulta-boleto-registro.json), os testes consumidor/provedor e o teste do controller publico foram atualizados juntos.

## Historico e recuperacao

1. Emissor grava aceite original e outbox na mesma transacao antes de 202. O aceite e o fato inicial persistido mesmo durante indisponibilidade do registro.
2. A simulacao externa grava uma operacao por UUID em `registradora_simulada_operacao`, em `REQUIRES_NEW`. A data e o resultado sao recuperados do JSON persistido, nao recalculados.
3. Registro grava o estado final e duas entradas ordenadas de `registro_historico` na mesma transacao: origem `AGUARDANDO_REGISTRO` no instante do aceite e estado final no instante da gravacao local.
4. Se o processo perde a resposta ou falha antes do commit local, o ledger externo permanece; retentativa com o mesmo comando recupera o resultado.
5. Reentrega de estado final local retorna o salvo sem chamar a registradora. Nao acrescenta transicao, nao converte rejeicao em aprovacao e nao muda o instante original.

O historico minimo e interno e verificavel no banco/testes; **nao foi criado endpoint publico de historico**. A trilha com ator, acao, permissao e correlacao ainda e E11. Falha tecnica nao vira rejeicao de negocio.

### Garantias e limites

- Esse ledger representa uma registradora idempotente; nao demonstra idempotencia de uma instituicao real.
- Ele usa o H2 do registro, mas transacao independente, para testar a janela de falha sem infraestrutura extra. Nao ha atomicidade distribuida entre efeito externo e resultado local.
- Uma instancia por servico e a configuracao de demonstracao. Chaves unicas protegem a persistencia; uma corrida fora das travas da instancia pode exigir retry, nao significa exactly-once.
- Registros finais pre-E7 continuam com os dados originais, inclusive campos nulos e historico ausente. Nao fabricamos fatos antigos nem chamamos a registradora para alterar finais. O teste legado prova que a reentrega nao os modifica.
- Novos campos/tabelas sao adicionados por JPA. O teste de rejeicao continua partindo de schema E4; nenhum banco de demonstracao foi apagado.
- A simulacao guarda comando completo para verificar conflito, como o registro ja fazia. Protecao/retencao dessas tabelas e acesso operacional sao E11/E12, nao conformidade de producao ja entregue.

## Significado de 202 e retry

`202` significa **aceita para processamento**, nao registrado. A outbox duravel permite aceitar enquanto o transporte esta fora, desde que aceite + comando tenham sido confirmados no banco. Falha que impede essa persistencia retorna 503. Repetir um POST idempotente nao reinicia mensagem isolada.

Continuam vigentes tres tentativas totais e esperas minimas de 1 s/5 s no HTTP, conforme [ADR-008](../adr/ADR-008-retry-e-isolamento-outbox.md). Esgotamento preserva a mensagem, nao garante termino eventual. A migracao para broker JVM sera E8; eventos e sua outbox continuam E9.

## Validacoes executadas — 20/10/2026

```powershell
mvn -B clean verify
```

**BUILD SUCCESS: 146 testes**, zero falhas, erros ou ignorados:

| Modulo/fase | Quantidade |
|---|---:|
| Emissor / Surefire | 87 |
| Registro / Surefire | 47 |
| Consulta / Surefire | 11 |
| Consulta / Failsafe | 1 |

Evidencias novas:

- [EmissaoRegrasTest](../../boleto-emissor/src/test/java/br/com/caixa/cobranca/emissor/integration/EmissaoRegrasTest.java): 54 casos da matriz.
- [DominioRegistroTest](../../boleto-registro/src/test/java/br/com/caixa/cobranca/registro/integration/DominioRegistroTest.java): 27 casos, incluindo 19 campos obrigatorios, UUID, dados persistidos, historico sem duplicacao, resposta perdida aprovada/rejeitada, falha local, rollback apos flush, conflito externo e legado.
- [RegistradoraSimuladaTest](../../boleto-registro/src/test/java/br/com/caixa/cobranca/registro/integration/RegistradoraSimuladaTest.java): 8 casos, incluindo troca de cenario, invariantes de resultado e disputa concorrente de chave sem sobrescrever efeito.
- [RegistroRejeitadoTest](../../boleto-registro/src/test/java/br/com/caixa/cobranca/registro/integration/RegistroRejeitadoTest.java): schema E4, rejeicao, historico e reentrega sem segunda chamada.
- Testes existentes de emissao, rollback de outbox, retry HTTP e concorrencia permaneceram verdes.
- PACT consumidor/provedor e comparacao canonico/gerado passaram com os campos novos; projecao publica tambem verificada.

Nao houve teste de carga, broker ou jornada completa dos tres processos nesta etapa. Todos os testes executaram sem Docker; recursos HTTP/H2 de teste encerrados ao finalizar.

## Uso de IA e proxima etapa

A IA implementou as mudancas e executou os testes. A decisao do grupo foi manter uma simulacao nao pagavel e migrar para broker JVM depois. O principal risco identificado foi uma falsa garantia de idempotencia se a resposta externa participasse do mesmo rollback; por isso a transacao independente foi testada com perda de resposta e falha depois do flush local.

**Proxima etapa: E8 — selecionar/validar broker JVM e migrar a fila de comandos. Fechamento somente na E15.**
