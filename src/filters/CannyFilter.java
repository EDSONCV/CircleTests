package filters;

import java.util.ArrayList;
import java.util.List;

import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

// --- Filtro 2: Canny Edge Detection (Exemplo de novo módulo) ---
public class CannyFilter implements ImageFilter {
    private OptParam threshold1 = new OptParam("Canny_T1", 100, 10, 300, 10, true);
    private OptParam threshold2 = new OptParam("Canny_T2", 200, 10, 300, 10, true);

    @Override
    public Mat process(Mat input) {
        Mat output = new Mat();
        Imgproc.Canny(input, output, threshold1.getValue(), threshold2.getValue());
        return output;
    }

    @Override
    public List<OptParam> getParams() {
        List<OptParam> p = new ArrayList<>();
        p.add(threshold1);
        p.add(threshold2);
        return p;
    }
    
    @Override
    public String getName() { return "Canny Edge"; }
}