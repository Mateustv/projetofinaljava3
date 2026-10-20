package br.com.caixa.cobranca.registro.services;

import br.com.caixa.cobranca.registro.exceptions.FalhaAoRegistrarBoletoException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

final class ConteudoComando {

    private ConteudoComando() {
    }

    static boolean igual(ObjectMapper mapper, String primeiro, String segundo) {
        try {
            return semCorrelacao(mapper, primeiro).equals(semCorrelacao(mapper, segundo));
        } catch (JsonProcessingException ex) {
            throw new FalhaAoRegistrarBoletoException(ex);
        }
    }

    private static ObjectNode semCorrelacao(ObjectMapper mapper, String json) throws JsonProcessingException {
        var node = mapper.readTree(json);
        if (!(node instanceof ObjectNode objeto)) {
            throw new FalhaAoRegistrarBoletoException(new IllegalStateException("Comando persistido invalido."));
        }
        objeto.remove("correlation_id");
        return objeto;
    }
}
