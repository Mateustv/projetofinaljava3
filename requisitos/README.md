# Requisitos — Central de Cobrança e Boletos Bancários

Pacote completo de **elicitação de requisitos de negócio e engenharia** da Central de Cobrança e Boletos. Este documento e as transcrições das sessões de descoberta estabelecem a linguagem ubíqua, as dores de negócio dos clientes corporativos, as regras regulatórias do Banco Central (Bacen/CIP) e os critérios funcionais e não-funcionais que guiam todas as decisões arquiteturais do sistema.

## O Sistema em uma Frase

Uma **plataforma bancária de missão crítica** que recebe solicitações de cobrança em lote de ERPs corporativos, valida e acolhe-as de forma assíncrona (`HTTP 202 Accepted`), **homologa o registro obrigatório perante a Câmara Registradora Centralizada (CIP/B3)** e permite a **consulta massiva de situação e dados pagáveis com altíssima disponibilidade e baixa latência**. O sistema não realiza liquidação financeira imediata (que ocorre dias depois no subsistema de compensação bancária).

## O Contexto do Problema Bancário

1. **Gargalo de Virada de Mês e Fechamento:** Milhares de empresas conveniadas faturam e emitem boletos simultaneamente nas datas de corte (picos de até 500 requisições/s). A câmara externa não suporta essa vazão sem degradação. A Central precisa atuar como um pulmão de acolhimento assíncrono para nunca travar o faturamento das empresas.
2. **Risco Jurídico de Cobrança Duplicada:** Reenvios de remessas por oscilação de rede entre o ERP e o banco exigem idempotência radical na entrada e no processamento, evitando emissão de dois boletos para a mesma dívida.
3. **Conformidade Regulatória (Bacen/Febraban):** Nenhum boleto pode ser disponibilizado para pagamento (linha digitável/código de barras) antes da homologação definitiva do registro na câmara centralizada.
4. **Assimetria de Tráfego 20:1:** A consulta de boletos tem volumetria 20 vezes maior que a emissão, exigindo isolamento de contexto e cache inteligente para não sobrecarregar as bases de gravação.

## Estrutura do Pacote de Requisitos

| Arquivo | Finalidade e Conteúdo |
|---|---|
| [`personas.md`](personas.md) | Perfil dos envolvidos: Operação Bancária, Arquitetura, Produto, Jurídico e Clientes Corporativos. |
| [`sessao-1-kickoff.md`](sessao-1-kickoff.md) | Alinhamento executivo: dores do cliente corporativo, escopo do MVP, riscos e máquina de estados. |
| [`sessao-2-emissao.md`](sessao-2-emissao.md) | Contrato de entrada, campos obrigatórios, regras de convênio, idempotência e resposta 202. |
| [`sessao-3-consulta-e-desempenho.md`](sessao-3-consulta-e-desempenho.md) | Padrões de consulta, proporção 20:1, estratégia de cache-aside, eliminação do falso 404 e SLAs. |
| [`sessao-4-confiabilidade-e-integracoes.md`](sessao-4-confiabilidade-e-integracoes.md) | Integração com a registradora, idempotência externa (ledger), outbox, retentativas e quarentena (DLQ). |
| [`sessao-5-compliance-e-fechamento.md`](sessao-5-compliance-e-fechamento.md) | Delimitação de fases: o que entra no MVP (Fase 1) vs pós-go-live (Fase 2 - liquidação e retenção). |
| [`user-stories.md`](user-stories.md) | Histórias de usuário consolidadas com critérios estritos de aceite técnico e de negócio. |
| [`glossario-e-regras.md`](glossario-e-regras.md) | Linguagem ubíqua, máquina de estados, regras de validação estrutural e requisitos não funcionais. |

## Como Utilizar este Pacote

1. Inicie pelas [`user-stories.md`](user-stories.md) para entender as capacidades entregues ao negócio.
2. Recorra às transcrições das sessões para compreender **o porquê de cada decisão de negócio**.
3. Consulte o [`glossario-e-regras.md`](glossario-e-regras.md) como a fonte de verdade para formatos de dados, respostas de erro e premissas de engenharia.
4. Veja como essas necessidades de negócio se traduziram em decisões de arquitetura e trade-offs nas [ADRs do projeto](../docs/adr/).
