package ParallelReinfLearningMod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import filters.OptParam;

class DynamicRLAgent {
    // Mapa Q: Estado(String) -> Mapa<IndiceParametro + Direcao, ValorQ>
    // Ação simplificada: String "Index_DIRECTION" (ex: "0_UP", "3_DOWN")
    private Map<String, Map<String, Double>> qTable = new HashMap<>();
    private Random random = new Random();
    private double epsilon = 0.5;
    private double learningRate = 0.1;
    private double discountFactor = 0.9;
    
    // A lista de parâmetros que o agente controla
    private List<OptParam> managedParams;

    public DynamicRLAgent(List<OptParam> params) {
        this.managedParams = params;
    }

    // Escolhe uma ação: Qual parâmetro mexer e em qual direção
    public String chooseAction(String state) {
        if (random.nextDouble() < epsilon) {
            return randomAction();
        }
        
        Map<String, Double> actions = qTable.computeIfAbsent(state, k -> new HashMap<>());
        if (actions.isEmpty()) return randomAction();

        // Retorna a melhor ação (Greedy)
        return actions.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(randomAction());
    }

 // Certifique-se que o método randomAction é público ou acessível internamente
    private String randomAction() {
        if (managedParams.isEmpty()) return "0_UP"; // Fallback
        
        int paramIndex = random.nextInt(managedParams.size());
        String direction = random.nextBoolean() ? "UP" : "DOWN";
        
        return paramIndex + "_" + direction;
    }
    // Executa a ação escolhida na lista de parâmetros
    public void executeAction(String actionKey) {
        String[] parts = actionKey.split("_");
        int idx = Integer.parseInt(parts[0]);
        String dir = parts[1];

        OptParam target = managedParams.get(idx);
        if (dir.equals("UP")) target.increase();
        else target.decrease();
    }
    
    /**
     * Escolhe uma ação para uma simulação paralela.
     * @param batchIndex O índice do worker (0, 1, 2...).
     * Pode ser usado para criar padrões (ex: worker 0 sempre testa UP, worker 1 testa DOWN).
     * @return Uma string de ação (ex: "3_UP").
     */
    public String chooseActionForSim(int batchIndex) {
        // Estratégia: Exploração Pura (Random Mutation)
        // O objetivo no passo paralelo é gerar diversidade para descobrir novos gradientes.
        return randomAction();
    }
    // Atualiza Q-Table
    public void learn(String state, String action, double reward, String nextState) {
        Map<String, Double> qValues = qTable.computeIfAbsent(state, k -> new HashMap<>());
        Map<String, Double> nextQValues = qTable.computeIfAbsent(nextState, k -> new HashMap<>());

        double currentQ = qValues.getOrDefault(action, 0.0);
        double maxNextQ = nextQValues.values().stream().max(Double::compareTo).orElse(0.0);

        double newQ = currentQ + learningRate * (reward + discountFactor * maxNextQ - currentQ);
        qValues.put(action, newQ);
    }
    
    public void decayEpsilon() { if (epsilon > 0.05) epsilon *= 0.99; }
}
