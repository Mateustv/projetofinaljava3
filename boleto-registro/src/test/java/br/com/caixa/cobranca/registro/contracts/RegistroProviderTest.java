package br.com.caixa.cobranca.registro.contracts;

import br.com.caixa.cobranca.registro.dtos.ResultadoRegistradora;
import br.com.caixa.cobranca.registro.models.RegistroBoletoEntity;
import br.com.caixa.cobranca.registro.repositories.RegistroBoletoRepository;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import br.com.caixa.cobranca.contracts.StatusBoleto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.time.LocalDateTime;
import java.util.UUID;

@Provider("boleto-registro")
@PactFolder("target/test-classes/pacts")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:registro-pact;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class RegistroProviderTest {

    private static final UUID REGISTRADO = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID REJEITADO = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final LocalDateTime RECEBIMENTO = LocalDateTime.parse("2026-10-20T10:00:00");

    @LocalServerPort
    private int port;

    @Autowired
    private RegistroBoletoRepository repository;

    @BeforeEach
    void preparar(PactVerificationContext context) {
        repository.deleteAll();
        context.setTarget(new HttpTestTarget("localhost", port));
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void verificaContrato(PactVerificationContext context) {
        context.verifyInteraction();
    }

    @State("boleto registrado")
    void registrado() {
        repository.saveAndFlush(new RegistroBoletoEntity(REGISTRADO, "{}", RECEBIMENTO,
                new ResultadoRegistradora(StatusBoleto.REGISTRADO, "SIM-" + REGISTRADO,
                        LocalDateTime.parse("2026-10-20T10:00:05"), null, null,
                        "NAO-PAGAVEL-SIM-LINHA-" + REGISTRADO, "NAO-PAGAVEL-SIM-BARRAS-" + REGISTRADO)));
    }

    @State("boleto rejeitado")
    void rejeitado() {
        repository.saveAndFlush(new RegistroBoletoEntity(REJEITADO, "{}", RECEBIMENTO,
                new ResultadoRegistradora(StatusBoleto.REJEITADO, null, null,
                        "SIM-REJ-001", "Rejeicao simulada.", null, null)));
    }

    @State("boleto inexistente")
    void inexistente() {
        repository.deleteAll();
    }
}
