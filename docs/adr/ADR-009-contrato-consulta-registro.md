# ADR-009 — Prevenção de Quebras em Produção: Contrato Executável Consumer-Driven (Pact) entre Consulta e Registro

## Contexto

Em arquiteturas orientadas a microsserviços, os serviços `boleto-consulta` (consumidor) e `boleto-registro` (provedor) possuem ciclos de vida, esteiras de entrega contínua (CI/CD) e equipes de desenvolvimento independentes. 

Nesse cenário distribuído, o risco mais crítico de integração é a **quebra silenciosa de contratos em produção**:
- Uma alteração não comunicada no serviço de registro (ex.: renomear um campo JSON, alterar o formato de serialização de datas ISO-8601, modificar o mapeamento de campos snake_case como `linha_digitavel` ou alterar o código de status HTTP retornado) pode fazer com que o serviço de consulta falhe catastrophicamente ao atender clientes em produção.
- **A Ilusão dos DTOs Compartilhados:** Apenas compartilhar classes Java (records/DTOs) no repositório não garante compatibilidade em tempo de execução. O compartilhamento de código apenas assegura que os tipos compilam, mas não valida se os mapeamentos de rotas do Spring MVC, os filtros de serialização do Jackson, os cabeçalhos HTTP e os códigos de resposta estão realmente alinhados.
- **A Fragilidade dos Ambientes de Teste Integrados:** Testes ponta a ponta em ambientes de homologação compartilhados são reconhecidamente caros, lentos, instáveis e propensos a falsos negativos causados por quedas de servidores de terceiros ou dados corrompidos.

## Decisão

Adotar o padrão **Consumer-Driven Contract Testing (Testes de Contrato Dirigidos pelo Consumidor)** utilizando o framework **Pact (Pact JVM 4.6.x)** com especificação V3:

1. **Definição pelo Consumidor (`boleto-consulta`):** O cliente REST real da consulta (`RestBoletoFonteClient`) é exercitado contra um servidor *mock* do Pact em porta aleatória, registrando formalmente o contrato exigido:
   - Requisição: `GET /interno/boletos/{id}`;
   - Respostas esperadas com matchers de tipo e formato:
     - `HTTP 200 OK` para `REGISTRADO` (contendo dados pagáveis simulados e metadados);
     - `HTTP 200 OK` para `REJEITADO` (contendo código e motivo de recusa, com dados de pagamento estritamente nulos);
     - `HTTP 404 Not Found` para identificadores inexistentes.
2. **Verificação Real no Provedor (`boleto-registro`):** O teste de contrato do provedor sobe a aplicação Spring Boot real com base H2 exclusiva em memória e valida se o backend real do registro cumpre integralmente cada cláusula e formato acordado no Pact.
3. **Contrato Canônico Versionado:** Um único arquivo de contrato canônico é mantido em `shared-contracts/pacts/boleto-consulta-boleto-registro.json`.
4. **Validação de Integridade no Build (`PactArtifactIT`):** Uma etapa no plugin Maven Failsafe compara o arquivo gerado pelo consumidor com o contrato canônico consumido pelo provedor. Qualquer desvio de campo, tipo ou status quebra imediatamente o build Maven (`mvn clean verify`).
5. **Autonomia Local Total:** Todo o fluxo executa em processos JVM locais e isolados, sem demandar instâncias externas de Pact Broker ou infraestrutura de contêineres.

## Alternativas consideradas

- **Apenas Compartilhar Records/DTOs Java:** Rejeitada. Não valida endpoints HTTP reais, cabeçalhos, códigos de status nem regras de serialização do Jackson (`PropertyNamingStrategies.SNAKE_CASE`).
- **Testes Ponta a Ponta em Ambiente de Homologação Integrado:** Rejeitada. Apresenta feedback tardio, dependência de servidores compartilhados externos e fragilidade operacional contínua.
- **Mocks Tradicionais (Mockito) nos Testes Unitários:** Rejeitada. Mocks manuais desacoplam-se rapidamente da realidade: o teste do consumidor continuaria verde mesmo se o provedor em produção alterasse completamente a estrutura de sua resposta.

## Consequências

- **Positivas:**
  - **Imunidade a Quebras de API em Produção:** Qualquer alteração incompatível na API do registro ou na deserialização da consulta quebra imediatamente o build antes de atingir o ambiente produtivo.
  - **Deploys Totalmente Independentes:** O `boleto-registro` pode ser implantado a qualquer momento em produção com a certeza matemática de que não interromperá a operação do `boleto-consulta`.
  - **Documentação Executável:** O arquivo de contrato Pact serve como a documentação técnica definitiva e viva do acordo entre as equipes.
- **Custos / Mitigações:**
  - Exige que qualquer evolução estrutural de campos ou novos status seja precedida de uma atualização intencional e coordenada do contrato canônico e dos testes em ambos os módulos.

## Status

Aceita e validada nos testes automatizados do build institucional.
