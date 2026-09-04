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
    //  (Tabu List) ---
    private java.util.Set<String> visitedStates = java.util.concurrent.ConcurrentHashMap.newKeySet();
    
    // Instant State (Best known parameters)
    private List<OptParam> currentBestParams;


    public ParallelOptimizer(int threadCount, ModularEnvironment env, List<OptParam> initialParams) {
        this.threadCount = threadCount;
        this.env = env;
        this.currentBestParams = deepCopyParams(initialParams); // security copy
        this.monitor = new PerformanceMonitor();

        // Creates a configurable thread pool
        this.executor = Executors.newFixedThreadPool(threadCount);
        this.agent = new DynamicRLAgent(currentBestParams);
        this.explorationHistory = new ArrayList<>();
        // Defines the header of the CSV file
        this.explorationHistory.add("Batch,Thread,Time_ms,Reward,Circle,mIoU,mDist_px,mHybrid,Parameters");
    }

    public List<OptParam> runOptimization(int totalEpisodes) {
        System.out.println("Starting parallel optimization...");
        //int batchSize = threadCount * 2;
        // we use 1:1 thread relation, but can be used a 1:2 to promoted efficiency
        int batchSize = threadCount ;
        double globalBestReward = -Double.MAX_VALUE;
        int episodesWithoutImprovement = 0;
        int globalBestCircles = 0;
        double globalBestIoU = 0.0;
        double globalBestDist = 0.0;
        double globalBestHybrid = 0.0;

        // "safe" building
        // Initializes the safe with default parameters so it doesn't start empty.
        List<OptParam> absoluteBestParams = deepCopyParams(this.currentBestParams);

        for (int i = 0; i < totalEpisodes; i++) {
            List<Callable<SimulationResult>> tasks = new ArrayList<>();

            // Inside the for loop that creates the tasks in ParallelOptimizer:
            for (int k = 0; k < batchSize; k++) {
                List<OptParam> candidateParams = null;
                boolean isNovel = false;
                int attempts = 0;

                // Attempts to find a UNIQUE configuration (up to 50 attempts)
                while (!isNovel && attempts < 50) {
                    candidateParams = deepCopyParams(this.currentBestParams);

                    if (attempts == 0) {
                        // On the first attempt, let the RL Agent choose the immediate neighbor.
                        String action = agent.chooseActionForSim(k);
                        applyActionToParams(candidateParams, action);
                    } else {
                        // If it's already a repeated error, force MULTIPLE MUTATIONS to escape congestion!
                        // The more it fails, the more parameters it changes at the same time (Tabu Search)
                        int numMutations = (attempts / 5) + 1;
                        for (int m = 0; m < numMutations; m++) {
                            int randomParamIndex = (int) (Math.random() * candidateParams.size());
                            OptParam paramToMutate = candidateParams.get(randomParamIndex);
                            if (Math.random() > 0.5) paramToMutate.increase();
                            else paramToMutate.decrease();
                        }
                    }

                   // Generates the "Signature" for this configuration.
                    String paramsKey = candidateParams.stream()
                            .map(OptParam::toString)
                            .collect(Collectors.joining("; "));

                    // If this signature has NEVER been seen in the entire history of training:
                    if (!visitedStates.contains(paramsKey)) {
                        isNovel = true;
                        visitedStates.add(paramsKey); //Write this down in your notebook so that no one else repeats it.
                    }

                    attempts++;
                }

                // --- CORRECTION OF THE RACE CONDITION (THREAD-SAFE) ---
                // We instantiated a clean and exclusive 'clone' environment for this Worker.
                ModularEnvironment localEnv = new ModularEnvironment(
                        this.env.getOriginalImage(),
                        this.env.getGroundTruth(),
                        this.env.getPipelineFactory(), // <--- USE the factory here
                        this.env.getRewardConfig()
                );

                //We passed 'localEnv' instead of 'env' global.
                tasks.add(new SimulationTask(localEnv, candidateParams, "Worker-" + (k % threadCount)));
            }

            try {
                // Execute the threads
                List<Future<SimulationResult>> futures = executor.invokeAll(tasks);
                SimulationResult bestOfBatch = null;

                if (verboseMode) {
                    System.out.println("   --- Thread details  (Batch " + i + ") ---");
                }

                for (Future<SimulationResult> future : futures) {
                    SimulationResult result = future.get();

                   // 1. Thread Metrics Calculations
                    double timeMs = result.executionTimeNano / 1_000_000.0;
                    double threadIoU = env.calculateMeanIoU(result.detectedCircles);
                    int circulosEncontrados = result.detectedCircles.size(); // <-- Capture the circles

                   // --- NEW METRIC CALCULATIONS ---

                    double[] hybridMetrics = env.calculateHybridMetricsForLog(result.detectedCircles);
                    double meanDist = hybridMetrics[0];
                    double meanHybrid = hybridMetrics[1];

                   /* We use semicolons (;) to separate the parameters,
                    ensuring that the columns in the CSV file don't break!*/
                    String threadParams = result.usedParams.stream()
                            .map(OptParam::toString)
                            .collect(java.util.stream.Collectors.joining("; "));

                    // 2. SAVES TO CSV LOG (Always happens in the background)
                    /* We use Locale.US to ensure that decimals use a period (e.g., 0.95) instead of a comma
                    / Saves to the log including the circles*/
                    if(logResults) {
                        String logLine = String.format(java.util.Locale.US, "%d,%s,%.2f,%.2f,%d,%.4f,%.2f,%.4f,%s",
                                i, result.workerName, timeMs, result.reward, circulosEncontrados, threadIoU, meanDist, meanHybrid, threadParams);
                        explorationHistory.add(logLine);

                        explorationHistory.add(logLine);
                    }
                    // --- Detailed printing by thread (if the flag is enabled) ---
                    if (verboseMode) {

                        // Prints: [Worker-X] Time | Rewards | mIoU | Params
                        System.out.printf("   [ %-8s ] Time: %5.1f ms | Reward: %8.1f | Circles: %3d | mIoU: %.4f | mDist: %5.1f px | mHybrid: %.4f | Params: %s%n",
                                result.workerName, timeMs, result.reward, circulosEncontrados, threadIoU, meanDist, meanHybrid, threadParams);
                    }

                    if (bestOfBatch == null || result.reward > bestOfBatch.reward) {
                        bestOfBatch = result;
                    }
                }
                if (verboseMode) {
                    System.out.println("   ------------------------------------------");
                }
               // Evaluates the results of the Batch
                if (bestOfBatch != null) {
                    double currentMeanIoU = env.calculateMeanIoU(bestOfBatch.detectedCircles);
                    double[] bestMetrics = env.calculateHybridMetricsForLog(bestOfBatch.detectedCircles);
                    double currentMeanDist = bestMetrics[0];
                    double currentMeanHybrid = bestMetrics[1];

                    //System.out.printf("Batch %3d | Reward: %8.1f | Circles: %3d | mIoU: %.4f | Thread: %s%n",
                    //        i, bestOfBatch.reward, bestOfBatch.detectedCircles.size(), currentMeanIoU, bestOfBatch.workerName);

                    // --- 1. Recovers the formatting of the parameters ---
                    String formattedParams = bestOfBatch.usedParams.stream()
                            .map(OptParam::toString)
                            .collect(java.util.stream.Collectors.joining(", "));

                    // --- BATCH WINNER'S PRINT (UPDATED) ---
                    System.out.printf("Batch %3d | Reward: %8.1f | Circles: %3d | mIoU: %.4f | mDist: %5.1f px | mHybrid: %.4f | Thread: %s%n",
                            i, bestOfBatch.reward, bestOfBatch.detectedCircles.size(), currentMeanIoU, currentMeanDist, currentMeanHybrid, bestOfBatch.workerName);

                    System.out.println("   >> Params: " + formattedParams);

                    // --- GLOBAL RECORD VERIFICATION ---
                    if (bestOfBatch.reward > globalBestReward) {
                        globalBestReward = bestOfBatch.reward;
                        globalBestCircles = bestOfBatch.detectedCircles.size();
                        globalBestIoU = currentMeanIoU;
                        globalBestDist = currentMeanDist;     // <-- SAVE THE DISTANCE RECORD
                        globalBestHybrid = currentMeanHybrid; // <-- SAVE THE HYBRID RECORD

                        this.currentBestParams = deepCopyParams(bestOfBatch.usedParams);
                        absoluteBestParams = deepCopyParams(bestOfBatch.usedParams);
                        episodesWithoutImprovement = 0;
                    } else {
                        episodesWithoutImprovement++;
                    }
                    // --- [EXPLORATORY JUMP STEP] ---
                    int patienceLimit = env.getRewardConfig().getPatienceLimit();

                    // Trigger the jump when the stagnation reaches half of the patience limit.
                    if (episodesWithoutImprovement == (patienceLimit / 2)) {
                        System.out.println("\n⚠️STAGNATION DETECTED! Initiating Radical Exploratory Leap...");

                        // Tell the RL agent to revert to randomness (if implemented).
                        agent.triggerExplorationBurst();

                        // It confuses the explorer, but 'absoluteBestParams' remains safe!
                        scrambleParameters(this.currentBestParams);
                    }

                    // ---STOPPING CRITERION 1: OBJECTIVE ACHIEVED ---
                    if (env.isGoalReached(bestOfBatch.detectedCircles)) {
                        System.out.println("\n✅ STOPPING CRITERION REACHED (mIoU Excellent)!");
                        break;
                    }

                    // --- STOP CRITERION 2: WITHDRAWAL (PLATEAU) ---
                    if (episodesWithoutImprovement >= patienceLimit) {
                        System.out.println("\n🛑 DEFINITIVE STOP DUE TO STAGNATION.");
                        break;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        executor.shutdown();

        // --- GENERATES THE CSV FILE WITH THE ENTIRE TRAINING HISTORY ---
        if(logResults)
            exportLogToCSV();

        // [STEP OF ELITISM]: Returns the Vault variable, never the Explorer!

// --- FINAL SUMMARY OF OPTIMIZATION (UPDATED) ---
        String finalParamsStr = absoluteBestParams.stream()
                .map(OptParam::toString)
                .collect(java.util.stream.Collectors.joining(", "));

        System.out.println("\n=======================================================================");
        System.out.println("🏆 FINAL RESULT OF THE OPTIMIZATION 🏆");
        System.out.printf("Reward: %.1f | Circles: %d | mIoU: %.4f | mDist: %.1f px | mHybrid: %.4f%n",
                globalBestReward, globalBestCircles, globalBestIoU, globalBestDist, globalBestHybrid);
        System.out.println("Best Parameters: " + finalParamsStr);
        System.out.println("=======================================================================\n");


        return absoluteBestParams;
    }

/**
 * Applies a strong random mutation to the current parameters to get them out of the hole.
 * Applies a strong random mutation to the current parameters to get them out of the hole (Local Optima).
* Strictly respects the parameter's 'Step' to avoid generating invalid numbers.
 */
    private void scrambleParameters(List<OptParam> params) {
        for (OptParam p : params) {
            //There is a 30% chance of applying the radical mutation to this specific parameter.
            if (Math.random() < 0.3) {

                // Calculates how many "steps" there are between the Minimum and the Maximum.
                double range = p.getMax() - p.getMin();
                int possibleSteps = (int) (range / p.getStep());

                // Selects a random number of steps
                int randomSteps = (int) (Math.random() * (possibleSteps + 1));

                //The new value will be the Minimum + (number of steps drawn * step size)
                double newValue = p.getMin() + (randomSteps * p.getStep());

                p.setValue(newValue);
            }
        }
    }

    /**
     * Exports the entire thread exploration history to a CSV file.
     */
    private void exportLogToCSV() {

        try (java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.File(logFileName))) {
            for (String line : explorationHistory) {
                writer.println(line);
            }
            System.out.println("\n📊 [EXPORT] Log successful saved on: " + logFileName);
        } catch (Exception e) {
            System.err.println("Error while saving exploration log: " + e.getMessage());
        }
    }
    
    /**
     * Creates a deep copy of the parameter list.
     * This is CRITICAL for multithreading: each thread needs its own objects
     * so as not to interfere with neighboring threads.
     */
    private List<OptParam> deepCopyParams(List<OptParam> src) {
        List<OptParam> copy = new ArrayList<>();
        for (OptParam p : src) {
            // Creates a new OptParam object with the SAME values as the original
            // Assuming the constructor: OptParam(name, value, min, max, step, isInteger)

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
     * Applies the action (string) to the provided parameter list.
     * Action format: "INDEX_DIRECTION" (e.g., "2_UP", "0_DOWN")
     */
    private void applyActionToParams(List<OptParam> params, String action) {
        try {
            String[] parts = action.split("_");
            int idx = Integer.parseInt(parts[0]);
            String dir = parts[1];

            // Intex protection
            if (idx >= 0 && idx < params.size()) {
                OptParam target = params.get(idx);
                if (dir.equals("UP")) {
                    target.increase();
                } else {
                    target.decrease();
                }
            }
        } catch (Exception e) {
            System.err.println("Error applying parallel action.: " + action);
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