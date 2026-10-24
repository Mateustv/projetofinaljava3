# ADR-003 — Corte Arquitetural: Três Bounded Contexts (Emissão, Registro e Consulta)

## Contexto

O processamento de cobranças por boleto bancário abrange responsabilidades com linguagens ubíquas distintas, ciclos de vida de software independentes e, principalmente, **Requisitos Não Funcionais (RNFs) diametralmente opostos**:

1. **Ingestão de Cobrança (Boleto-Emissor):**
   - *Missão de Negócio:* Receber a intenção de cobrança dos ERPs clientes, validar dados cadastrais/financeiros e emitir um comprovante jurídico de aceite (`202 Accepted`).
   - *Perfil de Carga:* Picos violentos de ingestão em dias de fechamento contábil e faturamento em lote (até 500 requisições/s).
   - *SLA Requerido:* Baixíssima latência (< 100 ms) e disponibilidade máxima. Não pode bloquear esperando confirmações externas.
2. **Registro e Integração Regulatória (Boleto-Registro):**
   - *Missão de Negócio:* Processar a solicitação de registro perante a Registradora Centralizada (CIP/B3), validar regras de negócio bancárias, lidar com recusas e gerar dados pagáveis (linha digitável/código de barras).
   - *Perfil de Carga:* Processamento assíncrono em segundo plano (*background worker*).
   - *SLA Requerido:* Consistência transacional absoluta e resiliência contra oscilações de rede e lentidão da instituição externa. A prioridade é a confiabilidade e a garantia de não duplicidade financeira, e não a velocidade de milissegundos.
3. **Disponibilização e Consulta de Títulos (Boleto-Consulta):**
   - *Missão de Negócio:* Permitir que ERPs, canais de atendimento e pagadores verifiquem a situação do título e obtenham os dados para pagamento ou 2ª via.
   - *Perfil de Carga:* Volumetria desproporcionalmente massiva — proporção estimada de **20 consultas para cada 1 emissão** (poling de ERPs, checagem de sistemas, conferência de lotes).
   - *SLA Requerido:* Leitura rápida (p95 < 100 ms), alta concorrência e imunidade a falhas das camadas de escrita.

Se esses fluxos fossem mantidos juntos, a competição por recursos de CPU, memória e *pools* de threads provocaria o colapso do sistema durante os períodos de maior estresse operacional.

## Decisão

Decompor o sistema em **três microsserviços autônomos baseados em Bounded Contexts do DDD**:

1. `boleto-emissor`: Bounded Context de Entrada e Aceite. Responsável pela validação estrita da solicitação, aplicação de idempotência de negócio na entrada e despacho assíncrono.
2. `boleto-registro`: Bounded Context de Processamento Regulatório. Responsável pelo consumo durável de comandos, orquestração com a câmara registradora simulada, histórico de transições de estado e isolamento de erros em quarentena.
3. `boleto-consulta`: Bounded Context de Leitura e Visualização. Responsável pela exposição pública de status, otimização de leitura com cache em memória e invalidação reativa orientada a eventos.

## Alternativas consideradas

- **Monólito Modular:** Rejeitada para a solução alvo. Embora um monólito modular reduza o overhead de rede, ele acopla fatalmente o consumo de recursos de computação. No pico de faturamento bancário, a enxurrada de consultas (20:1) concorreria diretamente pelas conexões e threads com a ingestão das novas cobranças. Além disso, se a conexão com a câmara externa sofresse lentidão, as threads do monólito ficariam retidas, degradando tanto a emissão quanto as consultas de títulos já existentes.
- **Decomposição em 2 Serviços (Emissão + Consulta juntos, Registro separado):** Rejeitada. A emissão é uma operação transacional de escrita pura com alto volume de entrada; a consulta é leitura pura com estratégias pesadas de cache. Mantê-las no mesmo processo impediria o dimensionamento elástico: não seria possível escalar apenas as instâncias de consulta para atender a um aumento súbito de acessos no portal de autoatendimento sem gastar recursos desnecessários escalando a infraestrutura de validação de emissão.
- **Microsserviços Excessivos (Serviço para cada estado ou validador):** Rejeitada. Criaria granularidade fina (*nanosserviços*), multiplicando latência de rede e complexidade de orquestração sem nenhum ganho real de negócio.

## Consequências

- **Positivas:**
  - **Escalabilidade Elástica Independente:** A camada de consulta pode ser multiplicada horizontalmente (ex.: 10 réplicas com cache local) para absorver o tráfego 20:1 sem onerar os serviços de emissão e registro.
  - **Isolamento de Falhas Radical:** A indisponibilidade temporária da registradora externa afeta exclusivamente a fila de processamento assíncrono do registro; a emissão continua acolhendo clientes (202) e a consulta continua servindo dados cacheados.
  - **Alinhamento com Equipes e Ciclo de Vida:** O código de integração externa e regras bancárias da CIP evolui no `boleto-registro` sem necessidade de alterar ou republicar o `boleto-emissor`.
- **Custos / Mitigações:**
  - **Complexidade Distribuída:** Exige comunicação assíncrona por mensagens, gerenciamento de consistência eventual na leitura e validação contínua de contratos executáveis entre serviços (ADR-007, ADR-009).

## Status

Aceita e materializada na arquitetura do sistema.
