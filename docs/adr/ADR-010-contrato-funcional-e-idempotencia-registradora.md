# ADR-010 — Contrato funcional e idempotencia da registradora simulada

## Contexto

A E6 comprovou a fronteira HTTP, mas a aprovacao ainda nao fornecia dados de pagamento. O registro validava apenas o envelope e podia aceitar solicitacoes incompletas. Tambem havia uma janela entre a resposta da registradora e o commit local: uma falha nesse intervalo poderia repetir o efeito externo.

A Sessao 4 pede idempotencia tanto do consumidor quanto da integracao e historico das transicoes. O ambiente permanece Java 17, H2 local e uma instancia de cada servico, sem Docker.

## Decisao

### Contrato e validacao

- Reutilizar a validacao estrutural da solicitacao em `shared-contracts/SolicitacaoBoletoValidator`, sem Spring, banco ou processamento do registro. O componente `EmissaoValidator` preserva o ponto de injecao do emissor.
- Essa e uma extensao deliberada do modulo de contratos: evita duas implementacoes divergentes das mesmas restricoes de campos. Maquina de estados, idempotencia e persistencia continuam nos respectivos servicos.
- Registro exige envelope consistente, UUID v4 com variante RFC, aceite `AGUARDANDO_REGISTRO`, instante e solicitacao valida antes de chamar a registradora. Erros retornam o 400 padrao do comando, sem expor valores pessoais; a emissao continua agregando todos os campos invalidos no seu 400.
- Nao acrescentar limites numericos de texto, verificacao de digitos CPF/CNPJ ou regras de encargos nao acordadas. A matriz explicita o que e formato implementado e o que ainda e premissa.

### Dados didaticos, nunca pagaveis

- Aprovacao fornece `SIM-{uuid}`, instante e strings `NAO-PAGAVEL-SIM-LINHA-{uuid}` / `NAO-PAGAVEL-SIM-BARRAS-{uuid}`.
- Sao marcadores de demonstracao, nao linha digitavel bancaria nem codigo de barras numerico/valido. Nao gerar digitos verificadores ou documento para pagamento real.
- Rejeicao fornece codigo/motivo, sem dados de pagamento nem identificador/data de registro.
- Persistir os novos campos e atualizar juntos PACT consumidor, canonico e provedor. A projecao publica preserva os campos apenas para `REGISTRADO`.

### Historico e atomicidade local

- O aceite original ja fica persistido no emissor na transacao aceite + outbox, antes do 202.
- Para novos registros, guardar em `registro_historico` a origem `AGUARDANDO_REGISTRO` com o instante recebido e a transicao final com o instante da gravacao local. O instante da confirmacao externa permanece separado em `data_hora_registro`.
- Historico e resultado local sao gravados na mesma transacao. Unicidade por boleto/estado e retorno antecipado em reentrega evitam duplicar transicoes.
- Falha tecnica nao cria estado `REJEITADO` nem historico final. Auditoria completa com ator/acesso/correlacao permanece na E11.

### Simular uma registradora com idempotencia duravel

- Guardar comando e resposta em `registradora_simulada_operacao`, com UUID do boleto como chave unica.
- Usar versao JPA inicialmente nula para que o UUID atribuido seja inserido com `persist`, nao `merge`. A disputa de chave falha sem sobrescrever uma resposta concorrente.
- O armazenamento e feito por outro bean em transacao `REQUIRES_NEW`: representa o commit independente da instituicao externa, sem usar a transacao local como falsa garantia distribuida.
- Mesmo UUID/conteudo devolve a resposta salva, inclusive data/hora e rejeicao, sem recalcular e independentemente do cenario configurado depois. Conteudo diferente conflita, tambem quando o resultado local ainda nao existe.
- Uma resposta perdida ou rollback local nao desfaz a operacao simulada; a retentativa recupera o resultado e conclui registro/historico.
- Consumidor continua usando travas por UUID na instancia e chave unica no banco. A simulacao persiste um unico efeito por UUID; uma corrida de insercao que perca a disputa pode falhar tecnicamente e precisar de reentrega, nunca sobrescreve o resultado.
- O ledger fica fisicamente no H2 do registro por ser simulacao didatica, nao uma base compartilhada entre microsservicos. Uma registradora real precisaria oferecer chave idempotente/consulta e contrato equivalente; nao prometemos exactly-once para um provedor desconhecido.

### Aceite 202 e compatibilidade

- Transporte indisponivel nao impede 202 quando aceite e outbox foram confirmados localmente. Se nao conseguimos persistir com seguranca, responder 503; nao e preciso esperar envio/registro para aceitar.
- Preservar a politica HTTP da ADR-008: tres tentativas totais, esperas minimas de 1 s/5 s e isolamento; fila JVM sera a E8.
- Novos campos/tabelas sao adicionados pelo JPA sem apagar bases. Registros finais antigos sao preservados como foram gravados: nao recalcular dados de pagamento, nao chamar a registradora e nao inventar historico retroativo. Essa limitacao historica e explicita; dados completos/historico sao garantidos para novos processamentos E7.

## Alternativas consideradas

- **Duplicar validadores no registro:** risco de divergencia e manutencao dobrada; reutilizar somente restricoes do contrato.
- **Gerar sequencias bancarias aparentemente validas:** rejeitado pelo carater exclusivamente didatico e risco de confusao com cobranca real.
- **Guardar resposta externa na mesma transacao local:** rollback apagaria a prova do efeito externo, escondendo justamente a janela que precisamos testar.
- **Somente cache em memoria na registradora:** perderia idempotencia apos reiniciar.
- **Reprocessar automaticamente estados finais antigos para preencher campos:** violaria a regra de nao repetir efeito externo nem alterar finais; sem fatos suficientes para reconstruir historico.

## Status e evidencias

**Implementada e validada em 20/10/2026.** Build limpo sem Docker com 146 testes aprovados. Resposta perdida (aprovacao/rejeicao), rollback apos flush, concorrencia, comando invalido e PACT cobertos. Matriz e limites: [E7](../etapas/E7-contrato-funcional.md).
