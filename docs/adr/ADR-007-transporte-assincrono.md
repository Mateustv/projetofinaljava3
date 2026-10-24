# ADR-007 — Comunicação Assíncrona: Nivelamento de Carga e Garantia de Entrega At-Least-Once

## Contexto

A integração entre o subsistema de entrada (`boleto-emissor`) e o subsistema de processamento bancário (`boleto-registro`) precisa lidar com duas realidades operacionais incompatíveis da infraestrutura financeira:
1. **Picos Extremos de Ingestão:** Durante a virada de mês e datas de vencimento em lote, o emissor recebe até 500 solicitações por segundo dos ERPs conveniados.
2. **Capacidade Limitada da Câmara Registradora:** As registradoras externas (CIP/B3) possuem limites rígidos de concorrência (*throttling*) e tempos de resposta que variam de centenas de milissegundos a vários segundos.

Se o emissor invocasse o registro por meio de chamadas síncronas (REST/gRPC):
- **Efeito Dominó e Esgotamento de Threads:** A lentidão na resposta da câmara externa reteria as conexões e threads do emissor, provocando indisponibilidade generalizada e recusa em cascata de novas emissões.
- **Perda de Cobranças por Timeout:** Timeouts na comunicação direta levariam à interrupção de transações e perda de solicitações dos clientes corporativos.

Além disso, a natureza da mensagem enviada é um **Comando Imperativo de Ação** (*"Efetive o registro regulatório desta cobrança"*), que possui **um único dono de processamento lógico** e não pode ser processado concorrentemente por sistemas não autorizados.

## Decisão

Adotar **Comunicação Assíncrona Ponto-a-Ponto (Fila ANYCAST) com Garantia At-Least-Once**:

1. **Desacoplamento por Fila Durável:** Os comandos de registro (`RegistrarBoletoCommand`) são enfileirados na fila durável persistente `registro.comandos`.
2. **Nivelamento de Carga (*Load Leveling*):** A fila atua como um amortecedor elástico (*buffer* transacional), absorvendo instantaneamente os picos de 500 req/s da emissão e permitindo que o `boleto-registro` consuma as mensagens na velocidade sustentada pela câmara externa.
3. **Confirmação Estrita Pós-Commit (Transactional Ack):** O consumidor no `boleto-registro` processa a cobrança, persiste o resultado no banco relacional local e **somente após o commit bem-sucedido no banco confirma o recebimento ao broker (ACK)**.
4. **Resiliência a Quedas (Semântica At-Least-Once):**
   - Caso o serviço consumidor sofra uma falha fatal ou reinício após gravar no banco mas antes de emitir o ACK ao broker, a mensagem retornará à fila e será reentregue.
   - Essa semântica de entrega *at-least-once* é tratada de forma mandatória pela **idempotência estrita do consumidor** (ADR-010), garantindo que a reentrega identifique o título já processado e não repita efeitos colaterais.

## Alternativas consideradas

- **Chamada Síncrona REST / gRPC:** Rejeitada. Acoplaria a disponibilidade da emissão à estabilidade da registradora externa, destruindo a resiliência nos picos de fechamento contábil.
- **Tópico Pub/Sub para Comandos:** Rejeitada. Comandos têm um único destinatário de execução de negócio; tópicos pub/sub são adequados para eventos de domínio com múltiplos assinantes (ADR-012), e não para delegação de comandos unívocos de trabalho.
- **Filas Efêmeras em Memória (In-Memory Queue):** Rejeitada por risco inaceitável de perda de dados: qualquer queda do processo destruiria cobranças acumuladas e não processadas.

## Consequências

- **Positivas:**
  - **Amortecimento Perfeito de Picos:** O banco pode aceitar milhares de cobranças em minutos sem sobrecarregar a câmara externa nem derrubar os servidores internos.
  - **Sobrevivência a Falhas Operacionais:** O serviço de registro ou a registradora podem ser parados para manutenção sem interromper o acolhimento de novas cobranças pelo emissor.
- **Custos / Mitigações:**
  - **Consistência Eventual:** O registro não é instantâneo; há uma defasagem temporal entre o aceite e a confirmação do título. Essa janela é resolvida elegantemente pela estratégia de *fallback* da consulta (ADR-004) e pela invalidação reativa de cache (ADR-012).
  - **Risco de Mensagens Duplicadas:** Tratado de forma definitiva pela idempotência baseada em chave única e ledger transacional no registro (ADR-010).

## Status

Aceita e implementada no fluxo de comandos do sistema.
