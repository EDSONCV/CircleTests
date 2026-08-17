package Utilities.test;


import org.opencv.core.Core;

import java.io.File;

import static Utilities.RegionCombiner.combineAndSaveRegions;

public class TestCircleThickNoFilter {
    // =========================================================
    // MÉTODO MAIN DE TESTE
    // =========================================================
    public static void main(String[] args) {
        // --- LOAD NATIVE LIBRARY DO OPENCV ---
        try {
            System.load(new File("lib/java/x64/" + Core.NATIVE_LIBRARY_NAME + ".dll").getAbsolutePath());
        } catch (UnsatisfiedLinkError e) {
            System.err.println("Erro ao carregar a biblioteca nativa do OpenCV./n" + e);
            System.exit(1);
        }

        String[] legendas = {
                "Original",
                "Resultado Final"
        };

        // Região de Corte (Entre X=0 a 500, Y=200 a 400)
        int[] startXY = {0, 0};
        int[] endXY = {190, 190};

        // Parâmetros de Entrada (Exemplo de uso)
        String[] imagens = {
                "W:/artigoPSE/bordas_grossas/sem_filtro_IoT_01/01_Original.png",
                "W:/artigoPSE/bordas_grossas/sem_filtro_IoT_01/02_Resultado_Final.png"
        };


        String outputPathHorizontal = "W:/artigoPSE/bordas_grossas/sem_filtro_IoT_01/Prancha_Artigo_Horizontal.png";
        String outputPathVertical = "W:/artigoPSE/bordas_grossas/sem_filtro_IoT_01/Prancha_Artigo_Vertical.png";

        // Chama a função no modo HORIZONTAL
        System.out.println("Gerando Layout Horizontal...");
        combineAndSaveRegions(imagens, startXY, endXY, legendas, outputPathHorizontal, true);

        // Chama a função no modo VERTICAL
        System.out.println("Gerando Layout Vertical...");
        combineAndSaveRegions(imagens, startXY, endXY, legendas, outputPathVertical, false);
        //


        // Parâmetros de Entrada (Exemplo de uso)
        String[] imagens2 = {
                "W:/artigoPSE/bordas_grossas/sem_filtro_IoT_05/01_Original.png",
                "W:/artigoPSE/bordas_grossas/sem_filtro_IoT_05/02_Resultado_Final.png"
        };

        String outputPathHorizontal2 = "W:/artigoPSE/bordas_grossas/sem_filtro_IoT_05/Prancha_Artigo_Horizontal.png";
        String outputPathVertical2 = "W:/artigoPSE/bordas_grossas/sem_filtro_IoT_05/Prancha_Artigo_Vertical.png";

        // Chama a função no modo HORIZONTAL
        System.out.println("Gerando Layout Horizontal...");
        combineAndSaveRegions(imagens2, startXY, endXY, legendas, outputPathHorizontal2, true);

        // Chama a função no modo VERTICAL
        System.out.println("Gerando Layout Vertical...");
        combineAndSaveRegions(imagens2, startXY, endXY, legendas, outputPathVertical2, false);

        // Parâmetros de Entrada (Exemplo de uso)
        String[] imagens3 = {
                "W:/artigoPSE/bordas_grossas/sem_filtro_IoT_09/01_Original.png",
                "W:/artigoPSE/bordas_grossas/sem_filtro_IoT_09/02_Resultado_Final.png"
        };

        String outputPathHorizontal3 = "W:/artigoPSE/bordas_grossas/sem_filtro_IoT_09/Prancha_Artigo_Horizontal.png";
        String outputPathVertical3 =   "W:/artigoPSE/bordas_grossas/sem_filtro_IoT_09/Prancha_Artigo_Vertical.png";

        // Chama a função no modo HORIZONTAL
        System.out.println("Gerando Layout Horizontal...");
        combineAndSaveRegions(imagens3, startXY, endXY, legendas, outputPathHorizontal3, true);

        // Chama a função no modo VERTICAL
        System.out.println("Gerando Layout Vertical...");
        combineAndSaveRegions(imagens3, startXY, endXY, legendas, outputPathVertical3, false);

    }

}

