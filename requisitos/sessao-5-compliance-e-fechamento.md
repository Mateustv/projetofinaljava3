# Sessão 5 — Compliance, segurança, retenção e fechamento

**Projeto:** Central de Cobrança e Boletos · **Data:** 04/05/2026, 14h00–14h30 · **Duração:** 30 min · **Plataforma:** Microsoft Teams
**Participantes:** Marina, Helena, Téo, Daniel, Caio e Roberto

> Trechos marcados com ➕ foram acrescentados para detalhamento de engenharia e conformidade.

---

**Helena:** A Central de Cobrança trata dados pessoais e financeiros protegidos: identificação de beneficiário e pagador, documentos CPF/CNPJ, endereços, valores, vencimentos e histórico transacional. A governança bancária exige finalidade legítima, base legal contratual, minimização estrita e controle de acesso proporcional ao risco.

**Daniel:** Isso se aplica a todas as fronteiras de dados — banco de dados, memória de cache, filas JMS, DLQ, logs de servidor e eventos de mensageria?

**Helena:** Exatamente. Cada canal deve trafegar apenas o estritamente necessário para sua função. A DLQ de mensagens com falha não pode se transformar em um repositório informal de dados desprotegidos, e eventos de domínio internos também não devem carregar dados excessivos.

**Téo:** A observabilidade e a auditoria devem permitir reconstruir qualquer incidente operacional: emissão, consulta, transição de estado e falha de processamento.

**Helena:** O registro de rastreabilidade deve conter apenas metadados técnicos: ator ou sistema requisitante, ação executada, identificador do título (UUID), data/hora, status do resultado e Correlation ID. Nunca registre o payload completo do boleto no log.

➕ **Helena:** Em logs, documentos cadastrais devem sair mascarados (ex.: `***.456.789-**`). Valores financeiros nominais e dados completos de endereço não devem constar nos logs operacionais da aplicação.

**Caio:** Qual é o prazo legal e regulatório de retenção de dados de cobrança?

**Helena:** Conforme a regulamentação do Banco Central e as normas fiscais aplicáveis a títulos de crédito, a hipótese de retenção é de **cinco anos** após o encerramento do ciclo da cobrança.

➕ **Daniel:** Entendemos "encerramento" como o instante em que o boleto atinge seu estado final definitivo (`REGISTRADO` homologado ou `REJEITADO` pela câmara). Esse é o marco inicial para contagem do ciclo de vida arquivístico.

**Daniel:** O cache da consulta terá retenção curta em memória e será invalidado reativamente. A Quarentena (DLQ) terá acesso operacional restrito e rastreável.

➕ **Téo:** Perfis de acesso da arquitetura:
1. **Sistemas Emissores (ERPs):** Chamam a API de emissão e consultam seus respectivos convênios;
2. **Canais de Autoatendimento / Pagadores:** Consultam dados para visualização de 2ª via pagável de títulos registrados;
3. **Engenharia e Sustentação (SRE):** Acessam filas de quarentena em caso de mensagens venenosas para diagnóstico forense.

**Roberto:** Como fatiamos essas exigências de segurança para viabilizar o lançamento da Fase 1 (MVP) com qualidade técnica inegociável?

➕ **Daniel:** Seguindo a estratégia de gestão de risco e time-to-market ([ADR-014](../docs/adr/ADR-014-prioridade-core-e-limites-de-escopo.md)), fatiamos a implementação:
- **Fase 1 (MVP — Núcleo de Alta Confiabilidade):** Minimização rigorosa em logs (sem valores ou dados pessoais), envelopes de eventos estritamente mínimos (7 campos de metadados), isolamento atômico de mensagens venenosas em Quarentena durável e correlação ponta a ponta (`X-Correlation-ID`) em todos os canais HTTP e JMS.
- **Fase 2 (Pós-Go-Live Corporativo):** Integração com mTLS e certificados digitais por convênio, criptografia de repouso com HSM corporativo e rotinas automatizadas de expurgo e arquivamento fiscal após 5 anos.

**Marina:** Fechado. A Fase 1 entrega a esteira transacional sólida: HTTP 202, Transactional Outbox, filas duráveis Artemis, consumidor com Ledger idempotente, cache reativo com travas e Circuit Breakers. A governança da Fase 2 complementa a proteção em repouso.

---

## Decisões da sessão

1. **LGPD e Governança em Todos os Canais:** Aplicação de minimização em bancos, cache, mensageria e logs.
2. **Minimização de Eventos:** Envelopes de domínio sem CPF/CNPJ, valor nominal ou dados de pagamento.
3. **Rastreabilidade Forense Padronizada:** Logs estruturados utilizando Correlation ID sem vazar dados sensíveis.
4. **Ciclo de Retenção Fiscal:** Prazo de cinco anos a partir do encerramento da cobrança (estado final).
5. **Estratégia de Fatiamento (ADR-014):** Fase 1 foca no isolamento de dados na esteira transacional; Fase 2 incorpora mTLS, HSM e rotinas de expurgo.
6. **Acesso Operacional à DLQ:** Quarentena restrita à equipe de sustentação, com auditoria de metadados.

## Campos Mínimos de Auditoria e Logs Técnicos

`ator/sistema` · `ação` (EMISSAO, CONSULTA, MUDANCA_ESTADO, ISOLAMENTO) · `identificador_boleto` (UUID) · `data_hora` · `resultado` · `correlation_id` (UUID v4)

---

## Glossário acrescentado

- **Minimização de Dados:** Princípio da LGPD de trafegar e persistir apenas o mínimo necessário para cada operação técnica.
- **Correlação Distribuída:** Identificador UUID v4 técnico para reconstruir a jornada da requisição entre múltiplos nós.
- **Retenção Fiscal:** Prazo de 5 anos determinado por regulamentação financeira com marco inicial após o estado final.
