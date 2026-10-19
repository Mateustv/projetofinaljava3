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
- [ ] **4. Health** (terminal 2, com o emissor no ar)
  ```powershell
  curl http://localhost:8081/actuator/health
  ```
  Esperado: `{"status":"UP"}`. Pare o emissor com `Ctrl+C`.
- [ ] **5. Repetir 3 e 4** para `boleto-registro` (8082) e `boleto-consulta` (8083).

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

## Se algo falhar
Cole o trecho `[ERROR]` completo. Pontos prováveis: versão do JDK, `spring-boot-starter-parent` ou `h2` não encontrados no Nexus (então avaliaremos o Plano C), porta ocupada.

## Histórico
| Data | Etapa | Resultado |
|---|---|---|
| 19/10 | E0 | `mvn clean verify`: sucesso; 17 testes aprovados. Health checks manuais pendentes. |
| 19/10 | E1 | `mvn -pl boleto-emissor -am test`: sucesso; 15 testes aprovados. POSTs manuais pendentes. |
