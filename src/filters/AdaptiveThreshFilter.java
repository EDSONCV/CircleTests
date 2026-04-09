package filters;
import java.util.List;

import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

public class AdaptiveThreshFilter implements ImageFilter {
    // Tamanho do bloco de vizinhança (deve ser ímpar)
    private OptParam blockSize = new OptParam("Thresh_Block", 11, 3, 51, 2, true);
    // Constante subtraída da média (ajuste fino de ruído)
    private OptParam cParam = new OptParam("Thresh_C", 2, -10, 10, 1, true);

    @Override
    public Mat process(Mat input) {
        Mat output = new Mat();
        // ADAPTIVE_THRESH_GAUSSIAN_C geralmente é mais natural que o MEAN_C
        Imgproc.adaptiveThreshold(input, output, 255, 
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, 
                Imgproc.THRESH_BINARY, 
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
