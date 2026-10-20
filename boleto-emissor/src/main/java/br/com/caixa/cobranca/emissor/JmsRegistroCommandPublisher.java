package br.com.caixa.cobranca.emissor;

import br.com.caixa.cobranca.contracts.RegistrarBoletoCommand;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.support.converter.MessageConversionException;
import org.springframework.stereotype.Component;

@Component
public class JmsRegistroCommandPublisher implements RegistroCommandPublisher {

    private final JmsTemplate jms;
    private final ObjectMapper mapper;
    private final String destino;

    public JmsRegistroCommandPublisher(JmsTemplate jms, ObjectMapper mapper,
                                      @Value("${mensageria.registro.fila:registro.comandos}") String destino) {
        this.jms = jms;
        this.mapper = mapper;
        this.destino = destino;
        jms.setSessionTransacted(true);
        jms.setExplicitQosEnabled(true);
        jms.setDeliveryPersistent(true);
    }

    @Override
    public void publicar(RegistrarBoletoCommand comando) {
        if (comando == null || comando.identificador() == null) {
            throw new MessageConversionException("Comando de registro sem identificador.");
        }
        final String json;
        try {
            json = mapper.writeValueAsString(comando);
        } catch (JsonProcessingException ex) {
            throw new MessageConversionException("Falha ao serializar comando de registro.", ex);
        }
        jms.send(destino, session -> {
            // Um endereco existente sem fila nao pode produzir uma confirmacao sem entrega.
            try (var browser = session.createBrowser(session.createQueue(destino))) {
                browser.getEnumeration();
            }
            var message = session.createTextMessage(json);
            message.setJMSCorrelationID(comando.identificador().toString());
            message.setStringProperty("identificador_boleto", comando.identificador().toString());
            return message;
        });
    }
}
