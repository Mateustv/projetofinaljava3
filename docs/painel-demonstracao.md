# Laboratório web do fluxo real

O painel permite ao grupo emitir solicitações sintéticas e ver **evidências produzidas pelas aplicações**, não uma animação que presume sucesso. É um recurso didático solicitado pelo grupo, não um novo requisito da rubrica nem a conclusão automática da F1.

Registradora e dados bancários continuam simulados. HTTP, bancos H2, outboxes, filas Artemis, eventos e cache executam de verdade. Não há pagamento real.

## 1. Iniciar

Requisitos: JDK 17, Maven e quatro terminais PowerShell. Não precisa de Docker, Node, npm ou internet no navegador.

Na raiz do projeto:

```powershell
mvn -B clean verify
```

Abra quatro terminais **inicialmente na raiz do projeto**, sem outra instância dos serviços nas portas 8081–8083. Usamos 61617 e um journal separado para não misturar mensagens com a execução normal.

**Terminal 1 — broker:**

```powershell
Set-Location .\cobranca-broker
java -jar .\target\cobranca-broker-0.0.1-SNAPSHOT-exec.jar --port=61617 --data=..\data\demonstracao-broker
```

Espere “Broker pronto”.

**Terminal 2 — registro:**

```powershell
Set-Location .\boleto-registro
$env:BROKER_URL='tcp://127.0.0.1:61617?blockOnDurableSend=true&callTimeout=2000&initialConnectAttempts=1&reconnectAttempts=0'
java -jar .\target\boleto-registro-0.0.1-SNAPSHOT.jar --spring.profiles.active=demonstracao
```

**Terminal 3 — emissor:**

```powershell
Set-Location .\boleto-emissor
$env:BROKER_URL='tcp://127.0.0.1:61617?blockOnDurableSend=true&callTimeout=2000&initialConnectAttempts=1&reconnectAttempts=0'
java -jar .\target\boleto-emissor-0.0.1-SNAPSHOT.jar --spring.profiles.active=demonstracao
```

**Terminal 4 — consulta:**

```powershell
Set-Location .\boleto-consulta
$env:BROKER_URL='tcp://127.0.0.1:61617?blockOnDurableSend=true&callTimeout=2000&initialConnectAttempts=1&reconnectAttempts=0'
java -jar .\target\boleto-consulta-0.0.1-SNAPSHOT.jar --spring.profiles.active=demonstracao
```

Confira `http://127.0.0.1:8081/actuator/health`, repetindo para 8082 e 8083: HTTP 200/`UP`. Abra **http://127.0.0.1:8083/demonstracao**.

O perfil precisa estar ativo nos **três serviços**. Ele liga rastros, restringe o bind a `127.0.0.1` e separa os bancos em `data\demonstracao\emissor` / `data\demonstracao\registro` dos respectivos módulos. Caminhos dependem do diretório de execução; não mude esse diretório entre reinícios. Não apague bancos ou journal para repetir um teste: gere outro controle.

Se precisar mudar portas, altere também `BROKER_URL` nos três serviços e, para HTTP, as propriedades da consulta `integracoes.emissor.base-url` / `integracoes.registro.base-url`. O proxy didático só aceita `http://localhost:porta` ou `http://127.0.0.1:porta`, sem caminhos, credenciais ou redirecionamentos. Não encerre processos de outras pessoas para liberar portas.

Pare primeiro os serviços, depois o broker, com **Ctrl+C**. Sem `--spring.profiles.active=demonstracao`, as rotas/recursos do painel não existem e os prefixos didáticos não alteram a registradora.

## 2. O que aparece na tela

