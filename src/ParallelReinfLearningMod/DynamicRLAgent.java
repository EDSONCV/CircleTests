package ParallelReinfLearningMod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import filters.OptParam;

class DynamicRLAgent {
    // Map Q: State(String) -> Map<ParameterIntex + Direction, Q Value>
    // Simplified action: String "Index_DIRECTION" (ex: "0_UP", "3_DOWN")
    private Map<String, Map<String, Double>> qTable = new HashMap<>();
    private Random random = new Random();
    private double epsilon = 0.5;
    private double learningRate = 0.1;
    private double discountFactor = 0.9;

    //The list of parameters that the agent controls.
    private List<OptParam> managedParams;

    public DynamicRLAgent(List<OptParam> params) {
        this.managedParams = params;
    }

    // Choose an action: Which parameter to change and in which direction
    public String chooseAction(String state) {
        if (random.nextDouble() < epsilon) {
            return randomAction();
        }
        
        Map<String, Double> actions = qTable.computeIfAbsent(state, k -> new HashMap<>());
        if (actions.isEmpty()) return randomAction();

// Returns the best action (Greedy)
        return actions.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(randomAction());
    }

    /**
     * Forces the agent to temporarily abandon current knowledge
     * and make radically random decisions to escape a local optimum.
     */
    public void triggerExplorationBurst() {
// Return the exploration rate to 90% (completely random actions)
        this.epsilon = 0.90;
        System.out.println("   [ RL agent ] Exploration reseted to 90%!");
    }

 // Make sure the randomAction method is public or internally accessible.
    private String randomAction() {
        if (managedParams.isEmpty()) return "0_UP"; // Fallback
        
        int paramIndex = random.nextInt(managedParams.size());
        String direction = random.nextBoolean() ? "UP" : "DOWN";
        
        return paramIndex + "_" + direction;
    }
    // Executes the choosen action on the parameters list
    public void executeAction(String actionKey) {
        String[] parts = actionKey.split("_");
        int idx = Integer.parseInt(parts[0]);
        String dir = parts[1];

        OptParam target = managedParams.get(idx);
        if (dir.equals("UP")) target.increase();
        else target.decrease();
    }

    /**
     * Choose an action for a parallel simulation.
     * @param batchIndex The index of the worker (0, 1, 2...).
     * Can be used to create patterns (e.g. worker 0 always tests UP, worker 1 tests DOWN).
     * @return An action string (e.g. "3_UP").
     */
    public String chooseActionForSim(int batchIndex) {
        // Strategy: Pure Exploration (Random Mutation)
       // The goal in the parallel step is to generate diversity to discover new gradients.
        return randomAction();
    }
    // Updates Q-Table
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
