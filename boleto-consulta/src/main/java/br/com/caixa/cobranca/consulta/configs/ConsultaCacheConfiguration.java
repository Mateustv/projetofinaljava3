package br.com.caixa.cobranca.consulta.configs;

import br.com.caixa.cobranca.contracts.BoletoConsulta;
import br.com.caixa.cobranca.contracts.StatusBoleto;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableCaching
public class ConsultaCacheConfiguration {

    public static final String CACHE_BOLETOS = "boletos";
    public static final Duration TTL_AGUARDANDO = Duration.ofSeconds(30);
    public static final Duration TTL_FINAL = Duration.ofMinutes(10);

    @Bean
    CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(CACHE_BOLETOS);
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfter(new Expiry<Object, Object>() {
                    @Override
                    public long expireAfterCreate(Object key, Object value, long currentTime) {
                        return ttl(value).toNanos();
                    }

                    @Override
                    public long expireAfterUpdate(Object key, Object value, long currentTime, long currentDuration) {
                        return ttl(value).toNanos();
                    }

                    @Override
                    public long expireAfterRead(Object key, Object value, long currentTime, long currentDuration) {
                        return currentDuration;
                    }
                }));
        return manager;
    }

    public static Duration ttl(Object value) {
        if (!(value instanceof BoletoConsulta boleto)) {
            throw new IllegalArgumentException("O cache de boletos aceita apenas BoletoConsulta.");
        }
        return boleto.status() == StatusBoleto.AGUARDANDO_REGISTRO ? TTL_AGUARDANDO : TTL_FINAL;
    }
}
