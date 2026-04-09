package filters;

import java.util.List;

import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

public class BilateralFilter implements ImageFilter {
    // SigmaColor: quanto as cores vizinhas devem ser parecidas para serem misturadas
    private OptParam sigmaColor = new OptParam("Bi_SigColor", 75, 10, 150, 5, false);
    // SigmaSpace: distância dos pixels a serem considerados (similar ao Kernel do Gauss)
    private OptParam sigmaSpace = new OptParam("Bi_SigSpace", 75, 10, 150, 5, false);
    // Diameter: Diâmetro da vizinhança de pixel (similar ao kernel size)
    private OptParam diameter = new OptParam("Bi_Dia", 9, 1, 15, 2, true);

    @Override
    public Mat process(Mat input) {
        Mat output = new Mat();
        // O filtro bilateral requer imagem de entrada de 8 bits
        Imgproc.bilateralFilter(input, output, (int)diameter.getValue(), sigmaColor.getValue(), sigmaSpace.getValue());
        return output;
    }

    @Override
    public List<OptParam> getParams() {
        return List.of(diameter, sigmaColor, sigmaSpace);
    }
    
    @Override
    public String getName() { return "Bilateral Filter"; }
}
