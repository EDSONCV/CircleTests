package filters;

import javax.swing.*;

// Parameters Class
public class FilterParam {
    public String name; public double value;
    double min;
    double max;
    public JTextField textField; // Ligação direta com a GUI

    public FilterParam(String name, double value, double min, double max) {
        this.name = name; this.value = value; this.min = min; this.max = max;
    }
    // Reads the JTextField from interface
    public void updateFromUI() {
        try {
            double val = Double.parseDouble(textField.getText().replace(",", "."));
            this.value = Math.max(min, Math.min(max, val)); // limits between min and max
            textField.setText(String.valueOf(this.value)); // Updates UI with the filtered value
        } catch (Exception e) {
            textField.setText(String.valueOf(this.value)); // Reverts value on error
        }
    }
    public String toString() { return name + "=" + value; }
}
