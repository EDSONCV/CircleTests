// SimulationClass.java
package ParallelReinfLearningMod;

import java.util.concurrent.Callable;
import org.opencv.core.Mat;

import filters.OptParam;


import java.util.List;

/**
 * The object that returns from the Thread with the simulation data.[cite: 2]
 */
class SimulationResult {
    public double reward;
    public List<Circle> detectedCircles;
    public List<OptParam> usedParams; // Parameters that generated this result[cite: 2]
    public long executionTimeNano;    // Time taken[cite: 2]
    public String workerName;         // Who executed it[cite: 2]

    public SimulationResult(double reward, List<Circle> circles, List<OptParam> params, long time, String worker) {
        this.reward = reward;
        this.detectedCircles = circles;
        this.usedParams = params;
        this.executionTimeNano = time;
        this.workerName = worker;
    }
}

/**
 * The task that will be executed in parallel.[cite: 2]
 * Implements Callable to be able to return a value.[cite: 2]
 */
class SimulationTask implements Callable<SimulationResult> {
    private ModularEnvironment env; // The environment (thread-safe or cloned)[cite: 2]
    private List<OptParam> paramsToTest;
    private String workerId;

    public SimulationTask(ModularEnvironment baseEnv, List<OptParam> params, String workerId) {
        // IMPORTANT: The environment must be able to handle concurrency or be cloned.[cite: 2]
        // Here we will assume that env creates new Mats on each execution, which is safe.[cite: 2]
        this.env = baseEnv;
        this.paramsToTest = params;
        this.workerId = workerId;
    }

    @Override
    public SimulationResult call() throws Exception {
        long start = System.nanoTime();

        // 1. Configures the pipeline with the parameters for this task[cite: 2]
        // Note: We need to ensure that this does not affect other threads.[cite: 2]
        // The best way is for the Environment to receive the params in the runDetection method[cite: 2]
        // instead of reading from the internal state of the shared pipeline.[cite: 2]

        // Executes detection passing the parameters explicitly[cite: 2]
        List<Circle> detected = env.runDetection(paramsToTest);

        // Calculates reward[cite: 2]
        double reward = env.calculateReward(detected);

        long duration = System.nanoTime() - start;

        return new SimulationResult(reward, detected, paramsToTest, duration, workerId);
    }
}