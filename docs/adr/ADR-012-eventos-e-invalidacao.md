# ADR-012 — Eventos confiaveis e invalidacao de cache

## Contexto e decisao

A Sessao 4 exige `EmissaoAceita`, `BoletoRegistrado` e `RegistroRejeitado`, sem dados pessoais, e intencao de publicacao atomica ao estado. A E8 ja fornece Artemis/Jakarta JMS em JVM separada; nao adicionar Kafka, Docker ou notificacao efetiva.

- Contrato unico de envelope em `shared-contracts`, com tipo/estado consistentes, UUID de evento, UUID de boleto, convenio, instante e correlacao; sem solicitacao ou resposta completa.
- Nova outbox de eventos em cada banco produtor, sem compartilhar bancos. Modulo tecnico `eventos-outbox` reutiliza entidade/repositorio/dispatcher JMS entre emissor e registro; regras e DTOs continuam sem Spring em `shared-contracts`.
- Persistir intencao junto ao aceite no emissor e junto ao resultado/historico no registro. Publicar depois, por scheduler independente, com commit JMS antes de marcar enviado. Nao publicar dentro da transacao de negocio.
- Tres tentativas totais de publicacao com minimos 1 s/5 s; erro final preservado na outbox. Sem replay automatico de isolados; E12. Confirmacao perdida pode repetir o mesmo id de evento.
- Endereco MULTICAST `boletos.eventos`, assinatura/fila duravel `boletos.eventos.consulta` e quarentena `boletos.eventos.quarentena`. Cada assinante adicional precisa de sua fila duravel preprovisionada, nao dividir a fila de cache com BI/antifraude.
- Consulta invalida apos evento final, nao projeta dados de pagamento do evento. Duplicatas/fora de ordem apenas repetem invalidacao, sem rebaixar estados. TTL 30 s/10 min continua protecao.
- Leitura/preenchimento e invalidacao usam a mesma trava por faixa de UUID para evitar instalar um transitório depois da invalidacao. A entrega aguarda a leitura em andamento terminar, invalida e so entao confirma JMS. Uma resposta em voo pode ser antiga; a proxima leitura nao reutiliza esse preenchimento.
- Uma instancia de consulta por demonstracao. Mais instancias com cache local exigem uma assinatura duravel exclusiva por instancia; consumidores concorrentes na mesma fila nao difundem invalidacao.
- Correlacao inicial usa UUID do boleto, estavel entre comando e eventos. Propagacao de identificador recebido no HTTP e validacao ponta a ponta continuam E10; nao alegar que ja existem.

## Alternativas

- Publicacao direta apos commit: rejeitada, perde intencao na janela de queda.
- Eventos dentro da transacao JPA: rejeitada, JMS/JPA nao tem XA; confirmacao do broker poderia sobreviver ao rollback local.
- Duplicar infraestrutura nos dois produtores: rejeitada; usar modulo tecnico pequeno, sem dependencias entre dominios.
- Apenas TTL: permanece como protecao, mas nao atende sozinho a reacao a mudanca.
- Atualizar cache com evento: rejeitada nesta etapa, evento minimo nao carrega dados suficientes para resposta completa.
- `@CacheEvict` sem coordenar `@Cacheable`: rejeitada; miss em andamento pode reinstalar estado antigo depois de invalidar.

## Status

Decisao implementada na E9. Evidencias de atomicidade/reentrega/pubsub e corrida de cache, operacao e limites: [E9](../etapas/E9-eventos-e-cache.md). Atualiza a estrategia apenas-TTL da ADR-006; seus valores continuam provisorios. Aceite integrado final continua E14.
