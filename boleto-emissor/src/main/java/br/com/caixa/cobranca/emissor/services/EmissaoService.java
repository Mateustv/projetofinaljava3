package br.com.caixa.cobranca.emissor.services;

import br.com.caixa.cobranca.emissor.exceptions.ChaveIdempotenciaConflitanteException;
import br.com.caixa.cobranca.emissor.exceptions.EmissaoInvalidaException;
import br.com.caixa.cobranca.emissor.exceptions.FalhaAoAceitarEmissaoException;
import br.com.caixa.cobranca.emissor.models.EmissaoAceitaEntity;
import br.com.caixa.cobranca.emissor.repositories.EmissaoAceitaRepository;
import br.com.caixa.cobranca.emissor.validators.EmissaoValidator;
import br.com.caixa.cobranca.contracts.BoletoAceito;
import br.com.caixa.cobranca.contracts.CampoInvalido;
import br.com.caixa.cobranca.contracts.EmitirBoletoRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class EmissaoService {

    private static final int QUANTIDADE_TRAVAS = 64;

    private final EmissaoValidator validator;
    private final EmissaoAceitaRepository repository;
    private final EmissaoPersistenceService persistencia;
    private final ObjectMapper objectMapper;
    private final ReentrantLock[] travas = criarTravas();

    public EmissaoService(EmissaoValidator validator,
                          EmissaoAceitaRepository repository,
                          EmissaoPersistenceService persistencia,
                          ObjectMapper objectMapper) {
        this.validator = validator;
        this.repository = repository;
        this.persistencia = persistencia;
        this.objectMapper = objectMapper;
    }

    public BoletoAceito aceitar(EmitirBoletoRequest request) {
        List<CampoInvalido> erros = validator.validar(request);
        if (!erros.isEmpty()) {
            throw new EmissaoInvalidaException(erros);
        }

        String fingerprint;
        String payload;
        try {
            fingerprint = fingerprint(request);
            payload = objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException ex) {
            throw new FalhaAoAceitarEmissaoException(ex);
        }

        ReentrantLock trava = travaPara(request.convenio(), request.numeroControleCliente());
        trava.lock();
        try {
            return aceitarComChaveProtegida(request, fingerprint, payload);
        } finally {
            trava.unlock();
        }
    }

    private BoletoAceito aceitarComChaveProtegida(EmitirBoletoRequest request,
                                                   String fingerprint,
                                                   String payload) {
        Optional<EmissaoAceitaEntity> existente = buscarPorChave(request.convenio(), request.numeroControleCliente());
        if (existente.isPresent()) {
            return devolverOuConflitar(existente.get(), fingerprint);
        }

        try {
            return persistencia.persistir(request, fingerprint, payload);
        } catch (DataIntegrityViolationException ex) {
            Optional<EmissaoAceitaEntity> concorrente =
                    buscarPorChave(request.convenio(), request.numeroControleCliente());
            if (concorrente.isPresent()) {
                return devolverOuConflitar(concorrente.get(), fingerprint);
            }
            throw new FalhaAoAceitarEmissaoException(ex);
        } catch (DataAccessException ex) {
            throw new FalhaAoAceitarEmissaoException(ex);
        }
    }

    private ReentrantLock travaPara(String convenio, String numeroControleCliente) {
        int indice = Math.floorMod(31 * convenio.hashCode() + numeroControleCliente.hashCode(), travas.length);
        return travas[indice];
    }

    private static ReentrantLock[] criarTravas() {
        ReentrantLock[] resultado = new ReentrantLock[QUANTIDADE_TRAVAS];
        for (int i = 0; i < resultado.length; i++) {
            resultado[i] = new ReentrantLock();
        }
        return resultado;
    }

    public Optional<BoletoAceito> buscarAceite(UUID identificador) {
        try {
            return repository.findById(identificador).map(EmissaoAceitaEntity::comoResposta);
        } catch (DataAccessException ex) {
            throw new FalhaAoAceitarEmissaoException(ex);
        }
    }

    private Optional<EmissaoAceitaEntity> buscarPorChave(String convenio, String numeroControleCliente) {
        try {
            return repository.findByConvenioAndNumeroControleCliente(convenio, numeroControleCliente);
        } catch (DataAccessException ex) {
            throw new FalhaAoAceitarEmissaoException(ex);
        }
    }

    private BoletoAceito devolverOuConflitar(EmissaoAceitaEntity existente, String fingerprint) {
        if (!existente.getFingerprint().equals(fingerprint)) {
            throw new ChaveIdempotenciaConflitanteException();
        }
        return existente.comoResposta();
    }

    private String fingerprint(EmitirBoletoRequest request) throws JsonProcessingException {
        JsonNode tree = objectMapper.valueToTree(request);
        String canonicalJson = canonicalizar(tree);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonicalJson.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponivel na plataforma", ex);
        }
    }

    private String canonicalizar(JsonNode node) throws JsonProcessingException {
        if (node.isObject()) {
            Map<String, JsonNode> campos = new TreeMap<>();
            node.fields().forEachRemaining(entry -> campos.put(entry.getKey(), entry.getValue()));
            StringBuilder json = new StringBuilder("{");
            boolean primeiro = true;
            for (Map.Entry<String, JsonNode> campo : campos.entrySet()) {
                if (!primeiro) {
                    json.append(',');
                }
                json.append(objectMapper.writeValueAsString(campo.getKey()))
                        .append(':')
                        .append(canonicalizar(campo.getValue()));
                primeiro = false;
            }
            return json.append('}').toString();
        }
        if (node.isArray()) {
            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < node.size(); i++) {
                if (i > 0) {
                    json.append(',');
                }
                json.append(canonicalizar(node.get(i)));
            }
            return json.append(']').toString();
        }
        if (node.isNumber()) {
            return node.decimalValue().stripTrailingZeros().toPlainString();
        }
        return objectMapper.writeValueAsString(node);
    }
}
