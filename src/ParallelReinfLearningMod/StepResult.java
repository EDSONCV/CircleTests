package ParallelReinfLearningMod;

import org.opencv.core.Mat;
import java.util.List;

/**
 * Representa o resultado de uma etapa do pipeline para fins de visualização.
 */
class StepResult {
    public Mat image;           // A imagem visual resultante desta etapa
    public String stepName;     // O nome do filtro (ex: "Gaussian Blur")
    public String paramsDescription; // A string formatada dos parâmetros (ex: "Kernel=5, Sigma=2.0")

    public StepResult(Mat image, String stepName, String paramsDescription) {
        this.image = image; // Nota: Deve ser um clone se a origem for reutilizada
        this.stepName = stepName;
        this.paramsDescription = paramsDescription;
    }
}