package br.com.caixa.cobranca.emissor;

import br.com.caixa.cobranca.contracts.BoletoAceito;
import br.com.caixa.cobranca.contracts.EmitirBoletoRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Testes de emissao, idempotencia e leitura interna do aceite. */
@SpringBootTest(properties = "emissao.outbox.dispatch.enabled=false")
@AutoConfigureMockMvc
class BoletoControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private EmissaoAceitaRepository repository;

    @Autowired
    private ComandoRegistroOutboxRepository outboxRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EmissaoService emissaoService;

    private static final String ENDERECO = """
            {"logradouro":"Rua A, 1","cidade":"Brasilia","uf":"DF","cep":"70000000"}""";

    private static String corpo(String convenio, String numeroControle, String valor) {
        return """
                {
                  "convenio": "%s",
                  "numero_controle_cliente": "%s",
                  "beneficiario": {"nome":"Empresa X","tipo_documento":"CNPJ","numero_documento":"12345678000195","endereco":%s},
                  "pagador": {"nome":"Maria","tipo_documento":"CPF","numero_documento":"12345678909","endereco":%s},
                  "valor_nominal": %s,
                  "data_emissao": "2026-10-19",
                  "data_vencimento": "2026-11-19",
                  "especie": "DM"
                }""".formatted(convenio, numeroControle, ENDERECO, ENDERECO, valor);
    }

    @Test
    void emissaoValidaRetorna202ComStatusAguardandoRegistro() throws Exception {
        mvc.perform(post("/boletos").contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("123456", "NF-VALID-001", "150.00")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.identificador").exists())
                .andExpect(jsonPath("$.status").value("AGUARDANDO_REGISTRO"))
                .andExpect(jsonPath("$.data_hora_recebimento").exists())
                .andDo(result -> {
                    UUID id = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
                            .get("identificador").asText());
                    mvc.perform(get("/interno/boletos/{identificador}", id))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.identificador").value(id.toString()))
                            .andExpect(jsonPath("$.status").value("AGUARDANDO_REGISTRO"));
                });
    }

    @Test
    void emissaoInvalidaRetorna400ComTodosOsErros() throws Exception {
        mvc.perform(post("/boletos").contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("1", "NF-INVALID-001", "0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.length()").value(2));
    }

    @Test
    void jsonMalformadoRetorna400() throws Exception {
        mvc.perform(post("/boletos").contentType(MediaType.APPLICATION_JSON).content("{nao e json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reenvioComMesmoConteudoRetornaMesmoAceiteSemDuplicar() throws Exception {
        String body = corpo("123456", "NF-IDEMP-001", "150.00");

        String primeiro = mvc.perform(post("/boletos").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String segundo = mvc.perform(post("/boletos").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        assertEquals(objectMapper.readTree(primeiro), objectMapper.readTree(segundo));
        assertEquals(1L,
                repository.countByConvenioAndNumeroControleCliente("123456", "NF-IDEMP-001"));
        assertEquals(1L, outboxRepository.countByIdentificador(
                UUID.fromString(objectMapper.readTree(primeiro).get("identificador").asText())));
    }

    @Test
    void mesmaChaveComConteudoDiferenteRetorna409SemDuplicar() throws Exception {
        mvc.perform(post("/boletos").contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("123456", "NF-CONFLICT-001", "150.00")))
                .andExpect(status().isAccepted());

        mvc.perform(post("/boletos").contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("123456", "NF-CONFLICT-001", "151.00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CHAVE_IDEMPOTENCIA_CONFLITANTE"));

        assertEquals(1L,
                repository.countByConvenioAndNumeroControleCliente("123456", "NF-CONFLICT-001"));
    }

    @Test
    void mesmaChaveComEscalaNumericaDiferenteEConteudoEquivalenteRetornaMesmoAceite() throws Exception {
        String primeiro = mvc.perform(post("/boletos").contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("123456", "NF-SCALE-001", "150.00")))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String reenvio = mvc.perform(post("/boletos").contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("123456", "NF-SCALE-001", "150.0")))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        assertEquals(objectMapper.readTree(primeiro), objectMapper.readTree(reenvio));
    }

    @Test
    void consultaInternaRetorna404QuandoAceiteNaoExiste() throws Exception {
        mvc.perform(get("/interno/boletos/{identificador}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void emissoesConcorrentesComMesmoConteudoCriamUmAceite() throws Exception {
        EmitirBoletoRequest request = objectMapper.readValue(
                corpo("123456", "NF-CONCURRENT-001", "150.00"),
                EmitirBoletoRequest.class
        );
        CountDownLatch inicio = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var primeira = executor.submit(() -> {
                inicio.await();
                return emissaoService.aceitar(request);
            });
            var segunda = executor.submit(() -> {
                inicio.await();
                return emissaoService.aceitar(request);
            });
            inicio.countDown();

            BoletoAceito aceite1 = primeira.get(10, TimeUnit.SECONDS);
            BoletoAceito aceite2 = segunda.get(10, TimeUnit.SECONDS);

            assertEquals(aceite1.identificador(), aceite2.identificador());
            assertEquals(1L,
                    repository.countByConvenioAndNumeroControleCliente("123456", "NF-CONCURRENT-001"));
            assertEquals(1L, outboxRepository.countByIdentificador(aceite1.identificador()));
        } finally {
            executor.shutdownNow();
        }
    }
}
