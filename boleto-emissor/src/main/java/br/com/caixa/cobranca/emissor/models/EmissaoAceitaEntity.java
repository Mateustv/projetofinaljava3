package br.com.caixa.cobranca.emissor.models;

import br.com.caixa.cobranca.contracts.BoletoAceito;
import br.com.caixa.cobranca.contracts.StatusBoleto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "emissao_aceita", uniqueConstraints = @UniqueConstraint(
        name = "uk_emissao_convenio_controle",
        columnNames = {"convenio", "numero_controle_cliente"}
))
public class EmissaoAceitaEntity {

    @Id
    private UUID identificador;

    @Column(nullable = false, length = 6)
    private String convenio;

    @Column(name = "numero_controle_cliente", nullable = false, length = 25)
    private String numeroControleCliente;

    @Column(nullable = false, length = 64)
    private String fingerprint;

    @Lob
    @Column(name = "solicitacao_json", nullable = false)
    private String solicitacaoJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private StatusBoleto status;

    @Column(name = "data_hora_recebimento", nullable = false)
    private LocalDateTime dataHoraRecebimento;

    protected EmissaoAceitaEntity() {
    }

    public EmissaoAceitaEntity(UUID identificador,
                               String convenio,
                               String numeroControleCliente,
                               String fingerprint,
                               String solicitacaoJson,
                               LocalDateTime dataHoraRecebimento) {
        this.identificador = identificador;
        this.convenio = convenio;
        this.numeroControleCliente = numeroControleCliente;
        this.fingerprint = fingerprint;
        this.solicitacaoJson = solicitacaoJson;
        this.status = StatusBoleto.AGUARDANDO_REGISTRO;
        this.dataHoraRecebimento = dataHoraRecebimento;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public BoletoAceito comoResposta() {
        return new BoletoAceito(identificador, status, dataHoraRecebimento);
    }
}
