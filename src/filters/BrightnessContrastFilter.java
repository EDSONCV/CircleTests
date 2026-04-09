package filters;

import java.util.ArrayList;
import java.util.List;

import org.opencv.core.Mat;

// --- Filtro 3: Ajuste de Brilho/Contraste (Exemplo de pré-tratamento) ---
public class BrightnessContrastFilter implements ImageFilter {
    private OptParam alpha = new OptParam("Contrast", 1.0, 0.5, 3.0, 0.1, false);
    private OptParam beta = new OptParam("Brightness", 0.0, -50, 50, 5, true);

    @Override
    public Mat process(Mat input) {
        Mat output = new Mat();
        input.convertTo(output, -1, alpha.getValue(), beta.getValue());
        return output;
    }

    @Override
    public List<OptParam> getParams() {
        List<OptParam> p = new ArrayList<>();
        p.add(alpha);
        p.add(beta);
        return p;
    }

    @Override
    public String getName() { return "Bright/Contrast"; }
    
}