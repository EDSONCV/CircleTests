package ParallelReinfLearningMod;

/**
 * Classe de Configuração para os parâmetros de Recompensa e Punição.
 * Elimina números mágicos e centraliza a política de aprendizado.
 */
public class RewardConfig {
    
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

    // Tolerância Geométrica
    private double positionTolerance = 15.0;    // Distância máxima em pixels para considerar "Match"

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

    public double getPositionTolerance() { return positionTolerance; }
    public void setPositionTolerance(double positionTolerance) { this.positionTolerance = positionTolerance; }
}
