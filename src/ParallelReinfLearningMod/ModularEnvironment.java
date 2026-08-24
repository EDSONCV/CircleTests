package ParallelReinfLearningMod;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import filters.OptParam;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public class ModularEnvironment {
    private String imagePath;
    private List<Circle> groundTruth;
    private ProcessingPipeline pipelinePrototype; // Protótipo (usado apenas para clonagem)
    private Mat originalImage;
    private RewardConfig rewardConfig;
    private Supplier<ProcessingPipeline> pipelineFactory;
    private ProcessingPipeline localPipeline;



    /**
     * Construtor
     */
    public ModularEnvironment(String path, List<Circle> gt, Supplier<ProcessingPipeline> pipeFactory, RewardConfig rConfig) {
        this.imagePath = path;
        this.groundTruth = gt;

        this.pipelineFactory = pipeFactory;
        // FABRICA UM PIPELINE EXCLUSIVO PARA ESTA INSTÂNCIA!
        this.localPipeline = pipeFactory.get();

        this.rewardConfig = (rConfig != null) ? rConfig : new RewardConfig();
        this.originalImage = Imgcodecs.imread(path);
        if (this.originalImage.empty()) {
            System.err.println("Erro crítico: Imagem não encontrada em " + path);
            System.exit(1);
        }
    }

    /**
     * NOVO CONSTRUTOR (Para Multithread):
     * Recebe a imagem já carregada na memória (Mat) para evitar leitura do disco a cada thread.
     */
    public ModularEnvironment(Mat loadedImage, List<Circle> gt, Supplier<ProcessingPipeline> pipeFactory, RewardConfig rConfig) {
        this.imagePath = "in-memory";
        this.groundTruth = gt;
        this.pipelineFactory = pipeFactory;

        // FABRICA UM PIPELINE EXCLUSIVO PARA A THREAD
        this.localPipeline = pipeFactory.get();

        this.rewardConfig = (rConfig != null) ? rConfig : new RewardConfig();
        this.originalImage = loadedImage;
    }



    // --- MÉTODOS DE DETECÇÃO ---


    /**
     * Versão Multithread (Safe):
     * Como a Factory já instanciou um 'localPipeline' exclusivo para esta Thread,
     * não precisamos de clonar. Basta sincronizar os parâmetros locais e executar.
     * @param paramsForThisRun A lista de parâmetros (variáveis de decisão) para este teste específico.
     */
    public List<Circle> runDetection(List<OptParam> paramsForThisRun) {

        // 1. Sincroniza os novos parâmetros diretamente no pipeline exclusivo desta Thread
        this.localPipeline.syncParameters(paramsForThisRun);

        // 2. Executa o algoritmo OpenCV
        return runDetectionInternal(this.localPipeline);
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

          // new version with IoT and center as reward

        // 2. Mapeamento Contínuo (Híbrido)
        for (Circle det : detected) {
            Circle bestMatch = null;
            double bestHybridScore = 0.0;
            double bestIouForLog = 0.0; // Guardamos o IoU real apenas para a estatística mIoU

            for (Circle truth : groundTruth) {
                // 1. Calcula o IoU (0.0 a 1.0)
                double iou = det.getIoU(truth);

                // 2. Calcula a Distância Euclidiana linear
                double dist = Math.sqrt(Math.pow(det.x - truth.x, 2) + Math.pow(det.y - truth.y, 2));

                // 3. Converte a Distância numa nota (0.0 a 1.0).
                // 1.0 = Centro exato | 0.0 = Muito longe
                double distScore = 0.0;
                if (dist < rewardConfig.getMaxCenterDistance()) {
                    distScore = 1.0 - (dist / rewardConfig.getMaxCenterDistance());
                }

                // 4. A MÁGICA: Nota Híbrida Ponderada
                double hybridScore = (iou * rewardConfig.getWeightIoU()) +
                        (distScore * rewardConfig.getWeightCenter());

                if (hybridScore > bestHybridScore) {
                    bestHybridScore = hybridScore;
                    bestMatch = truth;
                    bestIouForLog = iou;
                }
            }

            // Se houve pontuação Híbrida (ou tocou, ou está no raio de atração)
            if (bestHybridScore > 0.0) {
                if (!matchedGroundTruth.contains(bestMatch)) {

                    // A recompensa da IA agora é guiada pela nota híbrida!
                    reward += (rewardConfig.getMatchBonus() * bestHybridScore);

                    // Mas a métrica oficial de tela continua sendo apenas a geometria (IoU)
                    sumIoU += bestIouForLog;

                    matchedGroundTruth.add(bestMatch);
                } else {
                    reward += rewardConfig.getDuplicatePenalty();
                }
            } else {
                // Se está longe demais e não toca: Ruído absoluto
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

    /**
     * Calcula a Distância Média e a Nota Híbrida Média para fins de Log e Análise.
     * Retorna um array onde: [0] = Distância Euclidiana Média, [1] = Nota Híbrida Média
     */
    public double[] calculateHybridMetricsForLog(List<Circle> detected) {
        if (groundTruth.isEmpty() || detected.isEmpty()) {
            return new double[]{0.0, 0.0};
        }

        double sumDist = 0.0;
        double sumHybrid = 0.0;
        int validMatches = 0;

        for (Circle det : detected) {
            double bestHybridScore = 0.0;
            double distAtBestHybrid = 0.0;

            for (Circle truth : groundTruth) {
                double iou = det.getIoU(truth);
                double dist = Math.sqrt(Math.pow(det.x - truth.x, 2) + Math.pow(det.y - truth.y, 2));

                double distScore = 0.0;
                if (dist < rewardConfig.getMaxCenterDistance()) {
                    distScore = 1.0 - (dist / rewardConfig.getMaxCenterDistance());
                }

                double hybridScore = (iou * rewardConfig.getWeightIoU()) + (distScore * rewardConfig.getWeightCenter());

                // Guardamos a distância atrelada à melhor nota híbrida
                if (hybridScore > bestHybridScore) {
                    bestHybridScore = hybridScore;
                    distAtBestHybrid = dist;
                }
            }

            // Só contabilizamos se entrou no radar (nota > 0)
            if (bestHybridScore > 0.0) {
                sumHybrid += bestHybridScore;
                sumDist += distAtBestHybrid;
                validMatches++;
            }
        }

        if (validMatches == 0) return new double[]{0.0, 0.0};

        // Retorna as médias
        return new double[]{ (sumDist / validMatches), (sumHybrid / validMatches) };
    }

    // Getters para visualização
    public Mat getOriginalImage() { return originalImage; }
   // public ProcessingPipeline getPipelinePrototype() { return pipelinePrototype; }

	public RewardConfig getRewardConfig() {
		return rewardConfig;
	}

    public java.util.List<Circle> getGroundTruth() {
        return this.groundTruth;   // Substitua pelo nome exato da sua lista de gabaritos
    }

    public ProcessingPipeline getPipelinePrototype() { return this.localPipeline; } // Retorna o local
    public Supplier<ProcessingPipeline> getPipelineFactory() { return this.pipelineFactory; } // Repassa a fábrica
    /**
     * Atalho para rodar a detecção com um conjunto de parâmetros e já retornar a nota (score).
     * Usado pela camada superior de otimização de permutações.
     */
    public double evaluate(List<OptParam> params) {
        // 1. Roda a detecção com os parâmetros fornecidos
        List<Circle> detected = this.runDetection(params);

        // 2. Calcula e retorna a pontuação final
        return this.calculateReward(detected);
    }

    /**
     * Limpa a imagem base da memória nativa do OpenCV (C++).
     * Essencial para evitar vazamento de memória ao rodar dezenas de permutações.
     */
    public void releaseResources() {
        if (this.originalImage != null && !this.originalImage.empty()) {
            this.originalImage.release();
        }
    }



}