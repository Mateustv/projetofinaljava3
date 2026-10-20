package br.com.caixa.cobranca.emissor.integration;

import br.com.caixa.cobranca.emissor.validators.EmissaoValidator;
import br.com.caixa.cobranca.contracts.EmitirBoletoRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@JsonTest
class EmissaoRegrasTest {

    @Autowired
    private ObjectMapper mapper;
    private final EmissaoValidator validator = new EmissaoValidator();

    @ParameterizedTest
    @ValueSource(strings = {
            "convenio", "numero_controle_cliente", "beneficiario", "pagador", "valor_nominal",
            "data_emissao", "data_vencimento", "especie",
            "beneficiario.nome", "beneficiario.tipo_documento", "beneficiario.numero_documento",
            "beneficiario.endereco", "beneficiario.endereco.logradouro", "beneficiario.endereco.cidade",
            "beneficiario.endereco.uf", "beneficiario.endereco.cep",
            "pagador.nome", "pagador.tipo_documento", "pagador.numero_documento", "pagador.endereco",
            "pagador.endereco.logradouro", "pagador.endereco.cidade", "pagador.endereco.uf", "pagador.endereco.cep"
    })
    void obrigatorioAusenteRetornaCampoSemSeuValor(String campo) throws Exception {
        ObjectNode json = valida();
        pai(json, campo).putNull(ultimo(campo));
        var erros = validator.validar(mapper.treeToValue(json, EmitirBoletoRequest.class));
        assertEquals(1, erros.size());
        assertEquals(campo, erros.get(0).campo());
    }

    @ParameterizedTest
    @CsvSource({
            "convenio,12345", "convenio,1234567", "convenio,ABCDEF",
            "numero_controle_cliente,12345678901234567890123456",
            "pagador.numero_documento,123", "pagador.numero_documento,ABC123",
            "beneficiario.numero_documento,12345678901",
            "pagador.endereco.uf,df", "pagador.endereco.uf,DFX",
            "pagador.endereco.cep,1234567", "pagador.endereco.cep,123456789",
            "pagador.endereco.cep,ABCDEFGH", "especie,XX", "pagador.nome,' '"
    })
    void formatoOuLimiteInvalidoERejeitado(String campo, String valor) throws Exception {
        ObjectNode json = valida();
        pai(json, campo).put(ultimo(campo), valor);
        var erros = validator.validar(mapper.treeToValue(json, EmitirBoletoRequest.class));
        assertEquals(1, erros.size());
        assertEquals(campo, erros.get(0).campo());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-0.01", "1.001"})
    void valorNominalInvalidoNaoEArredondadoSilenciosamente(String valor) throws Exception {
        ObjectNode json = valida();
        json.put("valor_nominal", new BigDecimal(valor));
        var erros = validator.validar(mapper.treeToValue(json, EmitirBoletoRequest.class));
        assertEquals(1, erros.size());
        assertEquals("valor_nominal", erros.get(0).campo());
    }

    @ParameterizedTest
    @ValueSource(strings = {"juros.tipo", "juros.valor", "juros.a_partir_de",
            "multa.tipo", "multa.valor", "multa.a_partir_de", "desconto.valor", "desconto.ate"})
    void condicionalSemComplementoNaoEIgnorado(String campo) throws Exception {
        ObjectNode json = valida();
        pai(json, campo).putNull(ultimo(campo));
        var erros = validator.validar(mapper.treeToValue(json, EmitirBoletoRequest.class));
        assertEquals(1, erros.size());
        assertEquals(campo, erros.get(0).campo());
    }

    @ParameterizedTest
    @ValueSource(strings = {"juros.valor", "multa.valor", "desconto.valor", "abatimento"})
    void condicionalNaoPositivoERejeitado(String campo) throws Exception {
        ObjectNode json = valida();
        pai(json, campo).put(ultimo(campo), -1);
        var erros = validator.validar(mapper.treeToValue(json, EmitirBoletoRequest.class));
        assertEquals(1, erros.size());
        assertEquals(campo, erros.get(0).campo());
    }

    @Test
    void limitesValidosPreservamZerosECondicionaisCompletos() throws Exception {
        ObjectNode json = valida();
        json.put("convenio", "000001");
        json.put("numero_controle_cliente", "1234567890123456789012345");
        json.put("valor_nominal", new BigDecimal("0.01"));
        json.put("data_vencimento", "2026-10-20");
        var request = mapper.treeToValue(json, EmitirBoletoRequest.class);
        assertTrue(validator.validar(request).isEmpty());
        assertEquals("000001", request.convenio());
    }

    private ObjectNode valida() throws Exception {
        return (ObjectNode) mapper.readTree("""
                {
                  "convenio":"123456", "numero_controle_cliente":"NF-TESTE",
                  "beneficiario":{"nome":"Empresa ficticia","tipo_documento":"CNPJ",
                    "numero_documento":"12345678000195","endereco":{
                      "logradouro":"Rua ficticia","cidade":"Brasilia","uf":"DF","cep":"70000000"}},
                  "pagador":{"nome":"Pessoa ficticia","tipo_documento":"CPF",
                    "numero_documento":"12345678909","endereco":{
                      "logradouro":"Rua ficticia","cidade":"Brasilia","uf":"DF","cep":"70000000"}},
                  "valor_nominal":150.00,"data_emissao":"2026-10-20","data_vencimento":"2026-11-20",
                  "especie":"DM",
                  "juros":{"tipo":"PERCENTUAL","valor":1,"a_partir_de":"2026-11-21"},
                  "multa":{"tipo":"FIXO","valor":2,"a_partir_de":"2026-11-21"},
                  "desconto":{"valor":1,"ate":"2026-11-19"},"abatimento":1
                }
                """);
    }

    private ObjectNode pai(ObjectNode json, String campo) {
        String[] partes = campo.split("\\.");
        ObjectNode alvo = json;
        for (int i = 0; i < partes.length - 1; i++) {
            alvo = (ObjectNode) alvo.get(partes[i]);
        }
        return alvo;
    }

    private String ultimo(String campo) {
        return campo.substring(campo.lastIndexOf('.') + 1);
    }
}
