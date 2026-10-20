package br.com.caixa.cobranca.registro;

import br.com.caixa.cobranca.contracts.RegistrarBoletoCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.temporal.ChronoUnit;

@Service
public class RegistroPersistenceService {

    private final RegistroBoletoRepository repository;
    private final RegistradoraClient registradora;
    private final ObjectMapper objectMapper;

    public RegistroPersistenceService(RegistroBoletoRepository repository,
                                     RegistradoraClient registradora,
                                     ObjectMapper objectMapper) {
        this.repository = repository;
        this.registradora = registradora;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RegistroBoletoEntity registrar(RegistrarBoletoCommand comando) {
        ResultadoRegistradora resultado = registradora.registrar(comando);
        try {
            String comandoJson = objectMapper.writeValueAsString(comando);
            return repository.saveAndFlush(new RegistroBoletoEntity(
                    comando.identificador(),
                    comandoJson,
                    comando.aceite().dataHoraRecebimento().truncatedTo(ChronoUnit.MICROS),
                    resultado
            ));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new FalhaAoRegistrarBoletoException(ex);
        }
    }
}
