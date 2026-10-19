package br.com.caixa.cobranca.emissor;

import br.com.caixa.cobranca.contracts.BoletoAceito;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/interno/boletos")
public class ConsultaInternaBoletoController {

    private final EmissaoService emissao;

    public ConsultaInternaBoletoController(EmissaoService emissao) {
        this.emissao = emissao;
    }

    @GetMapping("/{identificador}")
    public ResponseEntity<BoletoAceito> consultarAceite(@PathVariable UUID identificador) {
        Optional<BoletoAceito> aceite = emissao.buscarAceite(identificador);
        return aceite.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }
}
