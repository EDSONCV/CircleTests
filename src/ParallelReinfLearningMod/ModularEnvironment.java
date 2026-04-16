package ParallelReinfLearningMod;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import filters.OptParam;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ModularEnvironment {
    private String imagePath;
    private List<Circle> groundTruth;
    private ProcessingPipeline pipelinePrototype; // Protótipo (usado apenas para clonagem)
    private Mat originalImage;
    private RewardConfig rewardConfig;

    /**
     * Construtor
     */
    public ModularEnvironment(String path, List<Circle> gt, ProcessingPipeline pipe, RewardConfig rConfig) {
        this.imagePath = path;
        this.groundTruth = gt;
        this.pipelinePrototype = pipe;
        
        // Se config for nula, usa padrão
        this.rewardConfig = (rConfig != null) ? rConfig : new RewardConfig();

        this.originalImage = Imgcodecs.imread(path);
        if (this.originalImage.empty()) {
            System.err.println("Erro crítico: Imagem não encontrada em " + path);
            System.exit(1);
        }
    }

    // --- MÉTODOS DE DETECÇÃO ---

    /**
     * Versão Padrão (Single Thread): Usa os parâmetros atuais do pipeline interno.
     */
    public List<Circle> runDetection() {
        // Pega os parâmetros que estão configurados no pipeline neste momento
        return runDetectionInternal(this.pipelinePrototype);
    }

    /**
     * Versão Multithread (Safe): Recebe uma lista de parâmetros candidatos,
     * cria um pipeline temporário, aplica os valores e executa.
     * * @param paramsForThisRun A lista de parâmetros (variáveis de decisão) para este teste específico.
     */
    public List<Circle> runDetection(List<OptParam> paramsForThisRun) {
        // 1. CLONAGEM CRÍTICA:
        // Precisamos de uma cópia isolada do pipeline para não afetar outras threads.
        // O método 'cloneWithParams' deve ser implementado no ProcessingPipeline (veja abaixo).
        ProcessingPipeline localPipeline = this.pipelinePrototype.cloneWithParams(paramsForThisRun);

        // 2. Executa usando essa cópia local
        return runDetectionInternal(localPipeline);
    }

    /**
     * Método interno privado que executa a lógica comum (Hough) dado um pipeline já configurado.
     */
    private List<Circle> runDetectionInternal(ProcessingPipeline pipeToUse) {
        // A. Executa os filtros (Blur, Canny, etc.)
        Mat processedImage = pipeToUse.executePipeline(originalImage);
        
        Mat circlesMat = new Mat();
        List<Circle> detectedList = new ArrayList<>();

        try {
            // B. Executa HoughCircles
            // Note que usamos 'pipeToUse' (local), não 'this.pipelinePrototype'
            Imgproc.HoughCircles(processedImage, circlesMat, Imgproc.HOUGH_GRADIENT_ALT,
                pipeToUse.getDp(), 
                pipeToUse.getMinDist(),
                pipeToUse.getParam1(), 
                pipeToUse.getParam2(),
                pipeToUse.getMinRadius(), 
                pipeToUse.getMaxRadius());

            for (int i = 0; i < circlesMat.cols(); i++) {
                double[] c = circlesMat.get(0, i);
                detectedList.add(new Circle(c[0], c[1], c[2]));
            }
        } catch (Exception e) {
            // Log de erro silencioso ou console error se necessário
        } finally {
            // C. Liberação de memória (Crítico em Multithread)
            if (processedImage != null) processedImage.release();
            if (circlesMat != null) circlesMat.release();
        }
        
        return detectedList;
    }

    // --- CÁLCULO DE RECOMPENSA (Mantido igual à versão anterior) ---

    public double calculateReward(List<Circle> detected) {
        int detectedCount = detected.size();
        int truthCount = groundTruth.size();
        
        // 1. Sanity Check / Early Exit
        int dynamicLimit = Math.max(
            rewardConfig.getSanityLimitAbsolute(), 
            truthCount * rewardConfig.getSanityLimitMultiplier()
        );

        if (detectedCount > dynamicLimit) {
            return rewardConfig.getSanityFailPenalty() - (detectedCount * rewardConfig.getSanityExcessWeight());
        }

        double reward = 0;
        int matchedCount = 0;
        Set<Circle> matchedGroundTruth = new HashSet<>();

        for (Circle det : detected) {
            boolean foundMatch = false;
            for (Circle truth : groundTruth) {
                if (det.matches(truth, rewardConfig.getPositionTolerance())) {
                    if (!matchedGroundTruth.contains(truth)) {
                        reward += rewardConfig.getMatchBonus();
                        matchedGroundTruth.add(truth);
                        matchedCount++;
                        foundMatch = true;
                        break;
                    } else {
                        reward += rewardConfig.getDuplicatePenalty();
                    }
                }
            }
            if (!foundMatch) {
                reward += rewardConfig.getNoisePenalty();
            }
        }

        int missed = truthCount - matchedCount;
        reward -= (missed * rewardConfig.getMissPenalty());

        int excess = Math.max(0, detectedCount - truthCount);
        if (excess > 0) {
            double exponentialPenalty = Math.pow(excess, rewardConfig.getExcessPenaltyExponent()) 
                                      * rewardConfig.getExcessPenaltyWeight();
            reward -= exponentialPenalty;
        }

        return reward;
    }
    
    // Getters para visualização
    public Mat getOriginalImage() { return originalImage; }
    public ProcessingPipeline getPipelinePrototype() { return pipelinePrototype; }
}