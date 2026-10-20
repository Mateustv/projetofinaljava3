# Validação do projeto

> Os comandos abaixo foram executados na raiz de `projeto-final-grupo`. Os passos já validados estão marcados `[x]`.

## Validação da E0

- [x] **1. Ambiente**
    ```powershell
    java -version
    mvn -v
    ```
    Resultado: Java 17.0.12 e Maven 3.9.10.
- [x] **2. Compilar, testar e instalar os módulos no repositório Maven local**
    ```powershell
    mvn -B clean install
    ```
    Resultado: `BUILD SUCCESS` nos 4 módulos; 17 testes aprovados. O `install` prepara as dependências locais para iniciar cada serviço separadamente.
- [x] **3. Subir o emissor e verificar o health** (em terminais separados)
    ```powershell
    mvn -pl boleto-emissor spring-boot:run
    curl.exe -i http://localhost:8081/actuator/health
    ```
    Resultado: emissor iniciou; HTTP 200 e `{"status":"UP"}`.
- [x] **4. Subir o registro e verificar o health**
    ```powershell
    mvn -pl boleto-registro spring-boot:run
    curl.exe -i http://localhost:8082/actuator/health
    ```
    Resultado: registro iniciou; HTTP 200 e `{"status":"UP"}`.
- [x] **5. Subir a consulta e verificar o health**
    ```powershell
    mvn -pl boleto-consulta spring-boot:run
    curl.exe -i http://localhost:8083/actuator/health
    ```
    Resultado: consulta iniciou; HTTP 200 e `{"status":"UP"}`. Os três processos foram parados após os testes.

## Validação da E1

- [x] **6. Testes** (a E0 e a E1 rodam juntas no `verify` do passo 2; os testes novos estão no emissor):
    ```powershell
    mvn -B -pl boleto-emissor -am test
    ```
    Resultado: `BUILD SUCCESS`; `EmissaoValidatorTest` (11), `BoletoControllerTest` (3) e `EmissorApplicationTests` (1) verdes.
- [x] **7. POST válido → 202**: resposta HTTP 202 com `identificador`, `status:"AGUARDANDO_REGISTRO"` e `data_hora_recebimento`.
- [x] **8. POST inválido → 400** com convênio `"1"` e valor `0`: resposta HTTP 400 com erros de `convenio` e `valor_nominal`.
- [x] **9. JSON malformado → 400**: resposta HTTP 400.

### Corpos para os testes HTTP 7 e 8 (PowerShell)

Defina o corpo válido uma vez e envie-o:

```powershell
$body = @'
{
  "convenio":"123456","numero_controle_cliente":"NF-0001",
  "beneficiario":{"nome":"Empresa X","tipo_documento":"CNPJ","numero_documento":"12345678000195",
    "endereco":{"logradouro":"Rua A, 1","cidade":"Brasilia","uf":"DF","cep":"70000000"}},
  "pagador":{"nome":"Maria","tipo_documento":"CPF","numero_documento":"12345678909",
    "endereco":{"logradouro":"Rua B, 2","cidade":"Brasilia","uf":"DF","cep":"70000000"}},
  "valor_nominal":150.00,"data_emissao":"2026-10-19","data_vencimento":"2026-11-19","especie":"DM"
}
'@
curl.exe -i -X POST http://localhost:8081/boletos -H "Content-Type: application/json" --data-binary $body
```

Para o teste inválido, altere os dois campos e envie de novo:

```powershell
$bodyInvalido = $body.Replace('"convenio":"123456"', '"convenio":"1"').Replace('"valor_nominal":150.00', '"valor_nominal":0')
curl.exe -i -X POST http://localhost:8081/boletos -H "Content-Type: application/json" --data-binary $bodyInvalido
```

> Nota histórica da E1: naquele momento o POST ainda não persistia o aceite. A persistência e leitura interna foram adicionadas na E2; o fluxo de consulta com fallback foi adicionado na E3.

## Validação da E2

- [x] **10. Testes automatizados de persistência e idempotência**
    ```powershell
    mvn -B -pl boleto-emissor -am test
    ```
    Resultado: `BUILD SUCCESS`; 20 testes do emissor passaram, sem falhas. Inclui repetição igual, conflito `409`, escala decimal equivalente, concorrência, uma linha por chave e consulta interna.
- [x] **11. Repetir emissão → mesmo aceite:** dois POSTs iguais retornaram `202` com o mesmo UUID e a mesma data/hora.
- [x] **12. Mesma chave e conteúdo diferente → conflito:** alterar o valor mantendo convênio e número de controle retornou `409` com `CHAVE_IDEMPOTENCIA_CONFLITANTE`.
- [x] **13. Consultar aceite pelo endpoint interno:** `GET /interno/boletos/{identificador}` retornou `200` e o aceite original.
- [x] **14. Identificador não encontrado:** consulta interna com UUID válido inexistente retornou `404`.
- [x] **15. Build completo após E2**
    ```powershell
    mvn -B clean verify
    ```
    Resultado: `BUILD SUCCESS` nos 4 módulos, 22 testes aprovados no total (20 no emissor).

## Se algo falhar

Cole o trecho `[ERROR]` completo. Pontos prováveis: versão do JDK, `spring-boot-starter-parent` ou `h2` não encontrados no Nexus (então avaliaremos o Plano C), porta ocupada.

## Histórico