| Área | Como interpretar |
|---|---|
| Solicitação editável | JSON enviado pelo proxy ao POST real do emissor; valores gerados são sintéticos |
| Aceite + outboxes | Rastro gerado **após commit H2** do aceite, comando e intenção de evento |
| Comando na fila | Publicador retornou após commit JMS; não comprova registro concluído |
| Consumo e resultado | Entrega informada pelo Artemis, chamada da registradora e commit do resultado; retorno do listener não comprova o ack posterior |
| Evento de domínio | Publicação e recebimento reais, identificados pelo UUID do evento |
| Cache e invalidação | Miss, preenchimento, hit e evicção coordenada realmente executados |
| Quarentena | Confirmação somente se o browse encontrar mensagem deste UUID no broker; não consome nem reenvia |
| Linha do tempo | Etapas, serviço, horário e correlação dos rastros retidos |
| HTTP | Status, correlação e JSON reais das últimas 30 emissões/consultas da página, incluindo erros |

Cada requisição HTTP tem sua própria correlação. Comandos/eventos preservam a correlação da emissão; clicar em consultar inicia outra requisição, não reutiliza artificialmente a correlação anterior.

O acompanhamento consulta a API e o diagnóstico a cada aproximadamente 1,5 s após o ciclo anterior e pausa em 45 s. **Pausar não cancela processamento no backend.** Os botões de consulta/atualização continuam disponíveis.

## 3. Experimentos

### Aprovação e caminho completo

1. Escolha **Aprovar registro** e clique em **Emitir e acompanhar**.
2. Veja o POST `202`, UUID e estado inicial: é aceite, não registro.
3. Acompanhe `ACEITE_PERSISTIDO`, `COMANDO_PUBLICADO`, `COMANDO_RECEBIDO`, `REGISTRADORA` e `RESULTADO_PERSISTIDO`.
4. A API de consulta deve mostrar `REGISTRADO`; expanda o JSON e veja os marcadores `NAO-PAGAVEL-SIM-...`.
5. Veja `EVENTO_PUBLICADO`, `EVENTO_RECEBIDO` e `CACHE_INVALIDADO`.

O processamento pode ser rápido demais para a consulta capturar o estado aguardando. Não há atrasos artificiais para fabricar essa transição. Publicação/consumo podem se sobrepor.

### Idempotência e conflito

1. Após uma emissão aceita, clique em **Repetir o mesmo pedido**: deve retornar `202` com o **mesmo UUID**, rastro `IDEMPOTENCIA` e sem novo aceite/comando de negócio.
2. Clique em **Testar conflito 409**: o painel mantém convênio/controle e muda apenas o valor.
3. Confira `409` e `CONFLITO`; o resultado anterior não deve mudar.

Esses botões usam o último pedido aceito, não eventuais alterações posteriores no textarea. Para enviar outra solicitação, gere um novo controle.

### Cache e invalidação

1. Emita e procure `CACHE_MISS` / `CACHE_PREENCHIDO`.
2. Clique em **Consultar (ver cache)** mais de uma vez; procure `CACHE_HIT`.
3. Veja o evento final e `CACHE_INVALIDADO`. A invalidação também pode ocorrer quando ainda não havia entrada: não significa necessariamente que uma resposta antiga foi vista.
4. Após invalidação, uma nova consulta preenche o cache com o estado final.

O TTL original permanece: 30 s para estado transitório e 10 min para final. Ele não garante que a página capture um estado transitório quando o registro for rápido. Indisponibilidade do registro deve produzir `503` fora do cache, não um fallback indevido para criar essa janela.

### Rejeição de negócio

1. Selecione **Rejeitar por regra de negócio**; isso gera controle `DEMO-R-...`.
2. Emita e acompanhe até `REJEITADO`.
3. Veja código/motivo no JSON, resultado persistido e evento de rejeição.
4. Não espere retry ou DLQ por uma rejeição de negócio.

### Falha técnica, retry e quarentena

