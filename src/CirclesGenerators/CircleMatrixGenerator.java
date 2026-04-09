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

/**
 * Classe responsável por gerar a imagem da matriz de círculos.
 */
class CircleMatrixGenerator {
    private int[] radii;
    private int spacing;     // Afastamento entre bordas
    private int thickness;
    private Scalar color;
    private boolean drawCenter;
    
    private Mat generatedImage;

    /**
     * Construtor com os parâmetros solicitados.
     * @param radii Vetor com "n" raios (n deve ter raiz quadrada inteira).
     * @param spacing Afastamento entre os raios (pixels).
     * @param thickness Espessura da linha.
     * @param color Cor da linha (BGR).
     * @param drawCenter Flag para desenhar o centro.
     */
    public CircleMatrixGenerator(int[] radii, int spacing, int thickness, Scalar color, boolean drawCenter) {
        // Validação: Verifica se n é quadrado perfeito
        double sqrt = Math.sqrt(radii.length);
        if ((sqrt % 1) != 0) {
            throw new IllegalArgumentException("O número de raios fornecido (" + radii.length + ") não forma uma matriz quadrada perfeita.");
        }

        this.radii = radii;
        this.spacing = spacing;
        this.thickness = thickness;
        this.color = color;
        this.drawCenter = drawCenter;
    }

    /**
     * Processa a criação da imagem e imprime os dados no console.
     */
    public Mat generate() {
        int n = radii.length;
        int gridSize = (int) Math.sqrt(n);
        
        // 1. Encontrar o MAIOR raio para definir o tamanho da célula da grade
        // Isso garante alinhamento perfeito dos centros
        int maxRadius = Arrays.stream(radii).max().orElse(1);
        
        // O tamanho da célula considera o diâmetro do maior círculo + o espaçamento
        int cellSize = (maxRadius * 2) + spacing;
        
        // Tamanho total da imagem (+1 spacing para margem final)
        int imageSize = (gridSize * cellSize) + spacing;

        // Cria imagem branca (Fundo)
        this.generatedImage = new Mat(imageSize, imageSize, CvType.CV_8UC3, new Scalar(255, 255, 255));

        System.out.println("--- Dados dos Círculos Gerados ---");

        // 2. Loop para desenhar
        for (int i = 0; i < n; i++) {
            int row = i / gridSize;
            int col = i % gridSize;
            int radius = radii[i];

            // Cálculo do Centro (X, Y)
            // Lógica: Margem inicial + (coluna * tamanho_celula) + metade_da_celula
            // Isso centraliza qualquer círculo dentro da sua célula virtual
            int centerX = spacing + (col * cellSize) + maxRadius;
            int centerY = spacing + (row * cellSize) + maxRadius;
            
            Point center = new Point(centerX, centerY);

            // Desenha o círculo
            Imgproc.circle(generatedImage, center, radius, color, thickness);

            // Desenha o centro se solicitado (ponto vermelho pequeno)
            if (drawCenter) {
                Imgproc.circle(generatedImage, center, 2, new Scalar(0, 0, 255), -1);
            }

            // Output no Console conforme solicitado
            System.out.printf("new Circle( %d, %d, %d),%n", centerX, centerY, radius);
        }
        
        System.out.println("----------------------------------");
        return this.generatedImage;
    }

    public Mat getImage() {
        return generatedImage;
    }
}

/**
 * JFrame para exibir a imagem e permitir salvamento.
 */
class MatrixViewer extends JFrame {
    
    public MatrixViewer(Mat imageMat) {
        setTitle("Gerador de Matriz de Círculos");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Conversão para exibir no Swing
        BufferedImage img = matToBufferedImage(imageMat);
        JLabel imageLabel = new JLabel(new ImageIcon(img));
        
        JScrollPane scrollPane = new JScrollPane(imageLabel);
        scrollPane.setPreferredSize(new Dimension(800, 800));
        add(scrollPane, BorderLayout.CENTER);

        // Botão de Salvamento
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
        fileChooser.setDialogTitle("Salvar Imagem");
        fileChooser.setSelectedFile(new File("matrix_output.png"));
        
        int userSelection = fileChooser.showSaveDialog(this);
        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File fileToSave = fileChooser.getSelectedFile();
            String path = fileToSave.getAbsolutePath();
            if (!path.toLowerCase().endsWith(".png")) {
                path += ".png";
            }
            
            // Salvamento usando OpenCV
            boolean success = Imgcodecs.imwrite(path, mat);
            if (success) {
                JOptionPane.showMessageDialog(this, "Imagem salva com sucesso em:\n" + path);
            } else {
                JOptionPane.showMessageDialog(this, "Erro ao salvar imagem.", "Erro", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // Utilitário de conversão padrão OpenCV -> Java
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