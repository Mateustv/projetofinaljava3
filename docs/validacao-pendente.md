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

> Nota histórica da E1: naquele momento o POST ainda não persistia o aceite. A persistência e a leitura interna foram adicionadas na E2; o fallback completo, passando pelo serviço de consulta, continua para E3.

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
| 19/10 | ADR-004 | Opção C aceita pelo solicitante; compartilhar com o grupo. Implementação prevista para E2/E3. |
| 19/10 | E2 | 20 testes do emissor; 22 no build completo. Repetição 202 estável, conflito 409, leitura interna 200 e ausente 404. |