1. Selecione **Falhar: retry e quarentena**, com controle `DEMO-F-...`.
2. Emita e veja aceite `202`: o problema ocorre depois.
3. Procure entregas reais **1, 2 e 3**, chamadas da simulação e `FALHA_PROCESSAMENTO`.
4. Após esgotamento, o cartão de quarentena só confirma se encontrar a mensagem deste UUID por browse.
5. A consulta pode permanecer `AGUARDANDO_REGISTRO`: falha técnica não inventa rejeição de negócio.

Artemis mantém os intervalos e orçamento existentes (três entregas, esperas mínimas 1 s/5 s). Não há retry extra do painel, reprocessamento da DLQ ou mudança de um resultado já persistido. Alterar o cenário no seletor prepara **outro pedido**.

### Validação e indisponibilidade HTTP

- Edite o JSON para convênio inválido e veja `400`; JSON malformado é identificado na página antes do envio.
- Pare **seu** emissor e tente emitir: o proxy mostra `503`, sem inventar UUID.
- Para o circuito HTTP, pare **seu** registro, gere/emita um novo pedido e observe as consultas fora do cache. Três falhas técnicas podem abrir o circuito; procure `HTTP_FONTE_FALHOU` e `CIRCUITO_ABERTO`. O `503` não é um estado de negócio e não autoriza fallback ao emissor.
- Reinicie a fonte para continuar; a recuperação do circuito respeita o período original de 10 s e a sonda half-open. O painel não administra processos.

## 4. Implementação e limites

- [PainelDemonstracaoController](../boleto-consulta/src/main/java/br/com/caixa/cobranca/consulta/controllers/PainelDemonstracaoController.java) serve os recursos locais e agrega diagnósticos; o navegador usa mesma origem, sem CORS aberto. Destinos são fixos na configuração, sem proxy de URL arbitrária.
- [Rastros](../infraestrutura-http/src/main/java/br/com/caixa/cobranca/http/demo/Rastros.java), [configuração](../infraestrutura-http/src/main/java/br/com/caixa/cobranca/http/demo/DemonstracaoConfiguration.java) e [endpoint](../infraestrutura-http/src/main/java/br/com/caixa/cobranca/http/demo/RastrosController.java) mantêm os **últimos 2.000 rastros globais por JVM**, compartilhados entre os boletos daquela JVM. Só filtramos por UUID na leitura. Reinício limpa; não há persistência nem prazo de retenção garantido.
- [QuarentenaDemonstracaoController](../boleto-registro/src/main/java/br/com/caixa/cobranca/registro/controllers/QuarentenaDemonstracaoController.java) examina no máximo 200 mensagens. Se atingir o limite, ausência é **inconclusiva**; a mensagem procurada pode estar depois da janela.
- Relógios podem divergir; ordenar horários não prova uma ordem causal global. A página acumula até 6.000 rastros recebidos do UUID atual até recarregar/trocar de boleto, mesmo se saírem da janela do servidor; acima disso descarta os primeiros recebidos.
- Se uma fonte falhar ou estiver sem perfil, a tela informa indisponibilidade. Ausência de rastro não comprova ausência de execução. Transações revertidas não geram rastros de commit, mas tentativas/falhas podem permanecer no diagnóstico.
- Recursos HTML/JS/CSS ficam fora de `static`; somente controllers do perfil os servem. Sem perfil, endpoints retornam `404` e a instrumentação não acumula rastros.
- `DEMO-A-` / `DEMO-R-` / `DEMO-F-` escolhem respectivamente aprovação, rejeição e indisponibilidade quando o diagnóstico está ativo, independentemente do cenário padrão. Fora do perfil, continua valendo `REGISTRADORA_CENARIO` como antes.
- Não é auditoria, monitoramento de produção ou controle de segurança. Não há autenticação nem uso de dados reais; não exponha serviços à rede. Os bancos/journal de demonstração persistem dados sintéticos, mesmo quando os rastros são perdidos.

O [guia do projeto](guia-do-projeto.md) explica os conceitos e o catálogo completo; o [roteiro](../ROTEIRO.md) mantém as pendências de entrega do grupo.
