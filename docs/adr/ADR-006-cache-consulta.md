# ADR-006 — Estratégia de Cache-Aside com TTL Diferenciado por Ciclo de Vida do Título

## Contexto

O ecossistema de cobrança bancária apresenta uma assimetria brutal entre operações de escrita e leitura: para cada 1 solicitação de emissão, ocorrem em média **20 consultas de situação** (ERPs realizando varreduras periódicas de status de remessa, pagadores acessando portais corporativos para imprimir a 2ª via e centrais de atendimento consultando títulos).

Se cada consulta executasse chamadas de rede e queries SQL contra o banco relacional do `boleto-registro`:
1. **Sobrecarga Catastrófica de Infraestrutura:** O volume de leitura de pico exauriria os pools de conexões e degradaria o I/O de disco da base que processa o registro regulatório.
2. **Latência de Leitura Elevada:** A experiência do pagador final e das APIs corporativas exige tempos de resposta inferiores a 100 ms (p95), inviáveis em cenários de consultas repetitivas a bancos relacionais sob alta carga.

Além disso, os estados do ciclo de vida bancário possuem comportamentos de mutabilidade opostos:
- `AGUARDANDO_REGISTRO`: estado transitório e efêmero. O título está na fila e transitará para o resultado definitivo em poucos segundos.
- `REGISTRADO` e `REJEITADO`: **estados finais juridicamente imutáveis**. Uma vez que a câmara registradora externa confirma o registro ou recusa a cobrança, o título não sofre novas alterações de estado no ciclo de emissão.

## Decisão

Adotar o padrão arquitetural **Cache-Aside com TTL Diferenciado por Estado** no microsserviço `boleto-consulta`, utilizando a biblioteca Caffeine em memória:

1. **TTL Curto (30 segundos) para Estado Transitório (`AGUARDANDO_REGISTRO`):**
   - Garante que a resposta temporária permaneça no cache por uma janela estreita, reduzindo a pressão imediata sobre o emissor e registro, mas forçando a busca do estado consolidado em no máximo 30 segundos mesmo em caso de atraso na mensageria.
2. **TTL Longo (10 minutos) para Estados Finais (`REGISTRADO` e `REJEITADO`):**
   - Como os dados do título final são imutáveis, consultas repetidas de pagadores e ERPs são servidas em menos de 5 ms diretamente da memória da JVM, com zero chamadas ao backend.
3. **Proibição Estrita de Cache para Falhas e Ausências:**
   - Respostas de ausência (`HTTP 404 Not Found`) e falhas técnicas (`HTTP 503 Service Unavailable`) **nunca são cacheadas**. Se um título acabou de ser emitido ou a rede oscilou, a próxima requisição deve consultar as fontes reais imediatamente, evitando criar "falsos negativos persistentes".
4. **Camada Dupla com Invalidação Reativa (ADR-012):**
   - O TTL de 30 segundos atua como rede de segurança (*fail-safe*). A invalidação prioritária é orientada a eventos em tempo real, expurgando a chave do cache assim que o evento `BoletoRegistrado` ou `RegistroRejeitado` é recebido via pub/sub durável.

## Alternativas consideradas

- **Consulta Direta sem Cache:** Rejeitada. A proporção 20:1 de leitura contra escrita provocaria contenção inaceitável nas bases do registro e descumprimento sistemático do SLA de latência sob carga de virada de mês.
- **TTL Fixo e Uniforme para Todos os Estados:** Rejeitada. Um TTL fixo curto (ex.: 10 s) sobrecarregaria o banco inutilmente com títulos já registrados há dias. Um TTL fixo longo (ex.: 5 min) prenderia o estado `AGUARDANDO_REGISTRO` por tempo excessivo, fazendo o cliente acreditar que o registro falhou.
- **Cache Write-Through / Write-Behind:** Rejeitada. Exigiria que a esteira de escrita do registro conhecesse ou atualizasse a camada de cache da consulta diretamente, destruindo o desacoplamento entre os microsserviços.

## Consequências

- **Positivas:**
  - **Latência Sub-Milissegundo:** Mais de 90% das consultas repetidas de títulos consolidados são resolvidas diretamente na memória da JVM com latência inferior a 10 ms.
  - **Proteção da Base Transacional:** Alívio maciço de carga de I/O e conexões no microsserviço de registro, preservando recursos para o processamento de novos boletos.
  - **Respeito à Semântica de Negócio:** Tratamento adequado da transitoriedade do aceite vs a imutabilidade do registro regulatório.
- **Custos / Mitigações:**
  - Existência de uma janela máxima de defasagem de até 30 segundos no estado transitório caso ocorra falha no transporte de eventos. Esse risco é neutralizado em condições normais pela invalidação orientada a eventos em tempo real (ADR-012).

## Status

Aceita e implementada no serviço de consulta.
