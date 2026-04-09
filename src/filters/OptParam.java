package filters;
import java.util.ArrayList;
import java.util.List;

/**
 * Representa um parâmetro otimizável genérico (Strategy/Command state).
 * Encapsula valor, limites e passo de modificação.
 */
public class OptParam {
    private String name;
    private double value;
    private double min;
    private double max;
    private double step;
    private boolean isInteger; // Define se deve arredondar (ex: Kernel Size)

    public OptParam(String name, double value, double min, double max, double step, boolean isInteger) {
        this.name = name;
        this.value = value;
        this.min = min;
        this.max = max;
        this.step = step;
        this.isInteger = isInteger;
    }

    public void increase() {
        this.value = Math.min(max, value + step);
        enforceConstraints();
    }

    public void decrease() {
        this.value = Math.max(min, value - step);
        enforceConstraints();
    }
    
    // Garante regras específicas (ex: kernel ímpar)
    private void enforceConstraints() {
        if (name.contains("Kernel") && (int)value % 2 == 0) {
            value = (value >= max) ? value - 1 : value + 1;
        }
    }

    public double getValue() { return isInteger ? Math.round(value) : value; }
    public String getName() { return name; }
    
    @Override
    public String toString() {
        return isInteger ? String.format("%s=%d", name, (int)value) : String.format("%s=%.1f", name, value);
    }

	public void setValue(double value2) {
		this.value = value2;
		// TODO Auto-generated method stub
		
	}

	public double getMin() {
		return min;
	}

	public double getMax() {
		return max;
	}

	public double getStep() {
		return step;
	}

	public boolean isInteger() {
		return isInteger;
	}
	
}