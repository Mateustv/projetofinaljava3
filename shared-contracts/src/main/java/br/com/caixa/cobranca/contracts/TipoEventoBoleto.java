package br.com.caixa.cobranca.contracts;

public enum TipoEventoBoleto {
    EmissaoAceita(StatusBoleto.AGUARDANDO_REGISTRO),
    BoletoRegistrado(StatusBoleto.REGISTRADO),
    RegistroRejeitado(StatusBoleto.REJEITADO);

    private final StatusBoleto status;

    TipoEventoBoleto(StatusBoleto status) {
        this.status = status;
    }

    public StatusBoleto status() {
        return status;
    }
}
