package br.com.caixa.cobranca.consulta.contracts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PactArtifactIT {

    private static final String ARQUIVO = "boleto-consulta-boleto-registro.json";
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void pactGeradoCorrespondeAoMesmoArtefatoVerificadoPeloProvedor() throws IOException {
        JsonNode canonico;
        try (var input = getClass().getResourceAsStream("/pacts/" + ARQUIVO)) {
            assertNotNull(input, "Pact canonico deve estar versionado em shared-contracts/pacts.");
            canonico = mapper.readTree(input);
        }
        var gerado = mapper.readTree(Path.of(System.getProperty("pact.rootDir"), ARQUIVO).toFile());
        assertEquals(canonico.at("/metadata/pactSpecification/version"),
                gerado.at("/metadata/pactSpecification/version"));
        assertEquals(normalizado(canonico), normalizado(gerado),
                "Contrato gerado diverge do canonico verificado pelo registro. Revise ambos antes de atualizar.");
    }

    private JsonNode normalizado(JsonNode pact) {
        ObjectNode resultado = pact.deepCopy();
        // Versoes da ferramenta e ordem de execucao nao alteram o contrato HTTP.
        resultado.remove("metadata");
        var interacoes = new ArrayList<JsonNode>();
        pact.withArray("interactions").forEach(interacoes::add);
        interacoes.sort(Comparator.comparing(interacao -> interacao.path("description").asText()));
        resultado.set("interactions", mapper.valueToTree(interacoes));
        return resultado;
    }
}
