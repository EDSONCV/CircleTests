package ParallelReinfLearningMod;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

class PerformanceMonitor {
    // Map: Thread Name -> Sum of execution times
    private ConcurrentHashMap<String, AtomicLong> totalTimeMap = new ConcurrentHashMap<>();
    // Map: Thread Name -> Number of tasks executed
    private ConcurrentHashMap<String, Integer> taskCountMap = new ConcurrentHashMap<>();

    public void record(String workerName, long nanoTime) {
        totalTimeMap.computeIfAbsent(workerName, k -> new AtomicLong(0)).addAndGet(nanoTime);
        taskCountMap.merge(workerName, 1, Integer::sum);
    }

    public void printStats() {
        System.out.println("---Thread Performance Report ---");
        long maxAvg = 0;
        String slowestThread = "";

        for (String worker : totalTimeMap.keySet()) {
            long total = totalTimeMap.get(worker).get();
            int count = taskCountMap.get(worker);
            if (count == 0) continue;
            
            long avg = total / count;
            double avgMs = avg / 1_000_000.0;
            
            System.out.printf("[%s] Executions: %d | Avarage: %.2f ms%n", worker, count, avgMs);

            if (avg > maxAvg) {
                maxAvg = avg;
                slowestThread = worker;
            }
        }
        System.out.println(">> Slowest Thread (bottleneck): " + slowestThread);
        System.out.println("--------------------------------------------");
    }
}
