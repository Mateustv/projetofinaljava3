# E3 — Consulta e cache-aside

## Objetivo
Permitir que o cliente consulte o boleto pelo UUID, veja o estado atual e receba uma resposta rápida quando repetir consultas.

## Fluxo em linguagem simples
1. O cliente chama `GET /boletos/{identificador}` no `boleto-consulta`.
2. A consulta procura o UUID no cache Caffeine.
3. Se encontrar uma resposta ainda válida, devolve-a sem chamar outros serviços.
4. Se não encontrar no cache, consulta o `boleto-registro`, dono do estado final.
5. Se o registro responder **404**, a consulta usa o endpoint interno do emissor. Esse é o fallback C do ADR-004: o emissor conhece o aceite enquanto o registro ainda não recebeu/processou a emissão.
6. Se as duas fontes responderem 404, o UUID não existe. A resposta não é armazenada; uma nova consulta repete a busca.
7. Se uma fonte necessária falhar tecnicamente, o resultado é **503**, com `correlation_id`; não se mascara falha como 404.
8. Uma resposta encontrada entra no cache: 30 segundos se `AGUARDANDO_REGISTRO`, 10 minutos se `REGISTRADO` ou `REJEITADO`. Os valores são provisórios.

## Contrato de resposta

| Estado | Campos disponibilizados |
|---|---|
| `AGUARDANDO_REGISTRO` | identificador, status, data/hora de recebimento |
| `REGISTRADO` | campos anteriores, linha digitável, código de barras, identificador definitivo da registradora e data/hora do registro |
| `REJEITADO` | identificador, status, data/hora de recebimento, código e motivo da rejeição; sem dados de pagamento |

Dados que não pertencem ao estado recebido são removidos da projeção, mesmo se uma fonte os incluir por engano.

## Arquivos e responsabilidades

| Elemento | Papel |
|---|---|
| `BoletoConsulta` em `shared-contracts` | projeção de leitura compartilhada entre a consulta e as fontes |
| `RestBoletoFonteClient` | chama endpoints internos do registro e emissor e diferencia HTTP 404 de erros técnicos |
| `ConsultaBoletoService` | executa a ordem registro → fallback emissor e aplica cache-aside |
| `ConsultaCacheConfiguration` | cache Caffeine limitado a 10.000 entradas, com expiração variável por status |
| `ConsultaBoletoController` | expõe `GET /boletos/{identificador}` e valida UUID |
| `ConsultaExceptionHandler` | mapeia ausência para 404, UUID malformado para 400 e falha de fonte para 503 rastreável |
| ADR-006 | registra estratégia, alternativas e caráter provisório dos TTLs |

## Como validar
Na raiz do projeto:

```powershell
mvn -B -pl boleto-consulta -am test
mvn -B clean verify
```

Os testes automatizados cobrem cache hit (segunda leitura não chama as fontes), TTL conforme o estado, fallback, estados finais, remoção de dados que não devem ser expostos, não cachear 404, 400 para UUID malformado e 503 para falha técnica.

Com emissor, registro e consulta em execução, crie uma emissão pelo `POST /boletos`, copie o UUID e consulte:

```powershell
curl.exe -i http://localhost:8083/boletos/COLE-O-UUID-AQUI
```

O registro agora é assíncrono: uma consulta muito rápida pode devolver `AGUARDANDO_REGISTRO`; depois que o consumidor processar, o estado passa a `REGISTRADO`. O cache pode manter a projeção transitória por até 30 segundos. Consultar UUID válido inexistente deve retornar 404; caminho com texto que não seja UUID deve retornar 400.

## Contexto histórico
Quando a E3 foi validada, o serviço de registro ainda não possuía endpoint interno nem registros de boletos; por isso, aquela validação exercitou o fallback ao emissor. A E4 implementou o consumidor, a persistência do registro e a rota interna de leitura de `REGISTRADO`.
