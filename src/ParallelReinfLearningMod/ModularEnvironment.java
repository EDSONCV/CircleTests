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
    // uses boolean metrics to say if a circle is good or not

    public double calculateRewardOld(List<Circle> detected) {
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


/*
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
*/
        for (Circle det : detected) {
            Circle bestMatch = null;
            double bestIou = 0.0;

            // Procura no Ground Truth o círculo que tem a MAIOR sobreposição com o detectado
            for (Circle truth : groundTruth) {
                double currentIou = det.getIoU(truth);
                if (currentIou > bestIou) {
                    bestIou = currentIou;
                    bestMatch = truth;
                }
            }

            // Se o melhor IoU superar o nosso rigor (ex: > 0.50)
            if (bestIou >= rewardConfig.getIouThreshold()) {
                if (!matchedGroundTruth.contains(bestMatch)) {
                    // Recompensa Proporcional: Se acertar cravado (IoU=1.0), ganha o bônus máximo!
                    reward += (rewardConfig.getMatchBonus() * bestIou);
                    matchedGroundTruth.add(bestMatch);
                    matchedCount++;
                } else {
                    reward += rewardConfig.getDuplicatePenalty();
                }
            } else {
                // Se o melhor IoU for menor que 0.50, é ruído (falso positivo)
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

    //uses quantitative metrics to say that a detection/circle is good

    public double calculateReward(List<Circle> detected) {
        int detectedCount = detected.size();
        int truthCount = groundTruth.size();

        // 1. Sanity Check (mantido igual)
        int dynamicLimit = Math.max(rewardConfig.getSanityLimitAbsolute(), truthCount * rewardConfig.getSanityLimitMultiplier());
        if (detectedCount > dynamicLimit) {
            return rewardConfig.getSanityFailPenalty() - (detectedCount * rewardConfig.getSanityExcessWeight());
        }

        double reward = 0;
        double sumIoU = 0.0; // O SEU INDICADOR QUANTITATIVO GLOBAL
        Set<Circle> matchedGroundTruth = new HashSet<>();

        // 2. Mapeamento Contínuo
        for (Circle det : detected) {
            Circle bestMatch = null;
            double bestIou = 0.0;

            for (Circle truth : groundTruth) {
                double currentIou = det.getIoU(truth); // Já retorna entre 0.0 e 1.0
                if (currentIou > bestIou) {
                    bestIou = currentIou;
                    bestMatch = truth;
                }
            }

            // Se houve qualquer toque (IoU > 0)
            if (bestIou > 0.0) {
                if (!matchedGroundTruth.contains(bestMatch)) {
                    // AQUI ESTÁ A MÁGICA DA RECOMPENSA DENSA:
                    // Se o MatchBonus é 50 e o IoU é 0.2, ele ganha 10 pontos.
                    // Na próxima rodada, se o IoU subir para 0.8, ele ganha 40 pontos.
                    // O gradiente empurra a IA para o IoU máximo (1.0).
                    reward += (rewardConfig.getMatchBonus() * bestIou);
                    sumIoU += bestIou;
                    matchedGroundTruth.add(bestMatch);
                } else {
                    // Penalidade para não deixar que 5 círculos detectados no mesmo lugar somem pontos
                    reward += rewardConfig.getDuplicatePenalty();
                }
            } else {
                // Círculo detectado no meio do nada (Ruído)
                reward += rewardConfig.getNoisePenalty();
            }
        }

        // 3. Punição por Omissão (Penaliza os que nem foram tocados)
        int missed = truthCount - matchedGroundTruth.size();
        reward -= (missed * rewardConfig.getMissPenalty());

        // 4. Punição Exponencial por Excesso de ruído
        int excess = Math.max(0, detectedCount - truthCount);
        if (excess > 0) {
            reward -= (Math.pow(excess, rewardConfig.getExcessPenaltyExponent()) * rewardConfig.getExcessPenaltyWeight());
        }

        return reward;
    }

    public double calculateMeanIoU(List<Circle> detected) {
        if (groundTruth.isEmpty()) return 0.0;

        double sumIoU = 0.0;
        Set<Circle> matchedGroundTruth = new HashSet<>();

        for (Circle det : detected) {
            double bestIou = 0.0;
            Circle bestMatch = null;

            for (Circle truth : groundTruth) {
                double currentIou = det.getIoU(truth);
                if (currentIou > bestIou) {
                    bestIou = currentIou;
                    bestMatch = truth;
                }
            }

            if (bestIou > 0 && !matchedGroundTruth.contains(bestMatch)) {
                sumIoU += bestIou;
                matchedGroundTruth.add(bestMatch);
            }
        }

        // Calcula e devolve a média do IoU (mIoU) da imagem face ao gabarito
        return sumIoU / groundTruth.size();
    }

    // Agora, atualize o método isGoalReached para ficar muito mais limpo:
    public boolean isGoalReached(List<Circle> detected) {
        // Exige que a quantidade bata certo
        if (detected.size() != groundTruth.size()) {
            return false;
        }
        // Usa o novo método para verificar se atingiu a excelência configurada
        return calculateMeanIoU(detected) >= rewardConfig.getTargetMeanIoU();
    }




    // to be used with calculateRewardOld

    public boolean isGoalReachedOld(List<Circle> detected) {
        // Se a quantidade de círculos detectados for diferente da real, já falhou
        if (detected.size() != groundTruth.size()) {
            return false;
        }

        int perfectMatches = 0;
        Set<Circle> matchedGroundTruth = new HashSet<>();

        for (Circle det : detected) {
            for (Circle truth : groundTruth) {
                // Usa o método Strict que acabamos de criar, pegando as tolerâncias do Config
                if (det.getIoU(truth) >= rewardConfig.getIouThreshold()) {
                    if (!matchedGroundTruth.contains(truth)) {
                        matchedGroundTruth.add(truth);
                        perfectMatches++;
                        break;
                    }
                }
                /* if (det.matchesStrict(truth, rewardConfig.getStopToleranceCenter(), rewardConfig.getStopToleranceRadius())) {
                    if (!matchedGroundTruth.contains(truth)) {
                        matchedGroundTruth.add(truth);
                        perfectMatches++;
                        break;
                    }
                }*/
            }
        }

        // O objetivo é alcançado se 100% dos círculos baterem estritamente
        return perfectMatches == groundTruth.size();
    }

    // Getters para visualização
    public Mat getOriginalImage() { return originalImage; }
    public ProcessingPipeline getPipelinePrototype() { return pipelinePrototype; }

	public RewardConfig getRewardConfig() {
		return rewardConfig;
	}
}