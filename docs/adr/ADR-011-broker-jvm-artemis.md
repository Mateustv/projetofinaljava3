# ADR-011 — Broker JVM Artemis

## Contexto e escolha

Grupo de tres integrantes, sem Docker e sem opcionais adicionais. A E8 precisa de fila real entre processos; a E9 precisa de pub/sub duravel. HTTP foi provisoriamente aceito na E4/E5, mas nao e broker.

Escolha para validacao e integracao: **Apache ActiveMQ Artemis**, usando a versao gerenciada pelo Spring Boot 3.3.5 (2.33.0), cliente Jakarta JMS e journal NIO compativel com Windows/Java 17. O broker executa em JVM propria, nao dentro de cada microsservico.

Alternativas: RabbitMQ nativo exige runtime/instalacao adicional; Qpid requer adaptar protocolo/configuracao; Kafka nao e necessario para os eventos deste MVP; filas isoladas em memoria nao conectam processos. Artemis oferece JMS transacional, ANYCAST/MULTICAST, assinaturas duraveis, armazenamento em disco e dead-letter.

## Topologia e garantias implementadas

- `registro.comandos`: ANYCAST, fila duravel, mensagens persistentes.
- `registro.quarentena`: ANYCAST duravel para mensagens permanentes e esgotamento de redelivery.
- Broker local escuta somente 127.0.0.1; sem autenticacao nesta etapa didatica. Protecao/credenciais e acesso operacional sao E11/E12, antes de exposicao fora da maquina.
- Publicador da outbox confirma transacao JMS antes de concluir envio local. Confirmacao significa **broker aceitou**, nao registro finalizado.
- Consumidor transacional confirma JMS somente apos commit do resultado JPA. Sem XA: falha apos commit local e antes de ack pode repetir; idempotencia E7 absorve essa janela.
- Erro permanente transfere payload e metadados para quarentena na mesma transacao JMS do recebimento; falha na transferencia causa rollback, nao descarte.
- Erro temporario provoca rollback JMS e redelivery do broker: tres entregas totais, esperas minimas de 1 s e 5 s. O produtor tem orcamento separado para publicar; nao retenta processamento de negocio.
- Nenhuma criacao automatica de destinos, nenhuma troca automatica para HTTP. Eventos/pub-sub de producao continuam E9.
- Fila inexistente e verificada antes de enviar. Ausencia de binding apos a verificacao encaminha comandos para a quarentena do broker, nao para descarte silencioso.
- Contador de entrega persistido antes do consumo; reinicio nao reinicia o orcamento. Orcamento da outbox continua acumulado durante a migracao.

## Status

**Aceita e implementada na E8.** Dependencias resolvidas no ambiente, Java 17/Windows, confirmacao transacional, TCP, journal/reinicio, retry/quarentena e duas assinaturas duraveis validados. Build completo com 177 testes e prova em quatro JVMs; execucao, migracao, limites e evidencias na [E8](../etapas/E8-broker-jvm.md).

Broker standalone empacotado com plugin Spring Boot, classifier `exec`; JAR normal preservado para testes dos servicos. A verificacao de ativacao impede anunciar prontidao depois de falha, por exemplo porta ocupada. Configuracao local nao comprova autenticacao, TLS, alta disponibilidade ou capacidade de producao.
