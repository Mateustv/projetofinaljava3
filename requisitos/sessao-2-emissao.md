# Sessão 2 — Emissão do boleto

**Projeto:** Central de Cobrança e Boletos · **Data:** 13/04/2026, 14h00–14h30 · **Duração:** 30 min · **Plataforma:** Microsoft Teams
**Participantes:** Marina, Daniel, Roberto, Sandra e Caio

> Trechos marcados com ➕ foram acrescentados ao original.

---

**Marina:** Precisamos fechar quem solicita, quais dados entram e o significado da resposta.

**Daniel:** A entrada será sistema a sistema, por POST. ERPs e canais corporativos enviam a solicitação.

**Caio:** Dados essenciais: convênio; beneficiário; pagador; valor nominal; emissão; vencimento; espécie; número de controle do cliente. Juros, multa, desconto, abatimento, instruções e mensagem são condicionais.

➕ **Marina:** Detalha o formato de cada um, Caio, porque o time vai validar na entrada.

➕ **Caio:** **Convênio:** 6 dígitos numéricos (string, zero à esquerda importa). **Beneficiário** e **pagador:** nome, tipo de documento (CPF/CNPJ), número do documento e endereço (logradouro, cidade, UF com 2 letras, CEP com 8 dígitos). **Valor nominal:** maior que zero, duas casas decimais. **Emissão:** data; **vencimento:** data **igual ou posterior** à emissão. **Espécie:** código do tipo de título (ex.: DM — duplicata mercantil, DS — duplicata de serviço). **Número de controle do cliente:** texto de até 25 caracteres, escolhido pelo emissor. **[PREMISSA]** tamanhos e lista de espécies: validar com o contrato da registradora.

➕ **Caio:** Condicionais: **juros** e **multa** exigem tipo (valor fixo ou percentual) e valor, e a data a partir da qual valem; **desconto** exige valor e data limite; **abatimento** só valor. Se o campo condicional vier **sem** o complemento (ex.: multa sem valor), é erro de validação.

**Daniel:** A combinação convênio + número de controle do cliente será idempotente. Reenvio igual devolve o mesmo resultado; mesma chave com conteúdo diferente retorna conflito.

➕ **Sandra:** "Reenvio igual" é o quê? O cliente reenviou o mesmo JSON?

➕ **Daniel:** Sim: mesmo convênio + número de controle **e** mesmo conteúdo. Devolvemos o **mesmo identificador e a resposta original** (202), sem criar outro boleto. Se o conteúdo diferir, **409 Conflict**, indicando que a chave já existe com outros dados.

**Caio:** Código de barras, linha digitável e identificadores definitivos só aparecem após o registro.

**Roberto:** O que volta imediatamente?

**Daniel:** 202 Accepted, identificador interno, status AGUARDANDO_REGISTRO e data/hora do recebimento. Depois a mensagem segue à fila.

**Sandra:** A resposta deve dizer "aceita para processamento", nunca "boleto registrado".

**Caio:** Erros de campos e regras conhecidas são síncronos. Rejeições da registradora são assíncronas e aparecem no status.

➕ **Marina:** Como o erro síncrono aparece?

➕ **Daniel:** **400 Bad Request** com a lista de **todos** os campos inválidos (não só o primeiro), cada um com campo e motivo. Sem dados sensíveis no corpo do erro. **409** para conflito de idempotência. Se o broker estiver fora e não conseguirmos aceitar com segurança, **503** — não respondemos 202 sem ter garantia de que a mensagem não será perdida.

**Marina:** Vou preservar essa diferença nas histórias.

➕ **Daniel:** E o identificador interno é um **UUID v4**, gerado na Central no momento do aceite.

## Decisões da sessão
1. POST sistema a sistema.
2. Campos essenciais e regras condicionais validados na entrada (formatos acima).
3. Idempotência por convênio + número de controle (igual → mesmo resultado; diferente → 409).
4. Resposta 202 com identificador interno (UUID v4), `AGUARDANDO_REGISTRO` e data/hora do recebimento.
5. Linha digitável e código de barras somente após registro.
6. ➕ Erros: 400 (lista de campos), 409 (conflito), 503 (aceite impossível com segurança).
7. ➕ Rejeição da registradora é **assíncrona**: aparece no status, nunca no POST.

## Action items
- **Daniel:** especificar idempotência e fila.
- **Caio:** classificar validações e rejeições.
- **Marina:** separar "aceito" de "registrado".

## Glossário acrescentado
- **Aceite assíncrono:** solicitação aceita para processamento posterior.
- **Chave de idempotência:** protege contra duplicidade em reenvios.
- **Aguardando registro:** estado anterior à confirmação externa.
- **Conflito (409):** mesma chave de idempotência com conteúdo diferente.
