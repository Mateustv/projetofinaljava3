# ADR-003 — O corte: três serviços (emissão, registro, consulta)

## Contexto
O domínio de cobrança poderia ser um monólito ou muitos serviços. Precisamos de ≥ 2 serviços (CORE) cortados por **bounded context**, não por camada técnica. A linguagem ubíqua muda de significado entre três momentos do boleto:
- **Emissão:** "aceitar a solicitação e criar a identidade" (valida, idempotência, 202).
- **Registro:** "obter a confirmação da registradora" (fila, retry, DLQ, estado final).
- **Consulta:** "ler o estado" (altíssimo volume de leitura, cache).

## Decisão
Três serviços: `boleto-emissor`, `boleto-registro`, `boleto-consulta`.

## Alternativas consideradas
- **Monólito modular** — mais simples, mas não demonstra decomposição/segregação (CORE). **Rejeitada.**
- **2 serviços (emissão+consulta, registro)** — mistura escrita leve com leitura de alto volume; os dois escalam e falham de formas diferentes. **Rejeitada.**
- **Serviço por estado/etapa (muitos)** — granularidade excessiva para o prazo. **Rejeitada.**
- **3 contextos** — alinhado às sessões de requisitos e ao critério de escala independente (leitura ≫ escrita, ~20:1). **Escolhida.**

## Consequências
- Cada contexto escala e falha de forma independente (a consulta continua mesmo se a registradora cair).
- Custo: três deploys e contratos a manter (PACT no fim do projeto).

## Status
Aceita
