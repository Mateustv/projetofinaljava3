package br.com.caixa.cobranca.http.demo;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@Profile("demonstracao")
@RequestMapping("/demonstracao/rastros")
public class RastrosController {
    @GetMapping("/{boleto}")
    public Janela buscar(@PathVariable UUID boleto) {
        return new Janela(Instant.now(), "Memoria local: ultimos 2000 rastros por JVM; reinicio limpa tudo.",
                Rastros.buscar(boleto));
    }

    public record Janela(Instant observadoEm, String limite, List<Rastros.Rastro> rastros) {
    }
}
