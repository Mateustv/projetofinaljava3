# E5 — Resiliência provisória na outbox HTTP

## O que mudou

A outbox não tenta mais entregar um comando indefinidamente. Agora diferencia erro temporário, erro permanente e rejeição de negócio:

| Situação | Ação |
|---|---|
| Conexão/timeout, HTTP 408, 429 ou 5xx | Retry; no máximo três tentativas totais |
| Outros HTTP 4xx | Isolamento imediato para análise |
| JSON inválido ou nulo | Isolamento imediato, sem enviar ao registro |
| Terceira falha temporária | Isolamento persistido, sem novos envios automáticos |
| Registradora recusa o título | Persistir `REJEITADO`, confirmar a entrega; não é falha técnica |

**Backoff** significa esperar antes de tentar outra vez. A segunda tentativa só fica elegível 1 segundo após a primeira falha; a terceira, 5 segundos após a segunda. O relógio e os horários persistidos são testados sem `sleep`; o polling e o tempo de rede podem aumentar a espera real.

Os itens em backoff, entregues ou isolados não entram no lote de até 20 comandos elegíveis. Mensagens boas continuam seguindo mesmo depois de mensagens inválidas. A entrega usa timeouts de conexão de 2 s e leitura de 5 s.

## Isolamento não é rejeição de negócio

Uma mensagem isolada tem problema técnico/de contrato ou esgotou tentativas. Ela continua guardada no H2 do emissor para investigação. O boleto pode permanecer `AGUARDANDO_REGISTRO`; não inventamos uma rejeição da registradora quando a rede falhou.

Uma rejeição de negócio é uma resposta válida: o registro grava `REJEITADO` com `codigo_rejeicao` e `motivo_rejeicao`. Não grava dados de pagamento, identificador da registradora ou data de registro. A resposta HTTP confirma o processamento, e a outbox é concluída normalmente.

O `POST /boletos` idempotente continua retornando o aceite original; ele não reinicia a entrega de um comando isolado.

## Como observar

Com o emissor em execução:

```powershell
Invoke-RestMethod http://localhost:8081/interno/outbox/falhas
```

A API retorna uma lista com até 20 falhas definitivas recentes: `identificador`, `tentativas`, `tipo_erro` e `falha_em`. Não retorna o payload. Os logs também registram apenas metadados; corpos de respostas e mensagens livres de exceção não são escritos.

O payload original dos itens isolados continua no banco, para análise restrita. Não foi adicionado endpoint de replay. Reprocessar exige corrigir a causa e verificar o resultado já existente antes de planejar um reenvio seguro. Não apague os bancos para “resolver” uma falha.

## Como simular a registradora

O modo vale para novos processamentos e é definido no processo do **registro**. `APROVAR` é o padrão. Para testar rejeição, em um terminal PowerShell na raiz do projeto:

```powershell
$env:REGISTRADORA_CENARIO = 'REJEITAR'
mvn -pl boleto-registro spring-boot:run
```

Em outro terminal, execute o emissor e envie um boleto válido com nova chave de idempotência (corpo de exemplo em [validação](../validacao-pendente.md)). O POST responde `202`/`AGUARDANDO_REGISTRO`; depois do despacho, a consulta interna do registro devolve `REJEITADO` e `SIM-REJ-001`. A consulta pública também projeta a rejeição; se já houver estado transitório no cache, espere seu TTL de até 30 segundos.

Para simular falha técnica, pare somente o processo de registro iniciado para esse teste e inicie-o com `REGISTRADORA_CENARIO=INDISPONIVEL`. Um novo boleto causará HTTP 503 no transporte interno, duas novas tentativas e, se a falha persistir, aparecerá na lista de falhas definitivas do emissor.

Ao encerrar o teste, remova a variável nesse terminal antes de iniciar o registro novamente:

```powershell
Remove-Item Env:\REGISTRADORA_CENARIO
```

Os registros já concluídos não mudam quando o modo de simulação muda; reentrega continua idempotente.

## Persistência e compatibilidade

Emissor e registro continuam usando H2 em arquivo na execução local e em memória nos testes. Os novos campos da outbox são nullable para preservar comandos da E4. O script `schema.sql` do registro remove as duas restrições antigas que impediam persistir `REJEITADO`; ele roda após a atualização JPA, sem excluir dados. O teste de rejeição parte de uma tabela no formato E4 para comprovar a compatibilidade.

## Testes

```powershell
mvn -B -pl boleto-emissor,boleto-registro -am test
mvn -B clean verify
```

- Intervalos exatos de 1 s e 5 s e limite de três tentativas totais.
- Persistência do backoff, erro e isolamento; itens não elegíveis não são reenviados.
- Falha HTTP real 503 seguida de recuperação e confirmação.
- HTTP 400/409 permanentes; 408/429 retentáveis; redirecionamento não é confirmação.
- JSON envenenado/nulo isolado e mensagem boa processada após um lote inválido.
- Consulta de falhas sem payload e logs sem conteúdo livre de exceções.
- Rejeição persistida e idempotente, com campos de pagamento ausentes.
- Falha técnica 503 sem registro parcial; nova tentativa pode recuperar.
- Reentregas concorrentes sem duplicação e rollback do aceite quando a outbox não pode ser gravada.

Os testes HTTP da outbox usam servidor local de teste, o cliente real de transporte e H2; não exigem RabbitMQ ou serviços externos. Os testes do registro exercitam os controllers e a persistência. Isso não substitui uma futura validação ponta a ponta de broker.

## O que ainda não foi entregue

Esta é a **parte de resiliência da E5 adaptada ao transporte HTTP**, aprovada pelo grupo. Não implementa RabbitMQ, exchange, binding, DLX ou DLQ de broker.

**Atualização após a revisão do roteiro:** comandos serão migrados para broker JVM na E8, sem Docker; eventos pelo mesmo broker na E9. PACT foi entregue na [E6](E6-contratos-pact.md). O fechamento foi movido para E15 e continua bloqueado até o aceite integrado.

Detalhes da decisão: [ADR-008](../adr/ADR-008-retry-e-isolamento-outbox.md).
