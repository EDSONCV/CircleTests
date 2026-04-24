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
    private boolean verboseMode = false;
    private boolean logResults = false;
    private String logFileName = "Historico_Exploracao_RL.csv";
    private List<String> explorationHistory; // <--- NOVA VARIÁVEL
    
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
        this.explorationHistory = new ArrayList<>();
        // Define o cabeçalho do arquivo CSV
        this.explorationHistory.add("Batch,Thread,Tempo_ms,Reward,Círculos encontrados,mIoU,Parametros");
    }

    public List<OptParam> runOptimization(int totalEpisodes) {
        System.out.println("Iniciando Otimização Paralela...");
        //int batchSize = threadCount * 2;
        // we use 1:1 thread relation, but can be used a 1:2 to promoted efficiency
        int batchSize = threadCount ;
        double globalBestReward = -Double.MAX_VALUE;
        int episodesWithoutImprovement = 0;
        int globalBestCircles = 0;
        double globalBestIoU = 0.0;

        // [PASSO DO ELITISMO]: Criação do "Cofre"
        // Inicializa o cofre com os parâmetros padrão para não começar vazio
        List<OptParam> absoluteBestParams = deepCopyParams(this.currentBestParams);

        for (int i = 0; i < totalEpisodes; i++) {
            List<Callable<SimulationResult>> tasks = new ArrayList<>();

            // Prepara as tarefas para as threads
            /*for (int k = 0; k < batchSize; k++) {
                List<OptParam> candidateParams = deepCopyParams(this.currentBestParams);
                String action = agent.chooseActionForSim(k);
                applyActionToParams(candidateParams, action);
                tasks.add(new SimulationTask(env, candidateParams, "Worker-" + (k % threadCount)));
            }*/
            // Dentro do for loop que cria as tasks no ParallelOptimizer:
            for (int k = 0; k < batchSize; k++) {
                List<OptParam> candidateParams = deepCopyParams(this.currentBestParams);
                String action = agent.chooseActionForSim(k);

                // Aplica a ação do Agente
                applyActionToParams(candidateParams, action);

                // --- NOVO CÓDIGO: VERIFICA SE BATEU NO LIMITE (Ficou igual) ---
                boolean isIdentical = true;
                for (int p = 0; p < candidateParams.size(); p++) {
                    if (candidateParams.get(p).getValue() != this.currentBestParams.get(p).getValue()) {
                        isIdentical = false;
                        break;
                    }
                }

                // Se a ação não fez nada (bateu no limite), forçamos uma mutação aleatória num parâmetro
                if (isIdentical) {
                    int randomParamIndex = (int) (Math.random() * candidateParams.size());
                    OptParam paramToMutate = candidateParams.get(randomParamIndex);

                    // Muta para cima ou para baixo aleatoriamente
                    if (Math.random() > 0.5) {
                        paramToMutate.increase();
                    } else {
                        paramToMutate.decrease();
                    }
                }
                // -------------------------------------------------------------

                tasks.add(new SimulationTask(env, candidateParams, "Worker-" + (k % threadCount)));
            }

            try {
                // Executa as threads
                List<Future<SimulationResult>> futures = executor.invokeAll(tasks);
                SimulationResult bestOfBatch = null;

                if (verboseMode) {
                    System.out.println("   --- Detalhes das Threads (Batch " + i + ") ---");
                }

                for (Future<SimulationResult> future : futures) {
                    SimulationResult result = future.get();

                    // 1. Cálculos de métricas da Thread
                    double timeMs = result.executionTimeNano / 1_000_000.0;
                    double threadIoU = env.calculateMeanIoU(result.detectedCircles);
                    int circulosEncontrados = result.detectedCircles.size(); // <-- Captura os círculos

                    // Usamos ponto e vírgula (;) para separar os parâmetros,
                    // garantindo que não quebre as colunas do arquivo CSV!
                    String threadParams = result.usedParams.stream()
                            .map(OptParam::toString)
                            .collect(java.util.stream.Collectors.joining("; "));

                    // 2. SALVA NO LOG DO CSV (Ocorre sempre, no background)
                    // Usamos Locale.US para garantir que decimais usem ponto (ex: 0.95) em vez de vírgula
                    // Salva no log incluindo os Círculos
                    if(logResults) {
                        String logLine = String.format(java.util.Locale.US, "%d,%s,%.2f,%.2f,%d,%.4f,%s",
                                i, result.workerName, timeMs, result.reward, circulosEncontrados, threadIoU, threadParams);

                        explorationHistory.add(logLine);
                    }
                    // --- IMPRESSÃO DETALHADA POR THREAD (Se a flag estiver ativa) ---
                    if (verboseMode) {

                        // Imprime: [Worker-X] Tempo | Reward | mIoU | Params
                        System.out.printf("   [ %-8s ] Tempo: %5.1f ms | Reward: %8.1f | Círculos: %3d | mIoU: %.4f | Params: %s%n",
                                result.workerName, timeMs, result.reward, circulosEncontrados, threadIoU, threadParams);
                    }

                    if (bestOfBatch == null || result.reward > bestOfBatch.reward) {
                        bestOfBatch = result;
                    }
                }
                if (verboseMode) {
                    System.out.println("   ------------------------------------------");
                }
                // Avalia os resultados da Batch
                if (bestOfBatch != null) {
                    double currentMeanIoU = env.calculateMeanIoU(bestOfBatch.detectedCircles);

                    //System.out.printf("Batch %3d | Reward: %8.1f | Círculos: %3d | mIoU: %.4f | Thread: %s%n",
                    //        i, bestOfBatch.reward, bestOfBatch.detectedCircles.size(), currentMeanIoU, bestOfBatch.workerName);

                    // --- 1. RECUPERA A FORMATAÇÃO DOS PARÂMETROS ---
                    String formattedParams = bestOfBatch.usedParams.stream()
                            .map(OptParam::toString)
                            .collect(java.util.stream.Collectors.joining(", "));

                    // --- 2. IMPRIME O BATCH COM O mIoU ---
                    System.out.printf("Batch %3d | Reward: %8.1f | Círculos: %3d | mIoU: %.4f | Thread: %s%n",
                            i, bestOfBatch.reward, bestOfBatch.detectedCircles.size(), currentMeanIoU, bestOfBatch.workerName);

                    // --- 3. IMPRIME OS PARÂMETROS LOGO ABAIXO ---
                    System.out.println("   >> Params: " + formattedParams);




                    // --- VERIFICAÇÃO DE RECORDE GLOBAL ---
                    if (bestOfBatch.reward > globalBestReward) {
                        globalBestReward = bestOfBatch.reward;

                        globalBestCircles = bestOfBatch.detectedCircles.size(); // <-- Guarda para o final
                        globalBestIoU = currentMeanIoU;                         // <-- Guarda para o final

                        // Atualiza o explorador para a próxima rodada
                        this.currentBestParams = deepCopyParams(bestOfBatch.usedParams);

                        // [PASSO DO ELITISMO]: Salva no Cofre de Ouro!
                        absoluteBestParams = deepCopyParams(bestOfBatch.usedParams);

                        episodesWithoutImprovement = 0; // Zera a estagnação
                    } else {
                        episodesWithoutImprovement++;
                    }

                    // --- [PASSO DO SALTO EXPLORATÓRIO] ---
                    int patienceLimit = env.getRewardConfig().getPatienceLimit();

                    // Aciona o pulo quando a estagnação chegar na metade do limite de paciência
                    if (episodesWithoutImprovement == (patienceLimit / 2)) {
                        System.out.println("\n⚠️ ESTAGNAÇÃO DETECTADA! Iniciando Salto Exploratório Radical...");

                        // Diz para o agente RL voltar a ser aleatório (se tiver implementado)
                        agent.triggerExplorationBurst();

                        // Embaralha o explorador, mas o 'absoluteBestParams' continua salvo!
                        scrambleParameters(this.currentBestParams);
                    }

                    // --- CRITÉRIO DE PARADA 1: OBJETIVO ALCANÇADO ---
                    if (env.isGoalReached(bestOfBatch.detectedCircles)) {
                        System.out.println("\n✅ CRITÉRIO DE PARADA ATINGIDO (mIoU Excelente)!");
                        break;
                    }

                    // --- CRITÉRIO DE PARADA 2: DESISTÊNCIA (PLATEAU) ---
                    if (episodesWithoutImprovement >= patienceLimit) {
                        System.out.println("\n🛑 PARADA DEFINITIVA POR ESTAGNAÇÃO.");
                        break;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        executor.shutdown();

        // --- GERA O ARQUIVO CSV COM TODA A HISTÓRIA DO TREINAMENTO ---
        if(logResults)
            exportLogToCSV();

        // [PASSO DO ELITISMO]: Retorna a variável do Cofre, nunca o Explorador!
        // --- NOVO: RESUMO FINAL DA OTIMIZAÇÃO ---
                String finalParamsStr = absoluteBestParams.stream()
                .map(OptParam::toString)
                .collect(java.util.stream.Collectors.joining(", "));

        System.out.println("\n=======================================================");
        System.out.println("🏆 RESULTADO FINAL DA OTIMIZAÇÃO 🏆");
        System.out.printf("Reward: %.1f | Círculos: %d | mIoU: %.4f%n",
                globalBestReward, globalBestCircles, globalBestIoU);
        System.out.println("Melhores Parâmetros: " + finalParamsStr);
        System.out.println("=======================================================\n");
        return absoluteBestParams;
    }

    /**
     * Aplica uma mutação aleatória forte nos parâmetros atuais para tirá-los do buraco.
     */
    /**
     * Aplica uma mutação aleatória forte nos parâmetros atuais para tirá-los do buraco (Local Optima).
     * Respeita rigorosamente o 'Step' (pulo) do parâmetro para não gerar números inválidos.
     */
    private void scrambleParameters(List<OptParam> params) {
        for (OptParam p : params) {
            // 30% de chance de aplicar a mutação radical neste parâmetro específico
            if (Math.random() < 0.3) {

                // Calcula quantos "degraus" (steps) existem entre o Mínimo e o Máximo
                double range = p.getMax() - p.getMin();
                int possibleSteps = (int) (range / p.getStep());

                // Sorteia um número aleatório de degraus
                int randomSteps = (int) (Math.random() * (possibleSteps + 1));

                // O novo valor será o Mínimo + (degraus sorteados * tamanho do degrau)
                double newValue = p.getMin() + (randomSteps * p.getStep());

                p.setValue(newValue);
            }
        }
    }

    /**
     * Exporta todo o histórico de exploração das threads para um arquivo CSV.
     */
    private void exportLogToCSV() {

        try (java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.File(logFileName))) {
            for (String line : explorationHistory) {
                writer.println(line);
            }
            System.out.println("\n📊 [EXPORTAÇÃO] Log completo salvo com sucesso em: " + logFileName);
        } catch (Exception e) {
            System.err.println("Erro ao salvar log de exploração: " + e.getMessage());
        }
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

    public boolean isVerboseMode() {
        return verboseMode;
    }

    public void setVerboseMode(boolean verboseMode) {
        this.verboseMode = verboseMode;
    }

    public String getLogFileName() {
        return logFileName;
    }

    public void setLogFileName(String logFileName) {
        this.logResults = true;
        this.logFileName = logFileName;
    }

    public boolean isLogResults() {
        return logResults;
    }

    public void setLogResults(boolean logResults) {
        this.logResults = logResults;
    }
}