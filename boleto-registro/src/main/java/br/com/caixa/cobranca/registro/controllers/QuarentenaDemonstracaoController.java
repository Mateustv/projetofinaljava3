package br.com.caixa.cobranca.registro.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.jms.TextMessage;
import org.springframework.context.annotation.Profile;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Profile("demonstracao")
public class QuarentenaDemonstracaoController {
    private final JmsTemplate jms;
    private final ObjectMapper mapper;

    public QuarentenaDemonstracaoController(JmsTemplate jms, ObjectMapper mapper) {
        this.jms = jms;
        this.mapper = mapper;
    }

    @GetMapping("/demonstracao/quarentena/{boleto}")
    public Observacao observar(@PathVariable UUID boleto) {
        return jms.browse("registro.quarentena", (session, browser) -> {
            var mensagens = browser.getEnumeration();
            int examinadas = 0;
            boolean encontrada = false;
            while (examinadas < 200 && mensagens.hasMoreElements()) {
                var mensagem = mensagens.nextElement();
                examinadas++;
                if (mensagem instanceof TextMessage texto) {
                    try {
                        var json = mapper.readTree(texto.getText());
                        if (json != null && boleto.toString().equals(json.path("identificador").asText())) {
                            encontrada = true;
                        }
                    } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
                        // Quarentena pode conter JSON invalido; nao expor nem descartar o corpo.
                        org.slf4j.LoggerFactory.getLogger(getClass())
                                .warn("JSON invalido observado na quarentena da demonstracao.");
                    }
                }
            }
            return new Observacao(encontrada, examinadas, mensagens.hasMoreElements());
        });
    }

    public record Observacao(boolean encontrada, int examinadas, boolean limiteAtingido) {
    }
}
