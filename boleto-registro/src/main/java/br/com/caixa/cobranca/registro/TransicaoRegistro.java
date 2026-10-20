package br.com.caixa.cobranca.registro;

import br.com.caixa.cobranca.contracts.StatusBoleto;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.time.LocalDateTime;

@Embeddable
public class TransicaoRegistro {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private StatusBoleto status;

    @Column(name = "ocorrido_em", nullable = false)
    private LocalDateTime ocorridoEm;

    protected TransicaoRegistro() {
    }

    public TransicaoRegistro(StatusBoleto status, LocalDateTime ocorridoEm) {
        this.status = status;
        this.ocorridoEm = ocorridoEm;
    }

    public StatusBoleto getStatus() {
        return status;
    }

    public LocalDateTime getOcorridoEm() {
        return ocorridoEm;
    }
}
