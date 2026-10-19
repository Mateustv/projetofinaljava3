package br.com.caixa.cobranca.consulta;

import br.com.caixa.cobranca.contracts.BoletoConsulta;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ConsultaBoletoService {

    private final BoletoFonteClient registro;
    private final BoletoFonteClient emissor;

    public ConsultaBoletoService(@Qualifier("registroBoletoClient") BoletoFonteClient registro,
                                 @Qualifier("emissorBoletoClient") BoletoFonteClient emissor) {
        this.registro = registro;
        this.emissor = emissor;
    }

    @Cacheable(cacheNames = ConsultaCacheConfiguration.CACHE_BOLETOS, key = "#identificador")
    public BoletoConsulta consultar(UUID identificador) {
        try {
            return normalizar(registro.buscar(identificador), identificador, "registro");
        } catch (BoletoNaoEncontradoException registroAindaNaoConhece) {
            return normalizar(emissor.buscar(identificador), identificador, "emissor");
        }
    }

    private BoletoConsulta normalizar(BoletoConsulta boleto, UUID identificador, String fonte) {
        if (boleto == null || boleto.identificador() == null || boleto.status() == null
                || !identificador.equals(boleto.identificador())) {
            throw new FalhaFonteBoletoException(fonte, new IllegalStateException("Resposta inconsistente."));
        }
        if (fonte.equals("emissor") && boleto.status() != br.com.caixa.cobranca.contracts.StatusBoleto.AGUARDANDO_REGISTRO) {
            throw new FalhaFonteBoletoException(fonte, new IllegalStateException("Estado inesperado no aceite."));
        }
        return fonte.equals("emissor") ? BoletoConsulta.aguardando(boleto) : BoletoConsulta.somenteEstado(boleto);
    }
}
