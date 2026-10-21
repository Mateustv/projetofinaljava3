package br.com.caixa.cobranca.consulta.listeners;

import br.com.caixa.cobranca.consulta.services.ConsultaBoletoService;
import br.com.caixa.cobranca.contracts.EventoBoleto;
import br.com.caixa.cobranca.contracts.TipoEventoBoleto;
import br.com.caixa.cobranca.http.Correlacao;
import br.com.caixa.cobranca.http.demo.Rastros;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.jms.DeliveryMode;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "consulta.eventos.enabled", havingValue = "true", matchIfMissing = true)
public class ConsultaEventoListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConsultaEventoListener.class);
    private final ObjectMapper mapper;
    private final ConsultaBoletoService consulta;

    public ConsultaEventoListener(ObjectMapper mapper, ConsultaBoletoService consulta) {
        this.mapper = mapper;
        this.consulta = consulta;
    }

    @JmsListener(destination = "boletos.eventos::boletos.eventos.consulta",
            containerFactory = "consultaEventosFactory")
    public void receber(Message message, Session session) throws JMSException {
        if (!(message instanceof TextMessage texto)) {
            isolar(message, session, "TIPO_MENSAGEM_INVALIDO");
            return;
        }
        final EventoBoleto evento;
        try {
            evento = mapper.readValue(texto.getText(), EventoBoleto.class);
            if (evento == null || (message.getJMSCorrelationID() != null
                    && !evento.correlationId().toString().equals(message.getJMSCorrelationID()))) {
                throw new IllegalArgumentException("Evento nulo.");
            }
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            isolar(message, session, "EVENTO_INVALIDO");
            return;
        }
        try (var escopo = Correlacao.abrir(evento.correlationId())) {
            Rastros.registrar(evento.identificadorBoleto(), "EVENTO_RECEBIDO",
                    evento.tipo() + "; id_evento=" + evento.idEvento());
            LOGGER.info("Evento recebido. idEvento={}, correlation_id={}", evento.idEvento(), evento.correlationId());
            if (evento.tipo() != TipoEventoBoleto.EmissaoAceita) {
                consulta.invalidar(evento.identificadorBoleto());
            }
            LOGGER.info("Evento consumido. idEvento={}, identificador={}, tipo={}, correlation_id={}",
                    evento.idEvento(), evento.identificadorBoleto(), evento.tipo(), evento.correlationId());
        }
    }

    private void isolar(Message original, Session session, String tipo) throws JMSException {
        String id = original.getJMSMessageID();
        int entregas = original.getIntProperty("JMSXDeliveryCount");
        Message copia;
        if (original instanceof TextMessage texto) {
            copia = session.createTextMessage(texto.getText());
        } else {
            original.clearProperties();
            copia = original;
        }
        copia.setJMSCorrelationID(original.getJMSCorrelationID());
        copia.setStringProperty("tipo_erro", tipo);
        copia.setStringProperty("mensagem_original", id);
        copia.setStringProperty("fila_origem", "boletos.eventos.consulta");
        copia.setIntProperty("entregas", entregas);
        copia.setLongProperty("falha_em", System.currentTimeMillis());
        var fila = session.createQueue("boletos.eventos.quarentena");
        try (var browser = session.createBrowser(fila)) {
            browser.getEnumeration();
        }
        try (var producer = session.createProducer(fila)) {
            producer.send(copia, DeliveryMode.PERSISTENT, 4, 0);
        }
        LOGGER.warn("Evento invalido encaminhado para quarentena. mensagem={}, tipo={}", id, tipo);
    }
}
