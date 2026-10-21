package br.com.caixa.cobranca.registro.listeners;

import br.com.caixa.cobranca.registro.exceptions.ComandoRegistroConflitanteException;
import br.com.caixa.cobranca.registro.exceptions.ComandoRegistroInvalidoException;
import br.com.caixa.cobranca.registro.services.RegistroBoletoService;
import br.com.caixa.cobranca.contracts.RegistrarBoletoCommand;
import br.com.caixa.cobranca.http.Correlacao;
import br.com.caixa.cobranca.http.demo.Rastros;
import br.com.caixa.cobranca.registro.exceptions.FalhaAoRegistrarBoletoException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.jms.DeliveryMode;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "registro.mensageria.enabled", havingValue = "true", matchIfMissing = true)
public class RegistroCommandListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegistroCommandListener.class);
    private final ObjectMapper mapper;
    private final RegistroBoletoService service;
    private final String quarentena;

    public RegistroCommandListener(ObjectMapper mapper, RegistroBoletoService service,
                                   @Value("${mensageria.registro.quarentena:registro.quarentena}") String quarentena) {
        this.mapper = mapper;
        this.service = service;
        this.quarentena = quarentena;
    }

    @JmsListener(destination = "${mensageria.registro.fila:registro.comandos}",
            containerFactory = "registroJmsFactory")
    public void receber(Message message, Session session) throws JMSException {
        try {
            if (!(message instanceof TextMessage texto)) {
                isolar(message, session, "TIPO_MENSAGEM_INVALIDO");
                return;
            }
            var comando = mapper.readValue(texto.getText(), RegistrarBoletoCommand.class);
            if (comando == null || comando.correlationId() == null
                    || comando.correlationId().version() != 4 || comando.correlationId().variant() != 2
                    || (message.getJMSCorrelationID() != null
                    && !comando.correlationId().toString().equals(message.getJMSCorrelationID()))) {
                throw new ComandoRegistroInvalidoException();
            }
            try (var escopo = Correlacao.abrir(comando.correlationId())) {
                int entrega = message.getIntProperty("JMSXDeliveryCount");
                Rastros.registrar(comando.identificador(), "COMANDO_RECEBIDO",
                        "Entrega real do Artemis: " + entrega);
                LOGGER.info("Comando recebido. identificador={}, correlation_id={}",
                        comando.identificador(), comando.correlationId());
                RegistroBoletoService.ResultadoRegistro resultado;
                try {
                    resultado = service.registrar(comando);
                } catch (FalhaAoRegistrarBoletoException ex) {
                    Rastros.registrar(comando.identificador(), "FALHA_PROCESSAMENTO",
                            "Falha na entrega " + entrega + "; " + ex.getClass().getSimpleName()
                                    + " relancada para rollback JMS. Broker decide redelivery/quarentena.");
                    throw ex;
                }
                Rastros.registrar(comando.identificador(), "PROCESSAMENTO_CONCLUIDO",
                        "Dominio retornou; duplicado=" + resultado.duplicado()
                                + ". Ack JMS sera tentado pelo container apos retorno do listener.");
                LOGGER.info("Comando processado. identificador={}, status={}, duplicado={}, entrega={}, correlation_id={}",
                        resultado.boleto().identificador(), resultado.boleto().status(),
                        resultado.duplicado(), message.getIntProperty("JMSXDeliveryCount"), comando.correlationId());
            }
        } catch (JsonProcessingException | ComandoRegistroInvalidoException | ComandoRegistroConflitanteException ex) {
            isolar(message, session, ex.getClass().getSimpleName());
        }
    }

    private void isolar(Message original, Session session, String tipo) throws JMSException {
        String mensagemOriginal = original.getJMSMessageID();
        int entregas = original.getIntProperty("JMSXDeliveryCount");
        Message copia;
        if (original instanceof TextMessage texto) {
            copia = session.createTextMessage(texto.getText());
        } else {
            // Preserva tambem o corpo nao textual; nenhuma desserializacao de ObjectMessage e necessaria.
            original.clearProperties();
            copia = original;
        }
        copia.setJMSCorrelationID(original.getJMSCorrelationID());
        copia.setStringProperty("tipo_erro", tipo);
        copia.setStringProperty("fila_origem", original.getJMSDestination().toString());
        copia.setStringProperty("mensagem_original", mensagemOriginal);
        copia.setIntProperty("entregas", entregas);
        copia.setLongProperty("falha_em", System.currentTimeMillis());
        var fila = session.createQueue(quarentena);
        try (var browser = session.createBrowser(fila)) {
            browser.getEnumeration();
        }
        try (var producer = session.createProducer(fila)) {
            producer.send(copia, DeliveryMode.PERSISTENT, 4, 0);
        }
        LOGGER.warn("Mensagem encaminhada para quarentena. mensagem={}, tipo={}", mensagemOriginal, tipo);
    }
}
