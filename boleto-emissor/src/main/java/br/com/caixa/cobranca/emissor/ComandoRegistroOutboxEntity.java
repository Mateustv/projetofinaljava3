package br.com.caixa.cobranca.emissor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "comando_registro_outbox")
public class ComandoRegistroOutboxEntity {

    @Id
    private UUID identificador;

    @Lob
    @Column(name = "comando_json")
    private String comandoJson;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "entregue_em")
    private LocalDateTime entregueEm;

    @Column(nullable = false)
    private int tentativas;

    @Column(name = "ultimo_erro", length = 120)
    private String ultimoErro;

    protected ComandoRegistroOutboxEntity() {
    }

    public ComandoRegistroOutboxEntity(UUID identificador, String comandoJson, LocalDateTime criadoEm) {
        this.identificador = identificador;
        this.comandoJson = comandoJson;
        this.criadoEm = criadoEm;
    }

    public UUID getIdentificador() {
        return identificador;
    }

    public String getComandoJson() {
        return comandoJson;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public LocalDateTime getEntregueEm() {
        return entregueEm;
    }

    public int getTentativas() {
        return tentativas;
    }

    public String getUltimoErro() {
        return ultimoErro;
    }

    public void registrarFalha(String tipoErro) {
        tentativas++;
        ultimoErro = tipoErro;
    }

    public void registrarEntrega(LocalDateTime entregueEm) {
        tentativas++;
        this.entregueEm = entregueEm;
        comandoJson = null;
        ultimoErro = null;
    }
}
