# ADR-006 — Cache-aside na consulta de boletos

## Contexto
A consulta é muito mais frequente que a emissão e precisa mostrar o estado atual sem transformar identificador inexistente em boleto existente. O estado `AGUARDANDO_REGISTRO` pode evoluir para `REGISTRADO` ou `REJEITADO`; os estados finais são imutáveis no domínio.

## Decisão provisória
- Usar **cache-aside** no `boleto-consulta`, com Caffeine no Plano B.
- Usar TTL variável por estado: **30 segundos** para `AGUARDANDO_REGISTRO` e **10 minutos** para `REGISTRADO`/`REJEITADO`. Os números são premissas didáticas da Sessão 3, ainda sujeitos a validação dos requisitos.
- Consultar primeiro o `boleto-registro`; somente uma resposta **404** dele ativa o fallback ao endpoint interno do `boleto-emissor`, conforme a opção C do ADR-004.
- Não cachear 404 nem falhas técnicas. Se qualquer fonte necessária estiver indisponível, responder 503 com `correlation_id`; não converter indisponibilidade em ausência.
- Na E3, apenas TTL. **Atualização E9:** eventos finais invalidam a entrada, mantendo TTL como proteção; leitura/preenchimento e invalidação são coordenados para não reinstalar estado antigo após o ack. Ver [ADR-012](ADR-012-eventos-e-invalidacao.md).
- Expor dados de pagamento apenas em `REGISTRADO`; dados de rejeição apenas em `REJEITADO`.

## Alternativas consideradas
- **TTL por estado (escolhida provisoriamente):** simples de explicar e validar; aceita uma janela de eventual desatualização.
- **Invalidação por evento:** adotada como complemento na E9, com outbox de eventos e assinatura durável; valores de TTL preservados.
- **Write-through:** faria a consulta participar da escrita do estado, contrariando a propriedade dos dados e acoplando os contextos.
- **Sem cache:** mais simples, mas não atende ao objetivo de leitura frequente e de baixa latência da Sessão 3.

## Consequências
- Respostas encontradas são cacheadas pelo identificador UUID.
- Uma entrada `AGUARDANDO_REGISTRO` expira antes que uma entrada final, permitindo descobrir a transição mesmo se a invalidação atrasar ou terminar isolada.
- 404 sempre consulta as fontes novamente; falha técnica sempre permanece distinguível de ausência.
- A política é provisória e deve ser revista se as premissas de TTL ou os requisitos operacionais mudarem.

## Status
**Provisória, aceita pelo solicitante em 19/10/2026.** A decisão de negócio ADR-004 (opção C) foi discutida e aceita pelo grupo; os TTLs e a estratégia de cache podem ser revistos pelo grupo com base nos requisitos e medições.

**Complementada pela ADR-012 na E9:** invalidação idempotente após evento final, sem projeção parcial nem rebaixamento por eventos fora de ordem. Uma resposta já em voo pode ser antiga; depois do ack seu preenchimento não fica no cache. Uma instância de consulta por demonstração; múltiplas instâncias exigem assinaturas independentes. Evidências e limites na [E9](../etapas/E9-eventos-e-cache.md).
