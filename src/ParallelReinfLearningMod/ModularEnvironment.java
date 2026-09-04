package ParallelReinfLearningMod;
import filters.implementations.HoughCirclesJavaAlt;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import filters.OptParam;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static filters.implementations.HoughCirclesJavaAlt.houghCirclesAlt;

public class ModularEnvironment {
    private String imagePath;
    private List<Circle> groundTruth;
    private ProcessingPipeline pipelinePrototype; // prototype used for clone
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
        //MANUFACTURE AN EXCLUSIVE PIPELINE FOR THIS INSTANCE!
        this.localPipeline = pipeFactory.get();

        this.rewardConfig = (rConfig != null) ? rConfig : new RewardConfig();
        this.originalImage = Imgcodecs.imread(path);
        if (this.originalImage.empty()) {
            System.err.println("Erro crítico: Imagem não encontrada em " + path);
            System.exit(1);
        }
    }

    /**
     * NEW CONSTRUCTOR (For Multithreading):
     * Receives the image already loaded into memory (Mat) to avoid reading from the disk for each thread.
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



    // ---DETECTION METHOD ---


    /**
     * Multithreaded Version (Safe):
     * Since the Factory has already instantiated a unique 'localPipeline' for this Thread,
     * we don't need to clone. Just synchronize the local parameters and run.
     * @param paramsForThisRun The list of parameters (decision variables) for this specific test.
     */
    public List<Circle> runDetection(List<OptParam> paramsForThisRun) {

      //  1. Synchronize the new parameters directly in this Thread's dedicated pipeline.
        this.localPipeline.syncParameters(paramsForThisRun);

        //2. Executes the OpenCV algorithm.
        return runDetectionInternal(this.localPipeline);
    }

    /**
     * Private internal method that executes common logic (Hough) given an already configured pipeline.
     */
    private List<Circle> runDetectionInternal(ProcessingPipeline pipeToUse) {
        // A. Execute filterss (Blur, Canny, etc.)
        Mat processedImage = pipeToUse.executePipeline(originalImage);

        List<Circle> detectedList = new ArrayList<>();
        Mat circlesMat = new Mat();
        try {
            if(!pipeToUse.isUsePureJavaHough()) {


                // B. Runs HoughCircles
                // Note that  'pipeToUse' is used (local), not  'this.pipelinePrototype'
                // native implementation


                Imgproc.HoughCircles(processedImage, circlesMat, pipeToUse.getHoughMethod(),
                        pipeToUse.getDp(),
                        pipeToUse.getMinDist(),
                        pipeToUse.getParam1(),
                        pipeToUse.getParam2(),
                        pipeToUse.getMinRadius(),
                        pipeToUse.getMaxRadius());

            /*System.out.println("Inside Modular env. Parameters: " +  pipeToUse.getDp() + " "  + pipeToUse.getMinDist() + " "  +
                            pipeToUse.getParam1()  + " "  +   pipeToUse.getParam2() + " "  +  pipeToUse.getMinRadius() + " "  +
                    pipeToUse.getMaxRadius()) ;*/
                for (int i = 0; i < circlesMat.cols(); i++) {
                    double[] c = circlesMat.get(0, i);
                    detectedList.add(new Circle(c[0], c[1], c[2]));
                }
            }
            else{
           //java implementation
            List<HoughCirclesJavaAlt.CircleData> circles;
            circles =  houghCirclesAlt(
                    processedImage, (float)  pipeToUse.getDp(), (float) pipeToUse.getMinDist(),
                    pipeToUse.getMinRadius(), pipeToUse.getMaxRadius(), (int) pipeToUse.getParam1(), (int)  pipeToUse.getParam2(),null);

            for (int i = 0; i < circles.size(); i++) {

                detectedList.add(new Circle(circles.get(i).cx, circles.get(i).cy, circles.get(i).radius));
            }
            }
        } catch (Exception e) {
            // implement Log
        } finally {
            // C. free memory  (Critical on multithread)
            if (processedImage != null) processedImage.release();
            if (circlesMat != null) circlesMat.release();
        }
        
        return detectedList;
    }

    // --- Reward Calculation
    // uses boolean metrics to say if a circle is good or not
    //uses quantitative metrics to say that a detection/circle is good

    public double calculateReward(List<Circle> detected) {
        int detectedCount = detected.size();
        int truthCount = groundTruth.size();

        // 1. Sanity Check
        int dynamicLimit = Math.max(rewardConfig.getSanityLimitAbsolute(), truthCount * rewardConfig.getSanityLimitMultiplier());
        if (detectedCount > dynamicLimit) {
            return rewardConfig.getSanityFailPenalty() - (detectedCount * rewardConfig.getSanityExcessWeight());
        }

        double reward = 0;
        double sumIoU = 0.0; //  GLOBAL QUANTITATIVE INDICATOR
        Set<Circle> matchedGroundTruth = new HashSet<>();

          // new version with IoT and center as reward

        // 2. Contiunuous mapping (Hibrid)
        for (Circle det : detected) {
            Circle bestMatch = null;
            double bestHybridScore = 0.0;
            double bestIouForLog = 0.0; // we save the real IoU for  mIoU statistics

            for (Circle truth : groundTruth) {
                // 1. Calculates o IoU (0.0 a 1.0)
                double iou = det.getIoU(truth);

                // 2. Calculates the mean Euclidian distance
                double dist = Math.sqrt(Math.pow(det.x - truth.x, 2) + Math.pow(det.y - truth.y, 2));

                // 3. Converts the distance into a grade (0.0 a 1.0).
                // 1.0 = exact center | 0.0 = far away
                double distScore = 0.0;
                if (dist < rewardConfig.getMaxCenterDistance()) {
                    distScore = 1.0 - (dist / rewardConfig.getMaxCenterDistance());
                }

                // 4.  Hybrid weighted grade
                double hybridScore = (iou * rewardConfig.getWeightIoU()) +
                        (distScore * rewardConfig.getWeightCenter());

                if (hybridScore > bestHybridScore) {
                    bestHybridScore = hybridScore;
                    bestMatch = truth;
                    bestIouForLog = iou;
                }
            }

            // If there was Hybrid scoring (either it touched, or it is within the radius of attraction)
            if (bestHybridScore > 0.0) {
                if (!matchedGroundTruth.contains(bestMatch)) {

                    // The AI reward is now guided by the hybrid note!
                    reward += (rewardConfig.getMatchBonus() * bestHybridScore);

                  //   But the official screen metric remains only geometry (IoU).
                    sumIoU += bestIouForLog;

                    matchedGroundTruth.add(bestMatch);
                } else {
                    reward += rewardConfig.getDuplicatePenalty();
                }
            } else {
                // If it's too far away and doesn't touch:  Absolute noise
                reward += rewardConfig.getNoisePenalty();
            }
        }


        // 3. Punishment for Omission (Penalizes those who weren't even touched)
        int missed = truthCount - matchedGroundTruth.size();
        reward -= (missed * rewardConfig.getMissPenalty());

        // 4.Exponential Punishment for Excessive Noise
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

        // Calculates and returns the average IoU (mIoU) of the image relative to the template.
        return sumIoU / groundTruth.size();
    }

   // Now, update the isGoalReached method to make it much cleaner:
    public boolean isGoalReached(List<Circle> detected) {
        // Requires the quantity to be correct.
        if (detected.size() != groundTruth.size()) {
            return false;
        }
        // Use the new method to verify if the configured excellence has been achieved.
        return calculateMeanIoU(detected) >= rewardConfig.getTargetMeanIoU();
    }

    /**
     * Calculates the Average Distance and Average Hybrid Score for Logging and Analysis purposes.
     * Returns an array where: [0] = Average Euclidean Distance, [1] = Average Hybrid Score
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

               // We maintain the distance linked to the best hybrid score.
                if (hybridScore > bestHybridScore) {
                    bestHybridScore = hybridScore;
                    distAtBestHybrid = dist;
                }
            }

            // We only count if it entered the radar (score > 0)
            if (bestHybridScore > 0.0) {
                sumHybrid += bestHybridScore;
                sumDist += distAtBestHybrid;
                validMatches++;
            }
        }

        if (validMatches == 0) return new double[]{0.0, 0.0};

        // Return the average
        return new double[]{ (sumDist / validMatches), (sumHybrid / validMatches) };
    }

    // Getters for viewing
    public Mat getOriginalImage() { return originalImage; }
   // public ProcessingPipeline getPipelinePrototype() { return pipelinePrototype; }

	public RewardConfig getRewardConfig() {
		return rewardConfig;
	}

    public java.util.List<Circle> getGroundTruth() {
        return this.groundTruth;
    }

    public ProcessingPipeline getPipelinePrototype() { return this.localPipeline; } // Returns the local
    public Supplier<ProcessingPipeline> getPipelineFactory() { return this.pipelineFactory; } // /sends the factory
    /**
     * Shortcut to run detection with a set of parameters and return the score.
     * Used by the upper layer of permutation optimization.
     */
    public double evaluate(List<OptParam> params) {
        // 1. Run the detection with the provided parameters
        List<Circle> detected = this.runDetection(params);

        // 2. Calculates and returns the final score
        return this.calculateReward(detected);
    }

    /**
     * Clears the base image from the native OpenCV (C++) memory.
     * Essential to prevent memory leaks when running dozens of permutations.
     */
    public void releaseResources() {
        if (this.originalImage != null && !this.originalImage.empty()) {
            this.originalImage.release();
        }
    }



}