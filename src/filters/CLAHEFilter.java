package filters;

import java.util.List;

import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

public class CLAHEFilter implements ImageFilter {
    // ClipLimit:Limit to avoid noise amplification (usually between 1.0 and 4.0)
    private OptParam clipLimit = new OptParam("CLAHE_Clip", 2.0, 0.5, 6.0, 0.1, false);
    // GridSize: Local area size (e.g., 8x8). Larger values ​​= more global.
    private OptParam gridSize = new OptParam("CLAHE_Grid", 8, 2, 60, 2, true);

    @Override
    public Mat process(Mat input) {
        Mat output = new Mat();
    // CLAHE works only on single channel (Grayscale or L from Lab)
    // The pipeline should already be delivering grayscale here
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
