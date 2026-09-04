package ParallelReinfLearningMod;

import org.opencv.core.Mat;
import java.util.List;

/**
 * Representa o resultado de uma etapa do pipeline para fins de visualização.
 */
class StepResult {
    public Mat image; // The resulting visual image from this step
    public String stepName; // The filter name (e.g., "Gaussian Blur")
    public String paramsDescription; // The formatted string of parameters (e.g., "Kernel=5, Sigma=2.0")
    public StepResult(Mat image, String stepName, String paramsDescription) {
        this.image = image; // Note: Must be a clone if the source is reused
        this.stepName = stepName;
        this.paramsDescription = paramsDescription;
    }
}