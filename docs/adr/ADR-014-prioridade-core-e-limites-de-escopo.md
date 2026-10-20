# ADR-014 - Prioridade ao CORE geral e requisitos nao atendidos

## Contexto

O brief geral exige bases segregadas, fluxo assincrono com consumidor idempotente, cache com invalidacao, contrato executavel, ADRs/README e execucao em um perfil. A rubrica tambem avalia resiliencia, testabilidade e uso critico de IA. Nao existe criterio separado de seguranca ou bonus garantido por implementar autenticacao.

Os requisitos do tema boletos sao mais amplos: a Sessao 5 exige controle de acesso por papel/convenio, auditoria, criptografia e retencao. O gabarito PIX e referencia de padroes, nao implementacao desses requisitos especificos.

Ao iniciar E11 em 20/10/2026, o solicitante escolheu explicitamente **revisar o planejamento para priorizar somente o CORE geral, registrando os requisitos da Sessao 5 como nao atendidos**. Nenhum codigo de seguranca foi implementado nessa etapa.

## Decisao de escopo da entrega

- Preservar E0-E10, inclusive eventos, resiliencia e correlacao ja entregues. Nao remover funcionalidades ou testes por ultrapassarem o CORE minimo.
- E11 fica **fora da entrega atual, nao implementada**, e nao "concluida" nem "nao aplicavel" como os opcionais E13.
- Nao implementar nesta entrega autenticacao/autorizacao por convenio, usuarios/papeis, credenciais tecnicas, TLS, criptografia gerenciada ou trilha persistida de auditoria.
- US-10 e US-11 nao atendidas integralmente; logs/correlacao e minimizacao parcial existentes nao substituem esses requisitos.
- US-12/retencao formal da Sessao 5 tambem nao atendida. Cinco anos permanece hipotese, sem politica implementada, conformidade ou expurgo alegados.
- Replay restrito/auditado das Sessoes 4/5 e da US-07 permanece nao atendido. Nao criar endpoint de replay sem a protecao retirada do escopo. Isolamento/retry/quarentena existentes permanecem.
- E12 passa a **preparacao operacional minima da demonstracao**, sem desenvolvimento de replay ou retencao: runbook de inicio/parada, preservacao, copia/restauracao local e recuperacao ja suportada. E14 deve verificar esses procedimentos e os limites reais, nao inventar auditoria/autorizacao.
- E14 continua obrigatoria para aceite integrado, contratos/build, cache, falhas, dados sinteticos e reproducao. Metas de desempenho sao premissas: medir ou registrar expressamente como nao validadas, sem atribuir resultados inexistentes.
- E15 continua por ultimo, depois de E12 revisada e E14; autoavaliacao precisa explicitar a reducao e os requisitos nao atendidos.

## Limites e riscos aceitos no planejamento

A decisao e do solicitante para o planejamento, **nao aprovacao do professor ou dispensa institucional**. Caso o docente exija todos os requisitos do tema, sera necessario retomar E11 e a parte adiada de E12. Nao garantir nota maxima nem pontos adicionais.

Sistema permanece demonstrador local, somente com dados sinteticos, servicos iniciados em loopback conforme README, sem acesso por rede compartilhada ou uso real. Restricao local nao equivale a autenticacao, autorizacao por convenio ou criptografia. Nao foram verificados/ativados volume criptografado, certificados ou mecanismos de seguranca do Windows. A preferencia anteriormente indicada para armazenamento nao representa protecao implementada.

H2/journal/quarentena podem conter payload necessario ao processamento. Nao enviar bancos, journal, logs de dados ou segredos ao Git. Nao apagar dados para contornar erro, liberar replay indiscriminado ou apresentar logs tecnicos como auditoria completa.

## Alternativas

- Implementar E11 completa com contas locais/HTTPS e broker autenticado: atende melhor ao tema, mas o solicitante optou por nao incluir esse trabalho na entrega atual.
- Pausar para confirmar exigencias com o professor: alternativa apresentada, nao escolhida. A confirmacao docente continua recomendavel se houver duvida sobre o escopo avaliado.
- Marcar seguranca como entregue ou eliminar requisitos das transcricoes: rejeitado; seria esconder lacunas e alterar a fonte historica.

## Status

Aceita pelo solicitante para a entrega atual, 20/10/2026. Revisao **somente documental**, sem alteracao de codigo ou nova evidencia de testes funcionais. Ultima versao funcional: E10, commit `0ae4139`, 265 testes aprovados. Substitui o planejamento anterior de E11/E12 e seus portoes, sem reescrever a Sessao 5 ou declarar conformidade.
