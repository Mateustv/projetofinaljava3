package br.com.caixa.cobranca.registro;

import br.com.caixa.cobranca.contracts.RegistrarBoletoCommand;
import br.com.caixa.cobranca.contracts.StatusBoleto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
public class RegistradoraSimuladaPersistence {

    private final OperacaoRegistradoraSimuladaRepository repository;
    private final ObjectMapper objectMapper;

    public RegistradoraSimuladaPersistence(OperacaoRegistradoraSimuladaRepository repository,
                                          ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    // Representa o commit externo: nao deve desfazer quando o registro local falhar.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ResultadoRegistradora registrar(RegistrarBoletoCommand comando, RegistradoraSimulada.Cenario cenario) {
        try {
            String json = objectMapper.writeValueAsString(comando);
            var existente = repository.findById(comando.identificador());
            if (existente.isPresent()) {
                if (!ConteudoComando.igual(objectMapper, existente.get().getComandoJson(), json)) {
                    throw new ComandoRegistroConflitanteException();
                }
                return objectMapper.readValue(existente.get().getResultadoJson(), ResultadoRegistradora.class);
            }
            ResultadoRegistradora resultado = switch (cenario) {
                case APROVAR -> new ResultadoRegistradora(StatusBoleto.REGISTRADO,
                        "SIM-" + comando.identificador(),
                        LocalDateTime.now().truncatedTo(ChronoUnit.MICROS), null, null,
                        "NAO-PAGAVEL-SIM-LINHA-" + comando.identificador(),
                        "NAO-PAGAVEL-SIM-BARRAS-" + comando.identificador());
                case REJEITAR -> new ResultadoRegistradora(StatusBoleto.REJEITADO, null, null,
                        "SIM-REJ-001", "Titulo recusado pela registradora simulada.", null, null);
                case INDISPONIVEL -> throw new FalhaAoRegistrarBoletoException(
                        new IllegalStateException("Registradora simulada indisponivel."));
            };
            repository.saveAndFlush(new OperacaoRegistradoraSimuladaEntity(
                    comando.identificador(), json, objectMapper.writeValueAsString(resultado)));
            return resultado;
        } catch (JsonProcessingException ex) {
            throw new FalhaAoRegistrarBoletoException(ex);
        }
    }
}
