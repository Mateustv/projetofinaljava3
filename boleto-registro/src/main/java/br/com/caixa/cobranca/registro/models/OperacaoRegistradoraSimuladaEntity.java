package br.com.caixa.cobranca.registro.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.UUID;

@Entity
@Table(name = "registradora_simulada_operacao")
public class OperacaoRegistradoraSimuladaEntity {

    @Id
    private UUID identificador;

    // Versao nula exige INSERT para UUID atribuido; merge poderia sobrescrever uma operacao concorrente.
    @Version
    private Long versao;

    @Lob
    @Column(name = "comando_json", nullable = false)
    private String comandoJson;

    @Lob
    @Column(name = "resultado_json", nullable = false)
    private String resultadoJson;

    protected OperacaoRegistradoraSimuladaEntity() {
    }

    public OperacaoRegistradoraSimuladaEntity(UUID identificador, String comandoJson, String resultadoJson) {
        this.identificador = identificador;
        this.comandoJson = comandoJson;
        this.resultadoJson = resultadoJson;
    }

    public String getComandoJson() {
        return comandoJson;
    }

    public String getResultadoJson() {
        return resultadoJson;
    }
}
