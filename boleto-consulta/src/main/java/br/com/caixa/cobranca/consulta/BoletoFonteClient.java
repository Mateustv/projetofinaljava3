package br.com.caixa.cobranca.consulta;

import br.com.caixa.cobranca.contracts.BoletoConsulta;
import br.com.caixa.cobranca.contracts.StatusBoleto;

import java.util.UUID;

public interface BoletoFonteClient {

    BoletoConsulta buscar(UUID identificador);

    static void validarResposta(BoletoConsulta boleto, UUID identificador, String fonte) {
        if (boleto == null || boleto.identificador() == null || boleto.status() == null
                || !identificador.equals(boleto.identificador())) {
            throw new FalhaFonteBoletoException(fonte, new IllegalStateException("Resposta inconsistente."));
        }
        if (fonte.equals("emissor") && boleto.status() != StatusBoleto.AGUARDANDO_REGISTRO) {
            throw new FalhaFonteBoletoException(fonte, new IllegalStateException("Estado inesperado no aceite."));
        }
    }
}
