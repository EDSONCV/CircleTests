package ParallelReinfLearningMod;

import java.util.concurrent.Callable;
import org.opencv.core.Mat;

import filters.OptParam;


import java.util.List;

/**
 * O objeto que volta da Thread com os dados da simulação.
 */
class SimulationResult {
    public double reward;
    public List<Circle> detectedCircles;
    public List<OptParam> usedParams; // Parâmetros que geraram este resultado
    public long executionTimeNano;    // Tempo levado
    public String workerName;         // Quem executou

    public SimulationResult(double reward, List<Circle> circles, List<OptParam> params, long time, String worker) {
        this.reward = reward;
        this.detectedCircles = circles;
        this.usedParams = params;
        this.executionTimeNano = time;
        this.workerName = worker;
    }
}

/**
 * A tarefa que será executada em paralelo.
 * Implementa Callable para poder retornar valor.
 */
class SimulationTask implements Callable<SimulationResult> {
    private ModularEnvironment env; // O ambiente (thread-safe ou clonado)
    private List<OptParam> paramsToTest;
    private String workerId;

    public SimulationTask(ModularEnvironment baseEnv, List<OptParam> params, String workerId) {
        // IMPORTANTE: O ambiente deve ser capaz de lidar com concorrência ou ser clonado.
        // Aqui assumiremos que o env cria Mats novos a cada execução, o que é seguro.
        this.env = baseEnv; 
        this.paramsToTest = params;
        this.workerId = workerId;
    }

    @Override
    public SimulationResult call() throws Exception {
        long start = System.nanoTime();

        // 1. Configura o pipeline com os parâmetros desta tarefa
        // Nota: Precisamos garantir que isso não afete outras threads.
        // A melhor forma é o Environment receber os params no método runDetection
        // em vez de ler do estado interno do pipeline compartilhado.
        
        // Executa detecção passando os parametros explicitamente
        List<Circle> detected = env.runDetection(paramsToTest);
        
        // Calcula recompensa
        double reward = env.calculateReward(detected);

        long duration = System.nanoTime() - start;
        
        return new SimulationResult(reward, detected, paramsToTest, duration, workerId);
    }
}
