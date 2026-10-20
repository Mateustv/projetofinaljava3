# Auto-avaliação — Central de Cobrança e Boletos
Grupo: <integrantes e papéis — PREENCHER>
Tema/domínio: Central que aceita emissões de boleto (202), registra o título numa registradora simulada via outbox + HTTP assíncrono e permite consultar o estado com cache.
Perfil de execução: B (pura-JVM)  ·  Fallback: transporte HTTP provisório na E4; RabbitMQ/Docker adiado.

> Esqueleto criado na E0. Os níveis (0–3) e evidências são preenchidos conforme as etapas avançam.

## Evidências por critério
1. Decomposição de domínio — nível: _
   Evidência: (E1) módulos `boleto-*`, bases H2 separadas, `docs/arquitetura.md`, ADR-002/003
2. Comunicação assíncrona — _
   Evidência: (E4) outbox transacional + entrega HTTP assíncrona; consumidor idempotente; garantia at-least-once. Sem broker RabbitMQ nesta etapa.
3. Idempotência e consistência — _
   Evidência: (E2) emissor persiste o aceite; mesma chave e conteúdo retorna a resposta original sem duplicar; conteúdo diferente retorna 409. (E4) consumidor idempotente para comandos repetidos.
4. Cache — _
   Evidência: (E3) Caffeine cache-aside; TTL variável por estado (30 s transitório, 10 min final), sem cachear 404; testes de cache hit, fallback e 503.
5. Resiliência — _
   Evidência: (E5 provisória HTTP) três tentativas totais, backoff persistido de 1 s/5 s, isolamento técnico e rejeição de negócio; testes com HTTP/H2. Exchange/binding e DLQ de broker ainda pendentes; não atribuir evidência RabbitMQ a esta implementação.
6. Testabilidade — _
   Evidência: (E5/PACT) — `mvn verify`
7. Decisões arquiteturais — _  → ver `docs/adr/`
8. Uso crítico de IA — _
   Como usamos IA e o que validamos manualmente: (ver `docs/etapas/` — cada etapa registra o que foi gerado/validado)
9. Execução — _
   Como rodar: ver README; perfil declarado.

## Opcionais entregues
<se grupo > 3 pessoas>
