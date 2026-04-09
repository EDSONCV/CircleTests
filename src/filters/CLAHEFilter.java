package filters;

import java.util.List;

import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

public class CLAHEFilter implements ImageFilter {
    // ClipLimit: Limite para evitar amplificação de ruído (geralmente entre 1.0 e 4.0)
    private OptParam clipLimit = new OptParam("CLAHE_Clip", 2.0, 0.5, 6.0, 0.5, false);
    // GridSize: Tamanho da área local (ex: 8x8). Valores maiores = mais global.
    private OptParam gridSize = new OptParam("CLAHE_Grid", 8, 2, 32, 2, true);

    @Override
    public Mat process(Mat input) {
        Mat output = new Mat();
        // CLAHE funciona apenas em canal único (Grayscale ou L do Lab)
        // O pipeline já deve estar entregando grayscale aqui
        org.opencv.imgproc.CLAHE clahe = Imgproc.createCLAHE(clipLimit.getValue(), new Size(gridSize.getValue(), gridSize.getValue()));
        clahe.apply(input, output);
        return output;
    }

    @Override
    public List<OptParam> getParams() {
        return List.of(clipLimit, gridSize);
    }

    @Override
    public String getName() { return "CLAHE (Contrast)"; }
}
