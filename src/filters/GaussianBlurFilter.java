package filters;

import java.util.ArrayList;
import java.util.List;

import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

// --- Filtro 1: Gaussian Blur ---
public class GaussianBlurFilter implements ImageFilter {
    private OptParam kernel = new OptParam("G_Kernel", 5, 1, 9, 2, true);
    private OptParam sigma = new OptParam("G_Sigma", 2.0, 0.5, 3, 0.5, false);

    @Override
    public Mat process(Mat input) {
        Mat output = new Mat();
        int k = (int) kernel.getValue();
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