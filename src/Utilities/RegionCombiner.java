package Utilities;


import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import javax.swing.*;
import java.awt.*;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class RegionCombiner {

    /**
     * Extrai uma região (ROI) de um conjunto de imagens, adiciona legendas em uma tarja externa e combina-as.
     *
     * @param imagePaths   Vetor com o caminho completo das imagens de origem.
     * @param startXY      Vetor de 2 posições {X, Y} indicando o ponto superior esquerdo do corte.
     * @param endXY        Vetor de 2 posições {X, Y} indicando o ponto inferior direito do corte.
     * @param labels       Vetor com as legendas para cada imagem (deve ter o mesmo tamanho de imagePaths).
     * @param outputPath   Caminho e nome do arquivo final (ex: "C:/resultados/comparacao.png").
     * @param isHorizontal Booleano: true para lado-a-lado, false para cima-para-baixo.
     */
    public static void combineAndSaveRegions(
            String[] imagePaths,
            int[] startXY,
            int[] endXY,
            String[] labels,
            String outputPath,
            boolean isHorizontal) {

        // 1. Validação Básica
        if (imagePaths.length == 0 || startXY.length != 2 || endXY.length != 2) {
            System.err.println("Parâmetros inválidos fornecidos.");
            return;
        }

        // Calcula Largura e Altura do corte solicitado
        int width = endXY[0] - startXY[0];
        int height = endXY[1] - startXY[1];
        Rect targetRoi = new Rect(startXY[0], startXY[1], width, height);

        List<Mat> croppedMats = new ArrayList<>();

        // 2. Loop de Processamento das Imagens
        for (int i = 0; i < imagePaths.length; i++) {
            Mat img = Imgcodecs.imread(imagePaths[i]);

            if (img.empty()) {
                System.err.println("Erro crítico: Imagem não encontrada -> " + imagePaths[i]);
                continue; // Pula esta imagem
            }

            // Cálculo manual da Intersecção dos Rects para evitar saídas de limite
            Rect imageBounds = new Rect(0, 0, img.cols(), img.rows());

            int x1 = Math.max(targetRoi.x, imageBounds.x);
            int y1 = Math.max(targetRoi.y, imageBounds.y);
            int x2 = Math.min(targetRoi.x + targetRoi.width, imageBounds.x + imageBounds.width);
            int y2 = Math.min(targetRoi.y + targetRoi.height, imageBounds.y + imageBounds.height);

            int safeW = Math.max(0, x2 - x1);
            int safeH = Math.max(0, y2 - y1);

            Rect safeRoi = new Rect(x1, y1, safeW, safeH);

            if (safeRoi.width <= 0 || safeRoi.height <= 0) {
                System.err.println("Erro: A região de corte está fora dos limites na imagem " + imagePaths[i]);
                img.release();
                continue;
            }

            // Cria o corte e faz um clone
            Mat crop = new Mat(img, safeRoi).clone();

            // Redimensiona caso a proteção de borda tenha encolhido o corte
            if (crop.width() != width || crop.height() != height) {
                Imgproc.resize(crop, crop, new Size(width, height));
            }

            // 3. Adiciona a Legenda em uma Tarja Externa Superior
            String label = (labels != null && i < labels.length) ? labels[i] : "";
            if (!label.isEmpty()) {
                int[] baseline = {0};
                double fontScale = 0.5;
                int thickness = 1;

                // Mede o tamanho exato da palavra para o tamanho da fonte escolhida
                Size textSize = Imgproc.getTextSize(label, Imgproc.FONT_HERSHEY_SIMPLEX, fontScale, thickness, baseline);

                // Define a altura da tarja preta (Altura do texto + margem superior e inferior)
                int bannerHeight = (int) textSize.height + 20;

                // Cria uma nova matriz expandida com uma borda preta no TOPO
                Mat cropWithBanner = new Mat();
                Core.copyMakeBorder(crop, cropWithBanner, bannerHeight, 0, 0, 0, Core.BORDER_CONSTANT, new Scalar(0, 0, 0));

                // Calcula as coordenadas para centralizar o texto exatamente no meio da tarja preta
                int textX = (cropWithBanner.cols() - (int) textSize.width) / 2;
                int textY = bannerHeight - 8; // 8 pixels de margem da base da tarja

                // Escreve o texto em branco na área preta recém-criada
                Imgproc.putText(cropWithBanner, label,
                        new org.opencv.core.Point(textX, textY),
                        Imgproc.FONT_HERSHEY_SIMPLEX, fontScale, new Scalar(255, 255, 255), thickness);

                // Substitui o crop antigo pelo novo crop com a tarja, e limpa a memória do antigo
                crop.release();
                crop = cropWithBanner;
            }

            croppedMats.add(crop);
            img.release(); // Libera a imagem original pesada da RAM
        }

        if (croppedMats.isEmpty()) {
            System.err.println("Nenhuma imagem válida foi processada.");
            return;
        }

        // 4. Concatenação Mágica do OpenCV
        Mat finalCombinedImage = new Mat();
        if (isHorizontal) {
            Core.hconcat(croppedMats, finalCombinedImage);
        } else {
            Core.vconcat(croppedMats, finalCombinedImage);
        }

        // 5. Salvar Imagem Final no Disco
        boolean success = Imgcodecs.imwrite(outputPath, finalCombinedImage);
        if (success) {
            System.out.println("✅ Imagem combinada salva com sucesso em: " + outputPath);
        } else {
            System.err.println("❌ Falha ao salvar a imagem em: " + outputPath);
        }

        // 6. Mostra no Visualizador
        visualize(finalCombinedImage, outputPath);

        // Limpa a RAM
        for (Mat m : croppedMats) {
            m.release();
        }
    }

    /**
     * Cria um JFrame para visualizar o resultado e permitir fechamento.
     */
    private static void visualize(Mat imageMat, String title) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Visualizador: " + title);
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setLayout(new BorderLayout());

            BufferedImage img = matToBufferedImage(imageMat);
            JLabel imageLabel = new JLabel(new ImageIcon(img));
            JScrollPane scrollPane = new JScrollPane(imageLabel);

            // Garante que a janela não fique maior que a tela do computador
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            int maxWidth = (int) (screenSize.width * 0.9);
            int maxHeight = (int) (screenSize.height * 0.9);
            scrollPane.setPreferredSize(new Dimension(Math.min(img.getWidth() + 50, maxWidth), Math.min(img.getHeight() + 50, maxHeight)));

            frame.add(scrollPane, BorderLayout.CENTER);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }

    // Utilitário de conversão padrão OpenCV -> Java
    private static BufferedImage matToBufferedImage(Mat m) {
        if (m == null || m.empty()) return null;
        int type = BufferedImage.TYPE_3BYTE_BGR;
        if (m.channels() == 1) type = BufferedImage.TYPE_BYTE_GRAY;
        int bufferSize = m.channels() * m.cols() * m.rows();
        byte[] b = new byte[bufferSize];
        m.get(0, 0, b);
        BufferedImage image = new BufferedImage(m.cols(), m.rows(), type);
        final byte[] targetPixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        System.arraycopy(b, 0, targetPixels, 0, b.length);
        return image;
    }


}
