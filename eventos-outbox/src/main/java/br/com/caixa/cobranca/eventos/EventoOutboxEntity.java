package br.com.caixa.cobranca.eventos;

import br.com.caixa.cobranca.contracts.EventoBoleto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "evento_outbox", uniqueConstraints =
        @UniqueConstraint(columnNames = {"identificador_boleto", "tipo"}))
public class EventoOutboxEntity {

    @Id
    private UUID idEvento;
    @Column(name = "identificador_boleto", nullable = false)
    private UUID identificadorBoleto;
    @Column(nullable = false, length = 32)
    private String tipo;
    @Column(nullable = false, length = 1024)
    private String eventoJson;
    @Column(nullable = false)
    private LocalDateTime criadoEm;
    private LocalDateTime publicadoEm;
    private LocalDateTime proximaTentativaEm;
    private LocalDateTime falhaDefinitivaEm;
    private int tentativas;
    @Column(length = 120)
    private String ultimoErro;

    protected EventoOutboxEntity() {
    }

    public EventoOutboxEntity(EventoBoleto evento, String json) {
        idEvento = evento.idEvento();
        identificadorBoleto = evento.identificadorBoleto();
        tipo = evento.tipo().name();
        eventoJson = json;
        criadoEm = evento.dataHora();
    }

    public void confirmar(LocalDateTime agora) {
        tentativas++;
        publicadoEm = agora;
        proximaTentativaEm = null;
        ultimoErro = null;
    }

    public void falhar(String tipoErro, boolean permanente, LocalDateTime agora) {
        tentativas++;
        ultimoErro = tipoErro;
        if (permanente || tentativas >= 3) {
            falhaDefinitivaEm = agora;
            proximaTentativaEm = null;
        } else {
            proximaTentativaEm = agora.plusSeconds(tentativas == 1 ? 1 : 5);
        }
    }

    public UUID getIdEvento() { return idEvento; }
    public UUID getIdentificadorBoleto() { return identificadorBoleto; }
    public String getEventoJson() { return eventoJson; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public LocalDateTime getPublicadoEm() { return publicadoEm; }
    public LocalDateTime getProximaTentativaEm() { return proximaTentativaEm; }
    public LocalDateTime getFalhaDefinitivaEm() { return falhaDefinitivaEm; }
    public int getTentativas() { return tentativas; }
    public String getUltimoErro() { return ultimoErro; }
}
