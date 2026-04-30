package ParallelReinfLearningMod;

/**
 * Classe de Configuração para os parâmetros de Recompensa e Punição.
 * Elimina números mágicos e centraliza a política de aprendizado.
 */
public class RewardConfig {

    // Pesos: A soma dos dois idealmente deve ser 1.0 (ex: 0.7 e 0.3)
    private double weightIoU = 0.7;     // 70% da nota vem da sobreposição
    private double weightCenter = 0.3;  // 30% da nota vem de acertar o centro exato

    // Raio de "Atração Gravitacional" (em pixels).
    // Círculos além dessa distância recebem nota 0.0 no quesito centro.
    private double maxCenterDistance = 100.0;

    // --- Parâmetros de Sanidade (Early Exit) ---
    private int sanityLimitAbsolute = 50;      // Limite máximo absoluto de círculos
    private int sanityLimitMultiplier = 4;     // Limite relativo (x vezes o número real)
    private double sanityFailPenalty = -100.0; // Punição base por falha de sanidade
    private double sanityExcessWeight = 0.5;   // Punição por círculo extra na falha de sanidade

    // --- Parâmetros de Match (Acertos) ---
    private double matchBonus = 20.0;          // Recompensa por encontrar um círculo correto
    private double duplicatePenalty = -5.0;    // Punição por encontrar o MESMO círculo 2x
    private double noisePenalty = -2.0;        // Punição por círculo que não bate com nada (Ruído)
    
    // --- Parâmetros de Erro Global ---
    private double missPenalty = 15.0;         // Punição por cada círculo real NÃO encontrado (Omissão)
    
    // --- Parâmetros de Punição Exponencial (Excesso) ---
    private double excessPenaltyExponent = 1.8; // Potência da punição (ex: excesso ^ 1.8)
    private double excessPenaltyWeight = 1.5;   // Peso multiplicador da punição exponencial


    // Tolerância em pixels para considerar o raio perfeito
    private double stopToleranceRadius = 1.0;

    // 0.50 (50%) é o padrão clássico em artigos acadêmicos (ex: PASCAL VOC).
    // Para medições industriais estritas, usa-se 0.75 ou 0.80.
    private double iouThreshold = 0.50;

    // Quantas rodadas seguidas sem melhoria o algoritmo deve tolerar antes de desistir
    private int patienceLimit = 20;

    // Nota média global (mIoU) necessária para parar o algoritmo por "Sucesso" (Padrão: 95%)
    private double targetMeanIoU = 0.95;

    // --- Construtor Padrão (com valores default) ---
    public RewardConfig() {}

    // --- Getters e Setters ---
    
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
