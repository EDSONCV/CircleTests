package CirclesGenerators;


import org.opencv.core.*;
import org.opencv.core.Point;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.util.Arrays;
import java.util.Random;

/**
 * Classe responsável por gerar a imagem de uma matriz de Esferas Sombreadas.
 */
public class ShadedSphereMatrixGenerator {
    private int tipoIluminacao;
    private int[] radii;
    private int[][] rgbColors;             // Array 2D: [n][3] -> {R, G, B}
    private double[][] illuminationParams; // Array genérico para os parâmetros da luz
    private double[] ambientLights;        // Array 1D: [n]
    private int spacing;                   // Afastamento entre células
    private boolean drawCenter;

    private Mat generatedImage;

    /**
     * Construtor da matriz de esferas com suporte a múltiplos modos de iluminação.
     * * @param tipoIluminacao O modo de iluminação (DIRECIONAL, RING_LIGHT, DEGRADE_BORDA).
     * @param radii Vetor com os raios das esferas (n deve ter raiz quadrada inteira).
     * @param rgbColors Matriz onde cada linha é um array {R, G, B} (0 a 255).
     * @param illuminationParams Matriz de parâmetros dependentes do tipo de luz.
     * @param ambientLights Vetor com as intensidades de luz ambiente (0.0 a 1.0).
     * @param spacing Afastamento entre os limites físicos de cada esfera na grade.
     * @param drawCenter Flag para desenhar um ponto central.
     */
    public ShadedSphereMatrixGenerator(int tipoIluminacao, int[] radii, int[][] rgbColors,
                                       double[][] illuminationParams, double[] ambientLights,
                                       int spacing, boolean drawCenter) {
        int n = radii.length;

        // Validação 1: Verifica se n é quadrado perfeito
        double sqrt = Math.sqrt(n);
        if ((sqrt % 1) != 0) {
            throw new IllegalArgumentException("O número de elementos (" + n + ") não forma uma matriz quadrada perfeita.");
        }

        // Validação 2: Verifica se todas as propriedades foram passadas para todos os círculos
        if (rgbColors.length != n || illuminationParams.length != n || ambientLights.length != n) {
            throw new IllegalArgumentException("Os vetores de cores, parâmetros de luz e luz ambiente devem ter o mesmo tamanho do vetor de raios (n=" + n + ").");
        }

        this.tipoIluminacao = tipoIluminacao;
        this.radii = radii;
        this.rgbColors = rgbColors;
        this.illuminationParams = illuminationParams;
        this.ambientLights = ambientLights;
        this.spacing = spacing;
        this.drawCenter = drawCenter;
    }

    /**
     * Processa a criação da imagem e imprime os dados no console.
     */
    public Mat generate() {
        int n = radii.length;
        int gridSize = (int) Math.sqrt(n);

        // 1. Encontrar o MAIOR raio para definir o tamanho universal da célula
        int maxRadius = Arrays.stream(radii).max().orElse(1);
        int cellSize = (maxRadius * 2) + spacing;

        // Tamanho total da imagem (+1 spacing para margem final)
        int imageSize = (gridSize * cellSize) + spacing;

        // Cria imagem com fundo cinza escuro para realçar os efeitos de iluminação
        this.generatedImage = new Mat(imageSize, imageSize, CvType.CV_8UC3, new Scalar(30, 30, 30));

        System.out.println("--- Dados das Esferas Geradas (" + tipoIluminacao + ") ---");

        // 2. Loop para construir e desenhar cada esfera individual
        for (int i = 0; i < n; i++) {
            int row = i / gridSize;
            int col = i % gridSize;
            int radius = radii[i];

            // Cálculo do Centro exato na grade virtual
            int centerX = spacing + (col * cellSize) + maxRadius;
            int centerY = spacing + (row * cellSize) + maxRadius;

            // Extração das características individuais deste índice
            int r = rgbColors[i][0];
            int g = rgbColors[i][1];
            int b = rgbColors[i][2];
            double amb = ambientLights[i];

            // 3. Inicializa o Builder
            ShadedSphere.Builder builder = new ShadedSphere.Builder(centerX, centerY, radius)
                    .comCorBGR(b, g, r)
                    .comLuzAmbiente(amb);

            // 4. Aplica os parâmetros específicos baseados no Modo de Iluminação
            double[] params = illuminationParams[i];

            switch (tipoIluminacao) {
                case 0: // direcional
                    // Exige 3 parâmetros: lx, ly, lz
                    builder.comDirecaoLuz(params[0], params[1], params[2]);
                    System.out.printf("Sphere[%d]: RGB=(%d,%d,%d) | Dir=(%.1f,%.1f,%.1f)%n", i, r, g, b, params[0], params[1], params[2]);
                    break;

                case 1://RING_LIGHT
                    // Exige 2 parâmetros: raioAnelNormalizado, larguraAnel
                    builder.comRingLight(params[0], params[1]);
                    System.out.printf("Sphere[%d]: RGB=(%d,%d,%d) | Ring(Raio=%.2f, Larg=%.2f)%n", i, r, g, b, params[0], params[1]);
                    break;

                case 2://DEGRADE_BORDA
                    // Exige 1 parâmetro: decaimento
                    builder.comDegradeBorda(params[0]);
                    System.out.printf("Sphere[%d]: RGB=(%d,%d,%d) | Degrade(Decaim=%.2f)%n", i, r, g, b, params[0]);
                    break;
            }

            // Renderiza na imagem
            ShadedSphere sphere = builder.build();
            sphere.desenhar(generatedImage);

            // 5. Desenha o centro mecânico se solicitado
            if (drawCenter) {
                Imgproc.circle(generatedImage, new Point(centerX, centerY), 2, new Scalar(0, 0, 255), -1);
            }
        }

        System.out.println("----------------------------------");
        return this.generatedImage;
    }

    public Mat getImage() {
        return generatedImage;
    }

}

/**
 * JFrame para exibir a imagem da matriz de esferas e permitir salvamento.
 */
class SphereMatrixViewer extends JFrame {

    public SphereMatrixViewer(Mat imageMat) {
        setTitle("Gerador de Matriz de Esferas Sombreadas");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        BufferedImage img = matToBufferedImage(imageMat);
        JLabel imageLabel = new JLabel(new ImageIcon(img));

        JScrollPane scrollPane = new JScrollPane(imageLabel);
        scrollPane.setPreferredSize(new Dimension(800, 800));
        add(scrollPane, BorderLayout.CENTER);

        JButton btnSave = new JButton("Salvar como PNG");
        btnSave.setFont(new Font("Arial", Font.BOLD, 14));
        btnSave.addActionListener((ActionEvent e) -> saveAction(imageMat));

        JPanel btnPanel = new JPanel();
        btnPanel.add(btnSave);
        add(btnPanel, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void saveAction(Mat mat) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Salvar Imagem de Esferas");
        fileChooser.setSelectedFile(new File("shaded_spheres_output.png"));

        int userSelection = fileChooser.showSaveDialog(this);
        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File fileToSave = fileChooser.getSelectedFile();
            String path = fileToSave.getAbsolutePath();
            if (!path.toLowerCase().endsWith(".png")) {
                path += ".png";
            }

            boolean success = Imgcodecs.imwrite(path, mat);
            if (success) {
                JOptionPane.showMessageDialog(this, "Matriz de Esferas salva com sucesso em:\n" + path);
            } else {
                JOptionPane.showMessageDialog(this, "Erro ao salvar imagem.", "Erro", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private BufferedImage matToBufferedImage(Mat m) {
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