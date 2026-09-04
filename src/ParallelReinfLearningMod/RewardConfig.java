// RewardConfig.java
package ParallelReinfLearningMod;

/**
 * Configuration Class for Reward and Punishment parameters.[cite: 1]
 * Eliminates magic numbers and centralizes the learning policy.[cite: 1]
 */
public class RewardConfig {

    // Weights: The sum of the two should ideally be 1.0 (e.g., 0.7 and 0.3)[cite: 1]
    private double weightIoU = 0.7;     // 70% of the score comes from overlap[cite: 1]
    private double weightCenter = 0.3;  // 30% of the score comes from hitting the exact center[cite: 1]

    // "Gravitational Attraction" radius (in pixels).[cite: 1]
    // Circles beyond this distance receive a 0.0 score for the center aspect.[cite: 1]
    private double maxCenterDistance = 100.0;

    // --- Sanity Parameters (Early Exit) ---[cite: 1]
    private int sanityLimitAbsolute = 50;      // Absolute maximum limit of circles[cite: 1]
    private int sanityLimitMultiplier = 4;     // Relative limit (x times the real number)[cite: 1]
    private double sanityFailPenalty = -100.0; // Base penalty for sanity failure[cite: 1]
    private double sanityExcessWeight = 0.5;   // Penalty per extra circle on sanity failure[cite: 1]

    // --- Match Parameters (Hits) ---[cite: 1]
    private double matchBonus = 20.0;          // Reward for finding a correct circle[cite: 1]
    private double duplicatePenalty = -5.0;    // Penalty for finding the SAME circle 2x[cite: 1]
    private double noisePenalty = -2.0;        // Penalty for a circle that doesn't match anything (Noise)[cite: 1]

    // --- Global Error Parameters ---[cite: 1]
    private double missPenalty = 15.0;         // Penalty for each real circle NOT found (Omission)[cite: 1]

    // --- Exponential Penalty Parameters (Excess) ---[cite: 1]
    private double excessPenaltyExponent = 1.8; // Penalty power (e.g., excess ^ 1.8)[cite: 1]
    private double excessPenaltyWeight = 1.5;   // Multiplier weight for the exponential penalty[cite: 1]


    // Tolerance in pixels to consider the radius perfect[cite: 1]
    private double stopToleranceRadius = 1.0;

    // 0.50 (50%) is the classic standard in academic papers (e.g., PASCAL VOC).[cite: 1]
    // For strict industrial measurements, 0.75 or 0.80 is used.[cite: 1]
    private double iouThreshold = 0.50;

    // How many consecutive rounds without improvement the algorithm should tolerate before giving up[cite: 1]
    private int patienceLimit = 20;

    // Global average score (mIoU) required to stop the algorithm due to "Success" (Default: 95%)[cite: 1]
    private double targetMeanIoU = 0.95;

    // --- Default Constructor (with default values) ---[cite: 1]
    public RewardConfig() {}

    // --- Getters and Setters ---[cite: 1]

    public int getSanityLimitAbsolute() { return sanityLimitAbsolute; }
    public void setSanityLimitAbsolute(int sanityLimitAbsolute) { this.sanityLimitAbsolute = sanityLimitAbsolute; }

    public int getSanityLimitMultiplier() { return sanityLimitMultiplier; }
    public void setSanityLimitMultiplier(int sanityLimitMultiplier) { this.sanityLimitMultiplier = sanityLimitMultiplier; }

    public double getSanityFailPenalty() { return sanityFailPenalty; }
    public void setSanityFailPenalty(double sanityFailPenalty) { this.sanityFailPenalty = sanityFailPenalty; }

    public double getSanityExcessWeight() { return sanityExcessWeight; }
    public void setSanityExcessWeight(double sanityExcessWeight) { this.sanityExcessWeight = sanityExcessWeight; }

    public double getMatchBonus() { return matchBonus; }
    public void setMatchBonus(double matchBonus) { this.matchBonus = matchBonus; }

    public double getDuplicatePenalty() { return duplicatePenalty; }
    public void setDuplicatePenalty(double duplicatePenalty) { this.duplicatePenalty = duplicatePenalty; }

    public double getNoisePenalty() { return noisePenalty; }
    public void setNoisePenalty(double noisePenalty) { this.noisePenalty = noisePenalty; }

    public double getMissPenalty() { return missPenalty; }
    public void setMissPenalty(double missPenalty) { this.missPenalty = missPenalty; }

    public double getExcessPenaltyExponent() { return excessPenaltyExponent; }
    public void setExcessPenaltyExponent(double excessPenaltyExponent) { this.excessPenaltyExponent = excessPenaltyExponent; }

    public double getExcessPenaltyWeight() { return excessPenaltyWeight; }
    public void setExcessPenaltyWeight(double excessPenaltyWeight) { this.excessPenaltyWeight = excessPenaltyWeight; }


    public double getIouThreshold() { return iouThreshold; }
    public void setIouThreshold(double iouThreshold) { this.iouThreshold = iouThreshold; }

    public double getTargetMeanIoU() {
        return targetMeanIoU;
    }

    public void setTargetMeanIoU(double targetMeanIoU) {
        this.targetMeanIoU = targetMeanIoU;
    }

    public double getStopToleranceRadius() { return stopToleranceRadius; }
    public void setStopToleranceRadius(double stopToleranceRadius) { this.stopToleranceRadius = stopToleranceRadius; }

    public int getPatienceLimit() { return patienceLimit; }
    public void setPatienceLimit(int patienceLimit) { this.patienceLimit = patienceLimit; }
    public double getWeightIoU() { return weightIoU; }
    public void setWeightIoU(double weightIoU) { this.weightIoU = weightIoU; }

    public double getWeightCenter() { return weightCenter; }
    public void setWeightCenter(double weightCenter) { this.weightCenter = weightCenter; }

    public double getMaxCenterDistance() { return maxCenterDistance; }
    public void setMaxCenterDistance(double maxCenterDistance) { this.maxCenterDistance = maxCenterDistance; }
}