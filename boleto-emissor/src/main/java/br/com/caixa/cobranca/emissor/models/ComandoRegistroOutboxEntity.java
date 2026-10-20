package br.com.caixa.cobranca.emissor.models;

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

    public static final int MAX_TENTATIVAS = 3;

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

    @Column(name = "proxima_tentativa_em")
    private LocalDateTime proximaTentativaEm;

    @Column(name = "falha_definitiva_em")
    private LocalDateTime falhaDefinitivaEm;

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

    public LocalDateTime getProximaTentativaEm() {
        return proximaTentativaEm;
    }

    public LocalDateTime getFalhaDefinitivaEm() {
        return falhaDefinitivaEm;
    }

    public void registrarFalha(String tipoErro, boolean permanente, LocalDateTime agora) {
        tentativas++;
        ultimoErro = tipoErro;
        if (permanente || tentativas >= MAX_TENTATIVAS) {
            isolar(tipoErro, agora);
        } else {
            proximaTentativaEm = agora.plusSeconds(tentativas == 1 ? 1 : 5);
        }
    }

    public void isolar(String tipoErro, LocalDateTime agora) {
        ultimoErro = tipoErro;
        falhaDefinitivaEm = agora;
        proximaTentativaEm = null;
    }

    public void registrarEntrega(LocalDateTime entregueEm) {
        tentativas++;
        this.entregueEm = entregueEm;
        comandoJson = null;
        ultimoErro = null;
        proximaTentativaEm = null;
    }
}
