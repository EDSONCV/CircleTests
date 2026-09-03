package ParallelReinfLearningMod;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import filters.strategies.FilterOrderStrategy;
import filters.strategies.FullPermutationStrategy;
import filters.strategies.IncrementalPermutationStrategy;
import filters.strategies.SingleOrderStrategy;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import filters.*;
import org.opencv.imgproc.Imgproc;
import tstDataCircles.Data2;


public class ParallelModularCircleDetector {

    public static void main(String[] args) {
    	// --- LOAD NATIVE LIBRARY ---
        // Option A: Use the default library path (Ensure the DLL is in your system path or project root)
        try {String filePre = "";
        	String fileExt = ".dll";
        	 final File nativeLibrary = new File("lib/java/x64/" + filePre + Core.NATIVE_LIBRARY_NAME + fileExt);
             System.load(nativeLibrary.getAbsolutePath());
			System.out.println("Opencv Version   " + Core.NATIVE_LIBRARY_NAME );
        } catch (UnsatisfiedLinkError e) {
            // Option B: Load directly from absolute path if Option A fails
            // System.load("C:/path/to/opencv/build/java/x64/opencv_java4x.dll");
            System.err.println("Native code library failed to load. \n" + e);
            System.exit(1);
        }
	// --- 1. Select HOUGH strategy ---
	 final int selectedHoughMethod =  Imgproc.HOUGH_GRADIENT_ALT;
	//	final int selectedHoughMethod = Imgproc.HOUGH_GRADIENT;
		// Select between native Houg circles or pure Java implementation
		final boolean usePureJava = false; // true = Java  | false = OpenCV Native

	// =========================================================
	// 2. FILTER DEFINITION
	// =========================================================
	// Select filters here
		List<Class<? extends ImageFilter>> baseFilters = Arrays.asList(
		//		BilateralFilter.class,
		//		CLAHEFilter.class,
		//		AdaptiveThreshMeanCFilter.class,
				GaussianBlurFilter.class
		);


		// =========================================================
		// 3. Prepare the task order (Single order, Incremental Permutaion, etc.
		// =========================================================
		// Escolha a estratégia desejada comentando/descomentando:

         FilterOrderStrategy strategy = new SingleOrderStrategy();
		//FilterOrderStrategy strategy = new FullPermutationStrategy();
		//FilterOrderStrategy strategy = new IncrementalPermutationStrategy();

      // Generate the filter order and combinations
		List<List<Class<? extends ImageFilter>>> allOrders = strategy.generateOrders(baseFilters);


		// Track global optimum
		double globalBestScore = -Double.MAX_VALUE;
		List<OptParam> globalBestParams = null;
		List<Class<? extends ImageFilter>> globalBestOrder = null;
		Supplier<ProcessingPipeline> globalBestPipelineFactory = null;

        // --- 2. CONFIGURE ENVIRONMMENT
        List<Circle> groundTruth = Data2.getData();
        System.out.println("number of initial circles: " + groundTruth.size());
        
     // --- 3. CONFIGURE REWARDS ---
        RewardConfig myConfig = new RewardConfig();
        
        // USER RULES
        myConfig.setMatchBonus(50.0);           // MATCH BONUS
        myConfig.setMissPenalty(20.0);          // IF CIRCLE IS NOT DETECTED = PENALTY
        myConfig.setNoisePenalty(-5.0);         // NOISE (UNREAL CIRCLE ) = PENALTY
        myConfig.setSanityLimitAbsolute(200);    // MORE THAN THIS CIRCLES = PENALTY
        myConfig.setExcessPenaltyExponent(2.0); // PENALTY WEIGHTS
		myConfig.setIouThreshold(0.95);         // intersection over union threshold (to consider a good match)
		myConfig.setTargetMeanIoU(0.9);         // target mean IoU
		myConfig.setPatienceLimit(20);          // if no bether results are found after this number of batches, try other parameters
		// For images where center position is harder to find than the radius
		myConfig.setWeightIoU(0.9);   // weight for IoU
		myConfig.setWeightCenter(0.1);  // weight for Euclidian Center Distance
		myConfig.setMaxCenterDistance(5);  // max center distance to avois penalty

        // 4.load files
        //String imagePath ="testImages/matrix_output.png";
		//String imagePath ="testImages/shaded_spheres_ring.png";
		//String imagePath ="testImages/shaded_spheres_output_s15.png";
		String imagePath ="testImages/DSC00582.png";
		//String imagePath ="testImages/matrix_output_thin.png";


		for (int i = 0; i < allOrders.size(); i++) {
			List<Class<? extends ImageFilter>> currentOrder = allOrders.get(i);


			System.out.println("\n--- Testing Order " + (i+1) + "/" + allOrders.size() + " ---");

			// Dynamic Factory of filter combinatin/permutations
			Supplier<ProcessingPipeline> dynamicPipelineFactory = () -> {
				ProcessingPipeline p = new ProcessingPipeline();
				//set Houg algorithm!
				p.setHoughMethod(selectedHoughMethod);
				p.setUsePureJavaHough(usePureJava);
				try {
					for (Class<? extends ImageFilter> filterClass : currentOrder) {
						p.addFilter((ImageFilter) filterClass.getDeclaredConstructor().newInstance());
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
				return p;
			};

			// Starting Modular environment and parameters ...
			ModularEnvironment env = new ModularEnvironment(imagePath, groundTruth, dynamicPipelineFactory, myConfig);
			List<OptParam> initialParams = dynamicPipelineFactory.get().getAllParameters();

			// ... [Starting ParallelOptimizer] ...
			// --- 5. MULTITHREADING CONFIGURE BASED ON AVAILABLE CORES
			// Option A: Automatic
			int logicalCores = Runtime.getRuntime().availableProcessors();
			int threadCount = Math.max(1, logicalCores - 1);
			// Option B: Manual
			// int threadCount = 10;
			System.out.println("Hardware detected: " + logicalCores + " cores.");
			System.out.println("Start Otimizer with  " + threadCount + " parallel threads.");

			ParallelOptimizer optimizer = new ParallelOptimizer(threadCount, env, initialParams);

			// Optional: Verbose mode

			optimizer.setVerboseMode(true);
			optimizer.setLogResults(true);
			optimizer.setLogFileName("666threads.csv");
			long startTime = System.nanoTime();


			List<OptParam> bestParamsForThisOrder = optimizer.runOptimization(500);
			double score = env.evaluate(bestParamsForThisOrder);

			if (score > globalBestScore) {
				globalBestScore = score;
				globalBestParams = bestParamsForThisOrder;
				globalBestOrder = currentOrder;
				globalBestPipelineFactory = dynamicPipelineFactory;
			}

			System.out.println("=== Optimization Finished ===");
			System.out.println("Best Parameters: " + globalBestParams);
			long endTime = System.nanoTime();
			long durationNanos = endTime - startTime;
			System.out.println("Execution time: " + durationNanos + " nano seconds");
			System.out.println("Execution time: " + durationNanos/1.0E9 + " seconds");

			// Clean native memory
			env.releaseResources();
			System.gc();
		}


        // --- 7.  RESULTS Visualization ---

// Criating 'final' variables to GUIs Swing Thread see
		final Supplier<ProcessingPipeline> finalFactory = globalBestPipelineFactory;
		final List<OptParam> finalParams = globalBestParams;

		SwingUtilities.invokeLater(() -> {
			Mat originalImg = Imgcodecs.imread(imagePath);
			System.out.println("Image Size width:  " + originalImg.cols() + "  height: "+ originalImg.rows());

			// 1. Call winner thread/optimization to generate image pipeline(Debug)

			ProcessingPipeline finalPipeline = finalFactory.get();

			finalPipeline.syncParameters(finalParams);

			List<StepResult> debugSteps = finalPipeline.runPipelineWithDebug(originalImg);

			// rebuild with winner/best results

			ModularEnvironment finalEnv = new ModularEnvironment(imagePath, groundTruth, finalFactory, myConfig);


			List<Circle> finalCircles = finalEnv.runDetection(finalParams);

			String houghInfo = finalPipeline.getHoughParamsString();

			new ResultVisualizerCompare(
					debugSteps,
					finalCircles,
					groundTruth,
					houghInfo,
					"Parallel Optimization Results");

			System.out.println("Finished. Final Parameters: " + finalPipeline.getAllParameters());
			System.out.println("Number of circles found: " + finalCircles.size());
		});

    }

}
