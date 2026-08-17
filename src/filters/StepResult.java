package filters;

import org.opencv.core.Mat;

// 3. Resultado de cada etapa
public class StepResult {
    public String stepName;
    public String paramsDescription;
    public Mat image;
    public StepResult(String name, String params, Mat img) {
        this.stepName = name; this.paramsDescription = params; this.image = img.clone();
    }
}
