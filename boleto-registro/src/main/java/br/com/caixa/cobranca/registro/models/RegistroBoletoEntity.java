package br.com.caixa.cobranca.registro.models;

import br.com.caixa.cobranca.registro.dtos.ResultadoRegistradora;
import br.com.caixa.cobranca.contracts.BoletoConsulta;
import br.com.caixa.cobranca.contracts.StatusBoleto;
import jakarta.persistence.Column;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.time.temporal.ChronoUnit;

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

    @Column(name = "linha_digitavel", length = 96)
    private String linhaDigitavel;

    @Column(name = "codigo_barras", length = 96)
    private String codigoBarras;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "registro_historico", joinColumns = @JoinColumn(name = "identificador"),
            uniqueConstraints = @UniqueConstraint(columnNames = {"identificador", "status"}))
    @OrderColumn(name = "ordem")
    private List<TransicaoRegistro> historico = new ArrayList<>();

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
        this.linhaDigitavel = resultado.linhaDigitavel();
        this.codigoBarras = resultado.codigoBarras();
        historico.add(new TransicaoRegistro(StatusBoleto.AGUARDANDO_REGISTRO, dataHoraRecebimento));
        historico.add(new TransicaoRegistro(status, LocalDateTime.now().truncatedTo(ChronoUnit.MICROS)));
    }

    public List<TransicaoRegistro> getHistorico() {
        return List.copyOf(historico);
    }

    public String getComandoJson() {
        return comandoJson;
    }

    public BoletoConsulta comoConsulta() {
        return new BoletoConsulta(
                identificador,
                status,
                dataHoraRecebimento,
                linhaDigitavel,
                codigoBarras,
                identificadorRegistradora,
                dataHoraRegistro,
                codigoRejeicao,
                motivoRejeicao
        );
    }
}
