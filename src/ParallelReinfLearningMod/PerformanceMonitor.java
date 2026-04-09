package ParallelReinfLearningMod;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

class PerformanceMonitor {
    // Mapa: Nome da Thread -> Soma dos tempos de execução
    private ConcurrentHashMap<String, AtomicLong> totalTimeMap = new ConcurrentHashMap<>();
    // Mapa: Nome da Thread -> Quantidade de tarefas executadas
    private ConcurrentHashMap<String, Integer> taskCountMap = new ConcurrentHashMap<>();

    public void record(String workerName, long nanoTime) {
        totalTimeMap.computeIfAbsent(workerName, k -> new AtomicLong(0)).addAndGet(nanoTime);
        taskCountMap.merge(workerName, 1, Integer::sum);
    }

    public void printStats() {
        System.out.println("--- Relatório de Performance das Threads ---");
        long maxAvg = 0;
        String slowestThread = "";

        for (String worker : totalTimeMap.keySet()) {
            long total = totalTimeMap.get(worker).get();
            int count = taskCountMap.get(worker);
            if (count == 0) continue;
            
            long avg = total / count;
            double avgMs = avg / 1_000_000.0;
            
            System.out.printf("[%s] Execuções: %d | Média: %.2f ms%n", worker, count, avgMs);

            if (avg > maxAvg) {
                maxAvg = avg;
                slowestThread = worker;
            }
        }
        System.out.println(">> Thread mais lenta (Gargalo): " + slowestThread);
        System.out.println("--------------------------------------------");
    }
}
