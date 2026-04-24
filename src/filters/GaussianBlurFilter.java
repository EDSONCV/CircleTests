package filters;

import java.util.ArrayList;
import java.util.List;

import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

// --- Filtro 1: Gaussian Blur ---
public class GaussianBlurFilter implements ImageFilter {
    /*
    private OptParam kernel = new OptParam("G_Kernel", 5, 1, 9, 2, true);
    private OptParam sigma = new OptParam("G_Sigma", 2.0, 0.5, 3, 0.5, false);
*/
    private OptParam kernel = new OptParam("G_Kernel", 11, 3, 9, 2, true);
    private OptParam sigma = new OptParam("G_Sigma", 7, 0.5, 3, 0.5, false);

    @Override
    public Mat process(Mat input) {
        Mat output = new Mat();

        // 1. Pega o valor do Kernel definido pela IA / Scrambler
        int k = (int) kernel.getValue();

        // 2. TRAVA DE SEGURANÇA: Se por qualquer motivo o número for par, soma 1.
        // Exemplo: Se a IA enviar 4, vira 5. Se enviar 2, vira 3.
        if (k % 2 == 0) {
            k++;
        }
        // Garante que o Kernel nunca seja zero ou negativo (se k for < 1, vira 1)
        if (k < 1) {
            k = 1;
        }

        // 3. Executa o OpenCV com a certeza de que a matemática não vai quebrar
        Imgproc.GaussianBlur(input, output, new Size(k, k), sigma.getValue());
        return output;
    }

    @Override
    public List<OptParam> getParams() {
        List<OptParam> params = new ArrayList<>();
        params.add(kernel);
        params.add(sigma);
        return params;
    }

    @Override
    public String getName() { return "Gaussian Blur"; }
}