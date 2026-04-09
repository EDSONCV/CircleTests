package filters;

import java.util.List;

import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

public class MorphClosingFilter implements ImageFilter {
    // Tamanho do elemento estruturante (Kernel)
    private OptParam kernelSize = new OptParam("Morph_K", 3, 1, 11, 2, true);
    // Número de iterações (quantas vezes aplicar)
    private OptParam iterations = new OptParam("Morph_Iter", 1, 1, 5, 1, true);

    @Override
    public Mat process(Mat input) {
        Mat output = new Mat();
        int k = (int) kernelSize.getValue();
        Mat element = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(k, k));
        
        Imgproc.morphologyEx(input, output, Imgproc.MORPH_CLOSE, element, new Point(-1,-1), (int)iterations.getValue());
        return output;
    }

    @Override
    public List<OptParam> getParams() {
        return List.of(kernelSize, iterations);
    }

    @Override
    public String getName() { return "Morph Closing"; }
}
