# E2 — Persistência do aceite e idempotência

## Objetivo
Fazer com que o aceite deixe de existir apenas na resposta HTTP: o emissor grava o resultado antes de responder e reconhece reenvios da mesma solicitação.

## Conceitos em linguagem simples

- **Persistir** é gravar dados numa base para que o serviço possa recuperá-los depois da requisição. Nesta etapa, o aceite é guardado na base H2 exclusiva do emissor.
- **Chave de idempotência** é a identidade que o cliente escolhe para uma operação repetível. Neste projeto, é a combinação `convenio + numero_controle_cliente`.
- **Idempotência** significa que repetir a mesma operação não cria um segundo efeito. A primeira chamada cria o aceite; uma repetição igual recebe a resposta original.
- **409 Conflict** indica que a chave já está ocupada por outro conteúdo. Não substituímos silenciosamente uma emissão já aceita.
- **Restrição única no banco** é a última proteção contra duas requisições simultâneas. Uma checagem apenas em Java teria uma corrida: ambas poderiam consultar “não existe” antes de tentar gravar.
- **Fingerprint** é uma impressão digital criptográfica do pedido. Guardamos o SHA-256 para comparar reenvios sem usar os dados pessoais como chave de comparação.

## Fluxo implementado
1. O emissor valida a solicitação. Se for inválida, ainda responde `400` e não persiste nada.
2. A aplicação cria uma representação canônica do conteúdo e calcula seu SHA-256. A canonicalização ordena as propriedades JSON e normaliza números decimais; assim, `150.0` e `150.00` representam o mesmo valor.
3. O emissor procura a chave composta no H2.
4. Se encontrar a chave e a impressão digital for igual, devolve o `BoletoAceito` guardado: mesmo UUID, estado e data/hora.
5. Se encontrar a chave com outra impressão digital, responde `409` com código `CHAVE_IDEMPOTENCIA_CONFLITANTE`, sem expor os dados da solicitação.
6. Se a chave ainda não existir, grava o aceite e a solicitação numa única linha. Travas locais por faixa serializam, na instância, requisições concorrentes da mesma chave; a restrição única no banco continua sendo a proteção final.
7. O emissor só responde `202` após a gravação bem-sucedida. Se não conseguir ler/gravar com segurança, responde `503`, não um sucesso falso.

## O que foi adicionado

| Elemento | Responsabilidade |
|---|---|
| `EmissaoAceitaEntity` | Linha persistida com UUID, chave única, resposta original, fingerprint e cópia JSON da solicitação |
| `EmissaoAceitaRepository` | Consulta e grava os aceites usando Spring Data JPA |
| `EmissaoService` | Valida, calcula fingerprint, detecta repetição/conflito e persiste |
| `ConsultaInternaBoletoController` | `GET /interno/boletos/{identificador}` para a consulta recuperar o aceite inicial (ADR-004, opção C) |
| `ErroHandler` | Converte conflito em `409` e falha de persistência em `503` |
| `BoletoControllerTest` | Verifica `202`, reenvio igual, conflito `409`, escala decimal equivalente, concorrência, contagem de registros e consulta interna |

## Por que guardar também o JSON da solicitação?
A impressão digital basta para comparar reenvios, mas o conteúdo é necessário para criar o `RegistrarBoletoCommand` da E4. O JSON pode conter dados pessoais. No perfil local, o aceite e sua solicitação ficam no H2 em arquivo; a cópia temporária do comando na outbox é removida depois da confirmação do registro. O conteúdo não é incluído em respostas de erro nem deve ser escrito em logs. Uma implantação real exigiria controles de acesso, proteção da base, retenção e avaliação de criptografia.

## Decisão arquitetural: SAGA e outbox
A [ADR-005](../adr/ADR-005-saga-e-outbox-no-mvp.md) registra a decisão histórica de não incluir outbox na E2. Na E4, a decisão foi revista: o aceite e a intenção de registrar agora são gravados na mesma transação; o POST continua sem chamar o serviço de registro.

## Limites do marco E2
- Quando a E2 foi concluída, o H2 estava em memória e o status ficava `AGUARDANDO_REGISTRO`, pois ainda não existia consumidor. Na E4, a execução passou a usar H2 em arquivo e o consumidor assíncrono passou a atualizar o estado.
- As travas são locais à instância; não substituem a restrição única do banco.
- O endpoint `/interno/...` continua sem autenticação/autorização entre serviços. Em uma implantação real, ele deve ficar restrito a uma rede confiável e receber controles de acesso.

## Como provar
Na raiz de `projeto-final-grupo`:

```powershell
mvn -B -pl boleto-emissor -am test
```

Os testes de integração exercitam o endpoint e o banco H2:
- mesma chave + mesmo corpo → duas respostas `202`, mesmo UUID, uma linha;
- mesma chave + valor diferente → segunda resposta `409`, uma linha;
- mesmo conteúdo monetário com escalas diferentes (`150.0` e `150.00`) → mesma resposta;
- duas chamadas concorrentes com mesma chave e conteúdo → mesmo UUID, uma linha;
- consulta do UUID recém-aceito no endpoint interno → `200` com o mesmo aceite.

Para um teste HTTP manual, suba o emissor com `mvn -pl boleto-emissor spring-boot:run`, envie o POST válido documentado na E1 e copie o UUID da resposta:

```powershell
curl.exe -i http://localhost:8081/interno/boletos/COLE-O-UUID-AQUI
```

Esperado: HTTP `200`, o mesmo identificador e `AGUARDANDO_REGISTRO`. Um UUID válido que não exista deve retornar `404`.
