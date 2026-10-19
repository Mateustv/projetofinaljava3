package br.com.caixa.cobranca.emissor;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Teste da API: POST /boletos devolve 202 (valido) ou 400 com a lista de erros. */
@SpringBootTest
@AutoConfigureMockMvc
class BoletoControllerTest {

    @Autowired
    private MockMvc mvc;

    private static final String ENDERECO = """
            {"logradouro":"Rua A, 1","cidade":"Brasilia","uf":"DF","cep":"70000000"}""";

    private static String corpo(String convenio, String valor) {
        return """
                {
                  "convenio": "%s",
                  "numero_controle_cliente": "NF-0001",
                  "beneficiario": {"nome":"Empresa X","tipo_documento":"CNPJ","numero_documento":"12345678000195","endereco":%s},
                  "pagador": {"nome":"Maria","tipo_documento":"CPF","numero_documento":"12345678909","endereco":%s},
                  "valor_nominal": %s,
                  "data_emissao": "2026-10-19",
                  "data_vencimento": "2026-11-19",
                  "especie": "DM"
                }""".formatted(convenio, ENDERECO, ENDERECO, valor);
    }

    @Test
    void emissaoValidaRetorna202ComStatusAguardandoRegistro() throws Exception {
        mvc.perform(post("/boletos").contentType(MediaType.APPLICATION_JSON).content(corpo("123456", "150.00")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.identificador").exists())
                .andExpect(jsonPath("$.status").value("AGUARDANDO_REGISTRO"))
                .andExpect(jsonPath("$.data_hora_recebimento").exists());
    }

    @Test
    void emissaoInvalidaRetorna400ComTodosOsErros() throws Exception {
        mvc.perform(post("/boletos").contentType(MediaType.APPLICATION_JSON).content(corpo("1", "0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.length()").value(2));
    }

    @Test
    void jsonMalformadoRetorna400() throws Exception {
        mvc.perform(post("/boletos").contentType(MediaType.APPLICATION_JSON).content("{nao e json"))
                .andExpect(status().isBadRequest());
    }
}
