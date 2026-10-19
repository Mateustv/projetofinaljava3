# Validação pendente (rodar na rede corporativa)

> Rode os comandos abaixo **na raiz** de `projeto-final-grupo` e cole a saída na conversa se houver erro. Os passos já validados estão marcados `[x]`.

## Validação da E0
- [ ] **1. Ambiente**
  ```powershell
  java -version
  mvn -v
  ```
  Esperado: Java 17.x e Maven 3.9.x.
- [x] **2. Compilar tudo e rodar os testes**
  ```powershell
  mvn -B clean verify
  ```
  Resultado: `BUILD SUCCESS` nos 4 módulos (`shared-contracts`, `boleto-emissor`, `boleto-registro`, `boleto-consulta`), 17 testes aprovados.
- [ ] **3. Subir o emissor** (terminal 1)
  ```powershell
  mvn -pl boleto-emissor spring-boot:run
  ```
- [ ] **4. Health do emissor** (terminal 2, com o emissor no ar)
  ```powershell
  curl.exe -i http://localhost:8081/actuator/health
  ```
  Esperado: HTTP 200 e `{"status":"UP"}`.
- [ ] **5. Health do registro e da consulta:** em terminais separados, suba `boleto-registro` e `boleto-consulta` com `mvn -pl boleto-registro spring-boot:run` e `mvn -pl boleto-consulta spring-boot:run`. Com cada serviço no ar, rode `curl.exe -i http://localhost:8082/actuator/health` e `curl.exe -i http://localhost:8083/actuator/health`. Esperado: HTTP 200 e `{"status":"UP"}` para ambos. Pare os três serviços com `Ctrl+C`.

## Validação da E1
- [x] **6. Testes** (a E0 e a E1 rodam juntas no `verify` do passo 2; os testes novos estão no emissor):
  ```powershell
  mvn -B -pl boleto-emissor -am test
  ```
  Resultado: `BUILD SUCCESS`; `EmissaoValidatorTest` (11), `BoletoControllerTest` (3) e `EmissorApplicationTests` (1) verdes.
- [ ] **7. POST válido → 202** (emissor no ar na 8081): usar o `curl` do §6 de `docs/etapas/E1-ddd-e-emissao.md`.
  Esperado: `HTTP/1.1 202`, corpo com `identificador`, `status:"AGUARDANDO_REGISTRO"`, `data_hora_recebimento`.
- [ ] **8. POST inválido → 400**: repetir com `"convenio":"1"` e `"valor_nominal":0`.
  Esperado: `HTTP/1.1 400` com `erros` contendo `convenio` e `valor_nominal`.
- [ ] **9. JSON malformado → 400:** com o emissor no ar, rode `curl.exe -i -X POST http://localhost:8081/boletos -H "Content-Type: application/json" --data-binary "{nao e json"`. Esperado: HTTP 400.

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
| Data | Etapa | Resultado |
|---|---|---|
| 19/10 | E0 | `mvn clean verify`: sucesso; 17 testes aprovados. Health checks manuais pendentes. |
| 19/10 | E1 | `mvn -pl boleto-emissor -am test`: sucesso; 15 testes aprovados. POSTs manuais pendentes. |
| 19/10 | ADR-004 | Opção C aceita pelo solicitante; compartilhar com o grupo. Implementação prevista para E2/E3. |