| Data  | Etapa   | Resultado |
| ----- | ------- | --------- |
| 19/10 | E0 | Java 17.0.12/Maven 3.9.10; build e 17 testes aprovados; health dos 3 serviços HTTP 200. |
| 19/10 | E1 | 15 testes do emissor aprovados; POST válido 202, POST inválido 400 e JSON malformado 400. |
| 19/10 | ADR-004 | Opção C discutida e aceita pelo grupo; leitura interna implementada em E2 e fallback em E3. |
| 19/10 | E2 | 20 testes do emissor; 22 no build completo. Repetição 202 estável, conflito 409, leitura interna 200 e ausente 404. |
| 19/10 | E3 | 8 testes de consulta; 29 no build completo. Fallback 200, cache hit, ausência 404, UUID inválido 400 e indisponibilidade 503 rastreável. |
| 19/10 | E4 | 22 testes do emissor, 5 do registro e 8 da consulta; 35 no build completo. Emissão HTTP 202, entrega após reinício do emissor e consulta interna 200/REGISTRADO. |
| 20/10 | E5 HTTP | 53 testes: 33 do emissor, 12 do registro e 8 da consulta. Retry limitado, isolamento persistido, rejeição de negócio e compatibilidade com schema E4; broker RabbitMQ/DLQ não implementados. |

## Validação da E3

- [x] **16. Testes automatizados da consulta/cache**
    ```powershell
    mvn -B -pl boleto-consulta -am test
    ```
    Resultado: `BUILD SUCCESS`; 8 testes da consulta aprovados, incluindo política Caffeine de expiração variável.
- [x] **17. Build completo**
    ```powershell
    mvn -B clean verify
    ```
    Resultado: `BUILD SUCCESS` nos 4 módulos, 29 testes aprovados.
- [x] **18. Fallback real (emissor, registro e consulta ligados):** POST válido no emissor e consulta pelo UUID no serviço de consulta. Resultado: HTTP 200, `AGUARDANDO_REGISTRO`.
- [x] **19. UUID existente no cache:** repetir o GET devolveu HTTP 200. O teste automatizado confirmou que a segunda leitura não chamou as fontes novamente.
- [x] **20. UUID válido inexistente:** consulta retornou HTTP 404. Teste automatizado confirmou que 404 não fica no cache.
- [x] **21. UUID malformado:** `/boletos/nao-e-uuid` retornou HTTP 400.
- [x] **22. Falha técnica:** com o serviço de registro indisponível, a consulta retornou HTTP 503 com `correlation_id` e `X-Correlation-ID`, nunca 404.

> Contexto histórico da E3: naquele marco, o serviço de registro ainda não tinha endpoint/dados de leitura; a E4 implementou o consumidor, a persistência e a leitura de `REGISTRADO`.

## Validação da E4

- [x] Testes direcionados do produtor/outbox e consumidor:
    ```powershell
    mvn -B -pl boleto-emissor,boleto-registro -am test
    ```
    Resultado: `BUILD SUCCESS`; 22 testes do emissor e 5 do registro passaram.
- [x] O emissor grava aceite e mensagem outbox na mesma transação; reenvio idempotente não cria uma segunda mensagem.
- [x] O consumidor registra o comando uma vez, responde de forma idempotente à reentrega e rejeita conteúdo conflitante com `409`.
- [x] Falha de transporte mantém o comando pendente para nova tentativa; sucesso remove o payload da outbox.
- [x] Validação manual entre processos: `POST /boletos` retornou `202`/`AGUARDANDO_REGISTRO`; após o despachante, `GET /interno/boletos/{id}` retornou `200`/`REGISTRADO` e `SIM-{id}`.
- [x] Reinício durante indisponibilidade: aceitei um boleto com o registro desligado, reiniciei o emissor, subi o registro e confirmei a entrega posterior do comando persistido.
- [x] `mvn -B clean verify`: `BUILD SUCCESS`; 35 testes nos quatro módulos.

## Validação da E5 provisória por HTTP

- [x] Testes de retry com relógio controlado: três tentativas totais, espera de 1 s e depois 5 s; JSON inválido/nulo isolado imediatamente.
- [x] Testes de integração do produtor com cliente HTTP real, servidor local de teste e H2: erro 503, backoff persistido, recuperação, esgotamento e ausência de novos envios após isolamento.
- [x] Falhas permanentes HTTP 400/409 não repetidas; HTTP 408/429 retentáveis; HTTP 302 não confirma a entrega.
- [x] Lote de 20 mensagens envenenadas isolado sem impedir a entrega da próxima mensagem boa.
- [x] `GET /interno/outbox/falhas` retorna somente metadados; logs não incluem conteúdo livre de exceções.
- [x] Registro simulado com aprovação, rejeição e indisponibilidade. Rejeição é persistida e idempotente, sem dados de pagamento; falha técnica retorna 503 e não deixa registro parcial.
- [x] Compatibilidade do H2: teste de rejeição começa com tabela no formato E4, cujos campos de confirmação eram `NOT NULL`.
- [x] Reentregas concorrentes não duplicam registro; falha na gravação da outbox faz rollback do aceite.
- [x] `mvn -B clean verify`: `BUILD SUCCESS`; 53 testes, sem falhas (33 emissor, 12 registro, 8 consulta).
- [ ] Validação com RabbitMQ, exchanges/bindings e DLQ reais — fora do escopo HTTP aprovado.

Os testes HTTP desta E5 usam servidor local controlado, não os três serviços implantados em processos separados. Os cenários manuais de E4 continuam registrados acima; o roteiro manual atualizado de E5 está em [E5-resiliencia-outbox.md](etapas/E5-resiliencia-outbox.md).
