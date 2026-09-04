package filters;

import java.util.ArrayList;
import java.util.List;

import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

// ---  Gaussian Blur Filter ---
public class GaussianBlurFilter implements ImageFilter {

    private OptParam kernel = new OptParam("G_Kernel", 11, 1, 15, 2, true);
    private OptParam sigma = new OptParam("G_Sigma", 4, 0.5, 4, 0.5, false);

    @Override
    public Mat process(Mat input) {
        Mat output = new Mat();

        // Get the Kernel value
        int k = (int) kernel.getValue();

        // SAFETY LOCK: If for any reason the number is even, add 1.
        // Example: If the AI sends 4, it becomes 5. If it sends 2, it becomes 3.
        if (k % 2 == 0) {
            k++;
        }
        // Ensures that the Kernel is never zero or negative (if k is < 1, it becomes 1).
        if (k < 1) {
            k = 1;
        }

        // Run OpenCV with the assurance that the math won't break down.
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