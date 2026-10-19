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

> Importante: em E1, o POST de aceite ainda não persiste o boleto. O 202 prova validação e formato da resposta, mas o identificador não estará disponível para consulta após a requisição. Persistência e fallback do ADR-004 serão implementados em E2/E3.

## Se algo falhar

Cole o trecho `[ERROR]` completo. Pontos prováveis: versão do JDK, `spring-boot-starter-parent` ou `h2` não encontrados no Nexus (então avaliaremos o Plano C), porta ocupada.

## Histórico

| Data  | Etapa   | Resultado |
| ----- | ------- | --------- |
| 19/10 | E0 | Java 17.0.12/Maven 3.9.10; build e 17 testes aprovados; health dos 3 serviços HTTP 200. |
| 19/10 | E1 | 15 testes do emissor aprovados; POST válido 202, POST inválido 400 e JSON malformado 400. |
| 19/10 | ADR-004 | Opção C aceita pelo solicitante; compartilhar com o grupo. Implementação prevista para E2/E3. |
