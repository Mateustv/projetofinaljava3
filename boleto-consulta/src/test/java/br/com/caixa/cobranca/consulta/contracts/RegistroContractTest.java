package br.com.caixa.cobranca.consulta.contracts;

import br.com.caixa.cobranca.consulta.clients.RestBoletoFonteClient;
import br.com.caixa.cobranca.consulta.exceptions.BoletoNaoEncontradoException;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;
import br.com.caixa.cobranca.contracts.StatusBoleto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@JsonTest
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "boleto-registro", pactVersion = PactSpecVersion.V3)
class RegistroContractTest {

    private static final String REGISTRADO = "11111111-1111-4111-8111-111111111111";
    private static final String REJEITADO = "22222222-2222-4222-8222-222222222222";
    private static final String AUSENTE = "33333333-3333-4333-8333-333333333333";
    private static final String RECEBIMENTO = "2026-10-20T10:00:00";
    private static final String REGISTRO = "2026-10-20T10:00:05";

    @Autowired
    private ObjectMapper objectMapper;

    @Pact(consumer = "boleto-consulta")
    public RequestResponsePact registrado(PactDslWithProvider builder) {
        return builder.given("boleto registrado")
                .uponReceiving("consultar boleto registrado")
                .method("GET").path("/interno/boletos/" + REGISTRADO)
                .willRespondWith().status(200)
                .headers(Map.of("Content-Type", "application/json"))
                .body(new PactDslJsonBody()
                        .stringValue("identificador", REGISTRADO)
                        .stringValue("status", "REGISTRADO")
                        .stringValue("data_hora_recebimento", RECEBIMENTO)
                        .stringType("identificador_registradora", "SIM-" + REGISTRADO)
                        .stringValue("data_hora_registro", REGISTRO)
                        .stringValue("linha_digitavel", "NAO-PAGAVEL-SIM-LINHA-" + REGISTRADO)
                        .stringValue("codigo_barras", "NAO-PAGAVEL-SIM-BARRAS-" + REGISTRADO)
                        .nullValue("codigo_rejeicao")
                        .nullValue("motivo_rejeicao"))
                .toPact();
    }

    @Pact(consumer = "boleto-consulta")
    public RequestResponsePact rejeitado(PactDslWithProvider builder) {
        return builder.given("boleto rejeitado")
                .uponReceiving("consultar boleto rejeitado")
                .method("GET").path("/interno/boletos/" + REJEITADO)
                .willRespondWith().status(200)
                .headers(Map.of("Content-Type", "application/json"))
                .body(new PactDslJsonBody()
                        .stringValue("identificador", REJEITADO)
                        .stringValue("status", "REJEITADO")
                        .stringValue("data_hora_recebimento", RECEBIMENTO)
                        .stringValue("codigo_rejeicao", "SIM-REJ-001")
                        .stringType("motivo_rejeicao", "Rejeicao simulada.")
                        .nullValue("linha_digitavel")
                        .nullValue("codigo_barras")
                        .nullValue("identificador_registradora")
                        .nullValue("data_hora_registro"))
                .toPact();
    }

    @Pact(consumer = "boleto-consulta")
    public RequestResponsePact ausente(PactDslWithProvider builder) {
        return builder.given("boleto inexistente")
                .uponReceiving("consultar boleto inexistente")
                .method("GET").path("/interno/boletos/" + AUSENTE)
                .willRespondWith().status(404)
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "registrado")
    void desserializaRegistradoComContratoAtual(MockServer server) {
        var boleto = cliente(server).buscar(UUID.fromString(REGISTRADO));
        assertEquals(UUID.fromString(REGISTRADO), boleto.identificador());
        assertEquals(StatusBoleto.REGISTRADO, boleto.status());
        assertEquals(LocalDateTime.parse(RECEBIMENTO), boleto.dataHoraRecebimento());
        assertEquals("SIM-" + REGISTRADO, boleto.identificadorRegistradora());
        assertEquals(LocalDateTime.parse(REGISTRO), boleto.dataHoraRegistro());
        assertEquals("NAO-PAGAVEL-SIM-LINHA-" + REGISTRADO, boleto.linhaDigitavel());
        assertEquals("NAO-PAGAVEL-SIM-BARRAS-" + REGISTRADO, boleto.codigoBarras());
        assertNull(boleto.codigoRejeicao());
        assertNull(boleto.motivoRejeicao());
    }

    @Test
    @PactTestFor(pactMethod = "rejeitado")
    void desserializaRejeitadoSemDadosDePagamento(MockServer server) {
        var boleto = cliente(server).buscar(UUID.fromString(REJEITADO));
        assertEquals(UUID.fromString(REJEITADO), boleto.identificador());
        assertEquals(StatusBoleto.REJEITADO, boleto.status());
        assertEquals(LocalDateTime.parse(RECEBIMENTO), boleto.dataHoraRecebimento());
        assertEquals("SIM-REJ-001", boleto.codigoRejeicao());
        assertEquals("Rejeicao simulada.", boleto.motivoRejeicao());
        assertNull(boleto.linhaDigitavel());
        assertNull(boleto.codigoBarras());
        assertNull(boleto.identificadorRegistradora());
        assertNull(boleto.dataHoraRegistro());
    }

    @Test
    @PactTestFor(pactMethod = "ausente")
    void ausenciaContinuaDistintaDeFalhaTecnica(MockServer server) {
        assertThrows(BoletoNaoEncontradoException.class,
                () -> cliente(server).buscar(UUID.fromString(AUSENTE)));
    }

    private RestBoletoFonteClient cliente(MockServer server) {
        var builder = RestClient.builder().messageConverters(converters -> {
            converters.removeIf(MappingJackson2HttpMessageConverter.class::isInstance);
            converters.add(new MappingJackson2HttpMessageConverter(objectMapper));
        });
        return new RestBoletoFonteClient("registro", server.getUrl(), builder);
    }
}
