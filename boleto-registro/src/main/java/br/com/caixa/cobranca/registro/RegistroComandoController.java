package br.com.caixa.cobranca.registro;

import br.com.caixa.cobranca.contracts.BoletoConsulta;
import br.com.caixa.cobranca.contracts.RegistrarBoletoCommand;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@RestController
@ConditionalOnProperty(name = "registro.http-comandos.enabled", havingValue = "true")
@RequestMapping("/interno/comandos")
public class RegistroComandoController {

    private final RegistroBoletoService service;

    public RegistroComandoController(RegistroBoletoService service) {
        this.service = service;
    }

    @PostMapping("/registro")
    public ResponseEntity<BoletoConsulta> registrar(@RequestBody RegistrarBoletoCommand comando) {
        RegistroBoletoService.ResultadoRegistro resultado = service.registrar(comando);
        HttpStatus status = resultado.duplicado() ? HttpStatus.OK : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).body(resultado.boleto());
    }
}
