package br.com.caixa.cobranca.emissor;

import br.com.caixa.cobranca.contracts.CampoInvalido;
import br.com.caixa.cobranca.contracts.ErroValidacao;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/** Traduz erros de entrada em HTTP 400 com corpo padronizado (ErroValidacao). */
@RestControllerAdvice
public class ErroHandler {

    @ExceptionHandler(EmissaoInvalidaException.class)
    public ResponseEntity<ErroValidacao> aoInvalidar(EmissaoInvalidaException ex) {
        return ResponseEntity.badRequest().body(new ErroValidacao(ex.getErros()));
    }

    /** JSON malformado ou tipo errado (ex.: tipo_documento = "RG"): tambem 400, sem vazar detalhes. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErroValidacao> aoLerCorpo(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(new ErroValidacao(List.of(new CampoInvalido("corpo", "JSON invalido ou com valor fora do formato"))));
    }

    @ExceptionHandler(ChaveIdempotenciaConflitanteException.class)
    public ResponseEntity<ConflitoIdempotencia> aoConflitarIdempotencia(ChaveIdempotenciaConflitanteException ex) {
        return ResponseEntity.status(409)
                .body(new ConflitoIdempotencia("CHAVE_IDEMPOTENCIA_CONFLITANTE", ex.getMessage()));
    }

    @ExceptionHandler(FalhaAoAceitarEmissaoException.class)
    public ResponseEntity<Void> aoFalharPersistencia(FalhaAoAceitarEmissaoException ex) {
        return ResponseEntity.status(503).build();
    }
}
