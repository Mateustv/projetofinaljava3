package br.com.caixa.cobranca.consulta;

import br.com.caixa.cobranca.contracts.BoletoConsulta;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/boletos")
public class ConsultaBoletoController {

    private final ConsultaBoletoService consulta;

    public ConsultaBoletoController(ConsultaBoletoService consulta) {
        this.consulta = consulta;
    }

    @GetMapping("/{identificador}")
    public ResponseEntity<BoletoConsulta> consultar(@PathVariable String identificador) {
        UUID boletoId;
        try {
            boletoId = UUID.fromString(identificador);
        } catch (IllegalArgumentException ex) {
            throw new IdentificadorBoletoInvalidoException();
        }
        return ResponseEntity.ok(consulta.consultar(boletoId));
    }
}
