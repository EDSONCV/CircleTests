package ParallelReinfLearningMod;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import filters.OptParam;

public class ParallelOptimizer {
    
    private int threadCount;
    private ExecutorService executor;
    private PerformanceMonitor monitor;
    private ModularEnvironment env;
    private DynamicRLAgent agent;
    
    // Estado atual (Melhores parâmetros conhecidos)
    private List<OptParam> currentBestParams;

    public ParallelOptimizer(int threadCount, ModularEnvironment env, List<OptParam> initialParams) {
        this.threadCount = threadCount;
        this.env = env;
        this.currentBestParams = deepCopyParams(initialParams); // Cópia de segurança
        this.monitor = new PerformanceMonitor();
        
        // Cria pool de threads configurável
        this.executor = Executors.newFixedThreadPool(threadCount);
        this.agent = new DynamicRLAgent(currentBestParams);
    }

    public List<OptParam> runOptimization(int totalEpisodes) {
        System.out.println("Iniciando Otimização Paralela com " + threadCount + " threads.");

        // O batch size define quantas variantes testamos por rodada
        // Se temos 8 threads, podemos testar 8, 16 ou 32 variantes por vez.
        int batchSize = threadCount * 2; 
        double globalBestReward = -Double.MAX_VALUE;
        int episodesWithoutImprovement = 0; // Contador de estagnação

        for (int i = 0; i < totalEpisodes; i++) {
            List<Callable<SimulationResult>> tasks = new ArrayList<>();

            // 1. Gerar Batch de Cenários (Exploração Paralela)
            for (int k = 0; k < batchSize; k++) {
                // Para cada tarefa, pegamos o estado base e aplicamos uma ação diferente
                // Clonamos os parâmetros base para não afetar o original
                List<OptParam> candidateParams = deepCopyParams(currentBestParams);
                
                // Agente escolhe uma ação (Exploração ou Exploitation)
                // Nota: Precisamos adaptar o agente para sugerir ações sem comprometer o estado ainda
                String action = agent.chooseActionForSim(k); 
                applyActionToParams(candidateParams, action);

                tasks.add(new SimulationTask(env, candidateParams, "Worker-" + (k % threadCount)));
            }

            try {
                // 2. O Gerenciador ESPERA todas as avaliações chegarem (Sincronização)
                List<Future<SimulationResult>> futures = executor.invokeAll(tasks);

                SimulationResult bestOfBatch = null;

                // 3. Processa os resultados
                for (Future<SimulationResult> future : futures) {
                    SimulationResult result = future.get(); // Bloqueia até obter resultado
                    
                    // Registra performance
                    monitor.record(result.workerName, result.executionTimeNano);

                    // Lógica de Decisão do Gerenciador:
                    // Verifica se algum worker encontrou um resultado melhor que o atual
                    if (bestOfBatch == null || result.reward > bestOfBatch.reward) {
                        bestOfBatch = result;
                    }
                    
                    // Opcional: Treinar o agente com TODOS os resultados (Off-policy learning)
                    // agent.learnFromSample(...);
                }

                // 4. Tomada de Decisão (Consolidação)
 /*               if (bestOfBatch != null) {
                	// Formata a lista de parâmetros: "Nome=Valor, Nome=Valor..."
                    String formattedParams = bestOfBatch.usedParams.stream()
                        .map(OptParam::toString) // Chama o toString() de cada parâmetro (ex: "G_Kernel=5")
                        .collect(Collectors.joining(", "));

                    // Imprime cabeçalho do Batch
                    System.out.printf("Batch %3d | Reward: %8.1f | Círculos: %3d | Thread: %s%n",
                            i,
                            bestOfBatch.reward,
                            bestOfBatch.detectedCircles.size(),
                            bestOfBatch.workerName);

                    // Imprime os parâmetros usados nessa melhor rodada
                    System.out.println("   >> Params: " + formattedParams);

                    // Atualiza o melhor global se necessário (Código existente...)
                    if (bestOfBatch.reward > -50) {
                         this.currentBestParams = bestOfBatch.usedParams;
                    }
                }*/
                if (bestOfBatch != null) {
                	// Formata a lista de parâmetros: "Nome=Valor, Nome=Valor..."
                    double currentMeanIoU = env.calculateMeanIoU(bestOfBatch.detectedCircles);
                    String formattedParams = bestOfBatch.usedParams.stream()
                        .map(OptParam::toString) // Chama o toString() de cada parâmetro (ex: "G_Kernel=5")
                        .collect(Collectors.joining(", "));
                    // Imprime o output no console
                    System.out.printf("Batch %3d | Reward: %8.1f | Círculos: %3d | mIoU: %.4f | Thread: %s%n",
                            i,
                            bestOfBatch.reward,
                            bestOfBatch.detectedCircles.size(),
                            currentMeanIoU,                 // <--- Variável adicionada
                            bestOfBatch.workerName);

                    System.out.println("   >> Params: " + formattedParams);

                    // --- VERIFICAÇÃO DE MELHORIA GLOBAL ---
                    if (bestOfBatch.reward > globalBestReward) {
                        globalBestReward = bestOfBatch.reward;
                        this.currentBestParams = bestOfBatch.usedParams;
                        episodesWithoutImprovement = 0; // Zera o contador pois houve melhoria
                    } else {
                        episodesWithoutImprovement++; // Incrementa se não melhorou
                    }

                    // --- CRITÉRIO DE PARADA 1: OBJETIVO ALCANÇADO ---
                    // Verifica com o ambiente se esta detecção foi 100% precisa
                    if (env.isGoalReached(bestOfBatch.detectedCircles)) {
                        System.out.println("\n✅ CRITÉRIO DE PARADA ATINGIDO!");
                        System.out.println("Todos os círculos foram encontrados com Centro (tol: "
                            + env.getRewardConfig().getStopToleranceCenter() + ") e Raio (tol: "
                            + env.getRewardConfig().getStopToleranceRadius() + ") previstos corretamente.");
                        break; // Sai do loop 'for' imediatamente
                    }

                    // --- CRITÉRIO DE PARADA 2: ESTAGNAÇÃO (PLATEAU) ---
                    if (episodesWithoutImprovement >= env.getRewardConfig().getPatienceLimit()) {
                        System.out.println("\n⚠️ PARADA POR ESTAGNAÇÃO (EARLY STOPPING)!");
                        System.out.println("O algoritmo rodou " + env.getRewardConfig().getPatienceLimit()
                            + " episódios sem encontrar uma recompensa melhor.");
                        System.out.println("Assumindo convergência para o melhor resultado possível localmente.");
                        break; // Sai do loop 'for' imediatamente
                    }
                }
                
                
                // Debug de Performance a cada 10 rodadas
                if (i % 10 == 0) monitor.printStats();

            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }
        
        executor.shutdown();
        System.out.println("Otimização Encerrada.");
        return this.currentBestParams;
    }
    
    /**
     * Cria uma cópia profunda (Deep Copy) da lista de parâmetros.
     * Isso é CRÍTICO para multithread: cada thread precisa de seus próprios objetos
     * para não interferir na thread vizinha.
     */
    private List<OptParam> deepCopyParams(List<OptParam> src) {
        List<OptParam> copy = new ArrayList<>();
        for (OptParam p : src) {
            // Cria um novo objeto OptParam com os MESMOS valores do original
            // Assumindo o construtor: OptParam(name, value, min, max, step, isInteger)
            // Nota: Você pode precisar adicionar getters na classe OptParam se não tiverem
            OptParam newP = new OptParam(
                p.getName(),
                p.getValue(),
                p.getMin(),
                p.getMax(),
                p.getStep(),
                p.isInteger()
            );
            copy.add(newP);
        }
        return copy;
    }

    /**
     * Aplica a ação (string) na lista de parâmetros fornecida.
     * Formato da ação: "INDEX_DIRECAO" (ex: "2_UP", "0_DOWN")
     */
    private void applyActionToParams(List<OptParam> params, String action) {
        try {
            String[] parts = action.split("_");
            int idx = Integer.parseInt(parts[0]);
            String dir = parts[1];

            // Proteção de índice
            if (idx >= 0 && idx < params.size()) {
                OptParam target = params.get(idx);
                if (dir.equals("UP")) {
                    target.increase();
                } else {
                    target.decrease();
                }
            }
        } catch (Exception e) {
            System.err.println("Erro ao aplicar ação paralela: " + action);
        }
    }
}