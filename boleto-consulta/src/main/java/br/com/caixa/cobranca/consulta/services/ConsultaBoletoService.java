package br.com.caixa.cobranca.consulta.services;

import br.com.caixa.cobranca.consulta.clients.BoletoFonteClient;
import br.com.caixa.cobranca.consulta.configs.ConsultaCacheConfiguration;
import br.com.caixa.cobranca.consulta.exceptions.BoletoNaoEncontradoException;
import br.com.caixa.cobranca.contracts.BoletoConsulta;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.Objects;

@Service
public class ConsultaBoletoService {

    private final BoletoFonteClient registro;
    private final BoletoFonteClient emissor;
    private final Cache cache;
    private final Object[] travas = new Object[64];

    public ConsultaBoletoService(@Qualifier("registroBoletoClient") BoletoFonteClient registro,
                                 @Qualifier("emissorBoletoClient") BoletoFonteClient emissor,
                                 CacheManager cacheManager) {
        this.registro = registro;
        this.emissor = emissor;
        this.cache = Objects.requireNonNull(cacheManager.getCache(ConsultaCacheConfiguration.CACHE_BOLETOS),
                "Cache de boletos nao configurado.");
        for (int i = 0; i < travas.length; i++) {
            travas[i] = new Object();
        }
    }

    public BoletoConsulta consultar(UUID identificador) {
        // A invalidacao aguarda o preenchimento em voo antes de confirmar o evento.
        synchronized (trava(identificador)) {
            var existente = cache.get(identificador, BoletoConsulta.class);
            if (existente != null) {
                return existente;
            }
            var boleto = buscar(identificador);
            cache.put(identificador, boleto);
            return boleto;
        }
    }

    public void invalidar(UUID identificador) {
        synchronized (trava(identificador)) {
            cache.evict(identificador);
        }
    }

    private Object trava(UUID identificador) {
        return travas[Math.floorMod(identificador.hashCode(), travas.length)];
    }

    private BoletoConsulta buscar(UUID identificador) {
        try {
            return normalizar(registro.buscar(identificador), identificador, "registro");
        } catch (BoletoNaoEncontradoException registroAindaNaoConhece) {
            return normalizar(emissor.buscar(identificador), identificador, "emissor");
        }
    }

    private BoletoConsulta normalizar(BoletoConsulta boleto, UUID identificador, String fonte) {
        BoletoFonteClient.validarResposta(boleto, identificador, fonte);
        return fonte.equals("emissor") ? BoletoConsulta.aguardando(boleto) : BoletoConsulta.somenteEstado(boleto);
    }
}
