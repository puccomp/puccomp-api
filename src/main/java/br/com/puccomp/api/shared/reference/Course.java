package br.com.puccomp.api.shared.reference;

public enum Course {
    COMPUTER_SCIENCE("Ciência da Computação"),
    DATA_SCIENCE("Ciência de Dados"),
    SOFTWARE_ENGINEERING("Engenharia de Software"),
    COMPUTER_ENGINEERING("Engenharia de Computação"),
    INFORMATION_SYSTEMS("Sistemas de Informação");

    private final String label;

    Course(String label) {
        this.label = label;
    }

    public String getLabel() { return label; }
}
