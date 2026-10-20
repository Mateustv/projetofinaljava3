package br.com.caixa.cobranca.registro.services;

import br.com.caixa.cobranca.registro.exceptions.ComandoRegistroConflitanteException;
import br.com.caixa.cobranca.registro.exceptions.ComandoRegistroInvalidoException;
import br.com.caixa.cobranca.registro.exceptions.FalhaAoRegistrarBoletoException;
import br.com.caixa.cobranca.registro.models.RegistroBoletoEntity;
import br.com.caixa.cobranca.registro.repositories.RegistroBoletoRepository;
import br.com.caixa.cobranca.contracts.BoletoConsulta;
import br.com.caixa.cobranca.contracts.RegistrarBoletoCommand;
import br.com.caixa.cobranca.contracts.StatusBoleto;
import br.com.caixa.cobranca.contracts.SolicitacaoBoletoValidator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class RegistroBoletoService {

    private static final int QUANTIDADE_TRAVAS = 64;
    private final SolicitacaoBoletoValidator validator = new SolicitacaoBoletoValidator();

    private final RegistroBoletoRepository repository;
    private final RegistroPersistenceService persistencia;
    private final ObjectMapper objectMapper;
    private final ReentrantLock[] travas = criarTravas();

    public RegistroBoletoService(RegistroBoletoRepository repository,
                                 RegistroPersistenceService persistencia,
                                 ObjectMapper objectMapper) {
        this.repository = repository;
        this.persistencia = persistencia;
        this.objectMapper = objectMapper;
    }

    public ResultadoRegistro registrar(RegistrarBoletoCommand comando) {
        validar(comando);
        String comandoJson = serializar(comando);
        ReentrantLock trava = travas[Math.floorMod(comando.identificador().hashCode(), travas.length)];
        trava.lock();
        try {
            Optional<RegistroBoletoEntity> existente = buscarEntidade(comando.identificador());
            if (existente.isPresent()) {
                return new ResultadoRegistro(verificarDuplicata(existente.get(), comandoJson), true);
            }
            try {
                return new ResultadoRegistro(persistencia.registrar(comando).comoConsulta(), false);
            } catch (DataIntegrityViolationException ex) {
                Optional<RegistroBoletoEntity> concorrente = buscarEntidade(comando.identificador());
                if (concorrente.isPresent()) {
                    return new ResultadoRegistro(verificarDuplicata(concorrente.get(), comandoJson), true);
                }
                throw new FalhaAoRegistrarBoletoException(ex);
            } catch (DataAccessException ex) {
                throw new FalhaAoRegistrarBoletoException(ex);
            }
        } finally {
            trava.unlock();
        }
    }

    public Optional<BoletoConsulta> buscar(UUID identificador) {
        try {
            return repository.findById(identificador).map(RegistroBoletoEntity::comoConsulta);
        } catch (DataAccessException ex) {
            throw new FalhaAoRegistrarBoletoException(ex);
        }
    }

    private Optional<RegistroBoletoEntity> buscarEntidade(UUID identificador) {
        try {
            return repository.findById(identificador);
        } catch (DataAccessException ex) {
            throw new FalhaAoRegistrarBoletoException(ex);
        }
    }

    private BoletoConsulta verificarDuplicata(RegistroBoletoEntity existente, String comandoJson) {
        if (!ConteudoComando.igual(objectMapper, existente.getComandoJson(), comandoJson)) {
            throw new ComandoRegistroConflitanteException();
        }
        return existente.comoConsulta();
    }

    private void validar(RegistrarBoletoCommand comando) {
        if (comando == null
                || comando.identificador() == null
                || comando.aceite() == null
                || comando.solicitacao() == null
                || !comando.identificador().equals(comando.aceite().identificador())
                || comando.aceite().status() != StatusBoleto.AGUARDANDO_REGISTRO
                || comando.aceite().dataHoraRecebimento() == null
                || comando.identificador().version() != 4
                || comando.identificador().variant() != 2
                || comando.correlationId() == null
                || comando.correlationId().version() != 4
                || comando.correlationId().variant() != 2
                || !validator.validar(comando.solicitacao()).isEmpty()) {
            throw new ComandoRegistroInvalidoException();
        }
    }

    private String serializar(RegistrarBoletoCommand comando) {
        try {
            return objectMapper.writeValueAsString(comando);
        } catch (JsonProcessingException ex) {
            throw new FalhaAoRegistrarBoletoException(ex);
        }
    }

    private static ReentrantLock[] criarTravas() {
        ReentrantLock[] resultado = new ReentrantLock[QUANTIDADE_TRAVAS];
        for (int i = 0; i < resultado.length; i++) {
            resultado[i] = new ReentrantLock();
        }
        return resultado;
    }

    public record ResultadoRegistro(BoletoConsulta boleto, boolean duplicado) {
    }
}
