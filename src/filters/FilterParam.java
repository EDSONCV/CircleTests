package filters;

import javax.swing.*;

// 1. Classe para Parâmetros
public class FilterParam {
    public String name; public double value;
    double min;
    double max;
    public JTextField textField; // Ligação direta com a GUI

    public FilterParam(String name, double value, double min, double max) {
        this.name = name; this.value = value; this.min = min; this.max = max;
    }
    // Lê o valor do JTextField digitado pelo usuário
    public void updateFromUI() {
        try {
            double val = Double.parseDouble(textField.getText().replace(",", "."));
            this.value = Math.max(min, Math.min(max, val)); // Limita entre min e max
            textField.setText(String.valueOf(this.value)); // Atualiza a UI com o valor filtrado
        } catch (Exception e) {
            textField.setText(String.valueOf(this.value)); // Reverte se der erro
        }
    }
    public String toString() { return name + "=" + value; }
}
