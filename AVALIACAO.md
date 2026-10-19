# Auto-avaliação — Central de Cobrança e Boletos
Grupo: <integrantes e papéis — PREENCHER>
Tema/domínio: Central que aceita emissões de boleto (202), registra o título numa registradora simulada via fila e permite consultar o estado com cache.
Perfil de execução: B (pura-JVM)  ·  Fallbacks usados: <preencher>

> Esqueleto criado na E0. Os níveis (0–3) e evidências são preenchidos conforme as etapas avançam.

## Evidências por critério
1. Decomposição de domínio — nível: _
   Evidência: (E1) módulos `boleto-*`, bases H2 separadas, `docs/arquitetura.md`, ADR-002/003
2. Comunicação assíncrona — _
   Evidência: (E4)
3. Idempotência e consistência — _
   Evidência: (E2/E4)
4. Cache — _
   Evidência: (E3)
5. Resiliência — _
   Evidência: (E5)
6. Testabilidade — _
   Evidência: (E5/PACT) — `mvn verify`
7. Decisões arquiteturais — _  → ver `docs/adr/`
8. Uso crítico de IA — _
   Como usamos IA e o que validamos manualmente: (ver `docs/etapas/` — cada etapa registra o que foi gerado/validado)
9. Execução — _
   Como rodar: ver README; perfil declarado.

## Opcionais entregues
<se grupo > 3 pessoas>
