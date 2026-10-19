# Validação pendente (rodar na rede corporativa)

> O assistente não consegue acessar o Nexus (`binario.caixa`) fora da rede do trabalho.
> Quando estiver na rede corporativa/VPN, rode os comandos abaixo **na raiz** de `projeto-final-grupo` e **cole a saída** na conversa. Marque `[x]` quando validar.

## Validação da E0
- [ ] **1. Ambiente**
  ```powershell
  java -version
  mvn -v
  ```
  Esperado: Java 17.x e Maven 3.9.x.
- [ ] **2. Compilar tudo e rodar os smoke tests**
  ```powershell
  mvn -B clean verify
  ```
  Esperado: `BUILD SUCCESS` com 4 módulos (`shared-contracts`, `boleto-emissor`, `boleto-registro`, `boleto-consulta`) e 3 testes passando.
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

## Se algo falhar
Cole o trecho `[ERROR]` completo. Pontos prováveis: versão do JDK, `spring-boot-starter-parent` ou `h2` não encontrados no Nexus (então avaliaremos o Plano C), porta ocupada.

## Histórico
| Data | Etapa | Resultado |
|---|---|---|
| 19/10 | E0 | _pendente_ |
