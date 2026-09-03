package filters;

import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.util.List;

public class AdaptiveThreshMeanCFilterInverse implements ImageFilter {
    // Tamanho do bloco de vizinhança (deve ser ímpar)
    private OptParam blockSize = new OptParam("Thresh_Block", 21, 3, 51, 2, true);
    // Constante subtraída da média (ajuste fino de ruído)
    private OptParam cParam = new OptParam("Thresh_C", 2, -10, 10, 1, true);

    @Override
    public Mat process(Mat input) {
        Mat output = new Mat();

        Imgproc.adaptiveThreshold(input, output, 255,
                Imgproc.ADAPTIVE_THRESH_MEAN_C,
                Imgproc.THRESH_BINARY_INV,
                (int)blockSize.getValue(),
                cParam.getValue());
        return output;
    }

    @Override
    public List<OptParam> getParams() {
        return List.of(blockSize, cParam);
    }

    @Override
    public String getName() { return "Adapt Threshold"; }
}
