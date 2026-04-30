package ParallelReinfLearningMod;

import javax.swing.SwingUtilities;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import filters.*;
import tstDataCircles.Data1;


public class ParallelModularCircleDetector {
    


    public static void main(String[] args) {
    	// --- LOAD NATIVE LIBRARY ---
        // Option A: Use the default library path (Ensure the DLL is in your system path or project root)
        try {String filePre = "";
        	String fileExt = ".dll";
        	 final File nativeLibrary = new File("lib/java/x64/" + filePre + Core.NATIVE_LIBRARY_NAME + fileExt);
             System.load(nativeLibrary.getAbsolutePath());
        } catch (UnsatisfiedLinkError e) {
            // Option B: Load directly from absolute path if Option A fails
            // System.load("C:/path/to/opencv/build/java/x64/opencv_java4x.dll");
            System.err.println("Native code library failed to load. \n" + e);
            System.exit(1);
        }
// --- 1. A FÁBRICA DE PIPELINES (SUPPLIER) ---
		// Em vez de instanciar direto, criamos uma "receita" que gera pipelines e filtros isolados
		Supplier<ProcessingPipeline> pipelineFactory = () -> {
			ProcessingPipeline p = new ProcessingPipeline();
//			p.addFilter(new BrightnessContrastFilter());
//			p.addFilter(new GaussianBlurFilter());
			p.addFilter(new GaussianBlurFilter());
			p.addFilter(new CLAHEFilter());
//			p.addFilter(new BilateralFilter());
			p.addFilter(new AdaptiveThreshFilter());

//			p.addFilter(new MorphClosingFilter());
			// Adicione os outros filtros que estava usando aqui
			return p;
		};
        
        // --- 2. CONFIGURAÇÃO DO AMBIENTE ---
        List<Circle> groundTruth = Data1.getData();
        System.out.println("number of initial circles: " + groundTruth.size());
        
     // --- 3. CONFIGURAÇÃO DAS RECOMPENSAS (SEM NÚMEROS MÁGICOS) ---
        RewardConfig myConfig = new RewardConfig();
        
        // Usuário define suas regras:
        myConfig.setMatchBonus(50.0);           // Quero valorizar muito o acerto
        myConfig.setMissPenalty(20.0);          // Omissão é grave
        myConfig.setNoisePenalty(-5.0);         // Ruído é mais grave que o padrão
        myConfig.setSanityLimitAbsolute(900);    // Se passar de 30 círculos, aborte
        myConfig.setExcessPenaltyExponent(2.0); // Punição quadrática rigorosa
		myConfig.setIouThreshold(0.95);
		myConfig.setTargetMeanIoU(0.98);
		myConfig.setPatienceLimit(50);
		// Para imagens onde a posição do centro é mais difícil de achar que o raio:
		myConfig.setWeightIoU(0.1);
		myConfig.setWeightCenter(0.9);
		myConfig.setMaxCenterDistance(5);

        // 4. Injeta a configuração no Ambiente
        //String imagePath ="testImages/matrix_output.png";
		String imagePath ="testImages/shaded_spheres_ring.png";
		//String imagePath ="testImages/matrix_output_thin.png";

		ModularEnvironment env = new ModularEnvironment(imagePath, groundTruth, pipelineFactory, myConfig);
     // --- 5. CONFIGURAÇÃO DE MULTITHREADING ---
        // Opção A: Automático (Núcleos lógicos - 1 para deixar o OS respirar)
        int logicalCores = Runtime.getRuntime().availableProcessors();
        int threadCount = Math.max(1, logicalCores - 1);
		//int threadCount = 2;
        // Opção B: Manual (Ex: vindo de um JSpinner da interface)
        // int threadCount = 10;

        System.out.println("Hardware detectado: " + logicalCores + " núcleos.");
        System.out.println("Iniciando Otimizador com " + threadCount + " threads em paralelo.");

        // --- 6. INSTANCIAÇÃO E EXECUÇÃO DO OTIMIZADOR ---
        // Passamos os parâmetros iniciais do pipeline para começar a exploração

        List<OptParam> initialParams = pipelineFactory.get().getAllParameters();
// measure time

		ParallelOptimizer optimizer = new ParallelOptimizer(threadCount, env, initialParams);

		// ATIVA O MODO DE EXPLORAÇÃO DETALHADO!
		optimizer.setVerboseMode(true);
		optimizer.setLogResults(true);
		optimizer.setLogFileName("27threads.csv");
		long startTime = System.nanoTime();
		List<OptParam> bestParams = optimizer.runOptimization(500);


        System.out.println("=== Otimização Finalizada ===");
        System.out.println("Melhores Parâmetros: " + bestParams);
		long endTime = System.nanoTime();
		long durationNanos = endTime - startTime;
		System.out.println("Execution time: " + durationNanos + " nano seconds");
		System.out.println("Execution time: " + durationNanos/1.0E9 + " seconds");



        // --- 7. VISUALIZAÇÃO DOS RESULTADOS ---
        // Precisamos aplicar os parâmetros vencedores de volta ao Pipeline principal
        // para gerar as imagens de debug corretamente.


		SwingUtilities.invokeLater(() -> {
			Mat originalImg = Imgcodecs.imread(imagePath);

			// 1. Puxa um pipeline da fábrica estritamente para gerar o histórico de imagens (Debug)
			ProcessingPipeline finalPipeline = pipelineFactory.get();
			finalPipeline.syncParameters(bestParams);
			List<StepResult> debugSteps = finalPipeline.runPipelineWithDebug(originalImg);

			// --- 2. CORREÇÃO AQUI: Execução Final Segura ---
			// Passamos os melhores parâmetros diretamente para o ambiente.
			// O ambiente encarrega-se de sincronizar o seu pipeline local e rodar o Hough.
			List<Circle> finalCircles = env.runDetection(bestParams);

			String houghInfo = finalPipeline.getHoughParamsString();

			new ResultVisualizerCompare(
					debugSteps,
					finalCircles,
					groundTruth,
					houghInfo,
					"Resultado Otimização Paralela");

			System.out.println("Fim. Parâmetros Finais: " + finalPipeline.getAllParameters());
			System.out.println("numero de círculos: " + finalCircles.size());
		});

    }

    private static String getSimpleState(int det, int truth) {
        if (det == truth) return "EXACT";
        if (det > truth) return "TOO_MANY";
        return "TOO_FEW";
    }
}
