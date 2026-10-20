package br.com.caixa.cobranca.registro;

import br.com.caixa.cobranca.contracts.BoletoConsulta;
import br.com.caixa.cobranca.contracts.StatusBoleto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "boleto_registrado")
public class RegistroBoletoEntity {

    @Id
    private UUID identificador;

    @Lob
    @Column(name = "comando_json", nullable = false)
    private String comandoJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private StatusBoleto status;

    @Column(name = "data_hora_recebimento", nullable = false)
    private LocalDateTime dataHoraRecebimento;

    @Column(name = "identificador_registradora", length = 64)
    private String identificadorRegistradora;

    @Column(name = "data_hora_registro")
    private LocalDateTime dataHoraRegistro;

    @Column(name = "codigo_rejeicao", length = 64)
    private String codigoRejeicao;

    @Column(name = "motivo_rejeicao", length = 255)
    private String motivoRejeicao;

    protected RegistroBoletoEntity() {
    }

    public RegistroBoletoEntity(UUID identificador,
                                String comandoJson,
                                LocalDateTime dataHoraRecebimento,
                                ResultadoRegistradora resultado) {
        this.identificador = identificador;
        this.comandoJson = comandoJson;
        this.status = resultado.status();
        this.dataHoraRecebimento = dataHoraRecebimento;
        this.identificadorRegistradora = resultado.identificador();
        this.dataHoraRegistro = resultado.dataHoraRegistro();
        this.codigoRejeicao = resultado.codigoRejeicao();
        this.motivoRejeicao = resultado.motivoRejeicao();
    }

    public String getComandoJson() {
        return comandoJson;
    }

    public BoletoConsulta comoConsulta() {
        return new BoletoConsulta(
                identificador,
                status,
                dataHoraRecebimento,
                null,
                null,
                identificadorRegistradora,
                dataHoraRegistro,
                codigoRejeicao,
                motivoRejeicao
        );
    }
}
