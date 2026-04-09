package ParallelReinfLearningMod;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.List;
import org.opencv.core.*;
import org.opencv.core.Point;
import org.opencv.imgproc.Imgproc;



public class ResultVisualizer extends JFrame {

    public ResultVisualizer(List<StepResult> pipelineSteps, List<Circle> finalCircles, String houghParams, String title) {
        setTitle(title);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // --- PAINEL DE CONTEÚDO (Horizontal) ---
        // Usamos Box Layout no eixo X para colocar quantos painéis forem necessários lado a lado
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.X_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 1. Adiciona cada etapa intermediária (Original -> Filtros)
        for (StepResult step : pipelineSteps) {
            mainPanel.add(createStepPanel(step.image, step.stepName, step.paramsDescription));
            mainPanel.add(Box.createRigidArea(new Dimension(10, 0))); // Espaçamento
        }

        // 2. Cria a etapa FINAL (Detecção de Círculos)
        // Pegamos a última imagem do pipeline para desenhar os círculos sobre ela
        Mat lastImage = pipelineSteps.get(pipelineSteps.size() - 1).image;
        
        // Se a última imagem for Grayscale, converte para BGR para desenhar círculos coloridos
        Mat finalVisualization = new Mat();
        if (lastImage.channels() == 1) {
            Imgproc.cvtColor(lastImage, finalVisualization, Imgproc.COLOR_GRAY2BGR);
        } else {
            lastImage.copyTo(finalVisualization);
        }

        drawCircles(finalVisualization, finalCircles);
        
        // Adiciona o painel final
        mainPanel.add(createStepPanel(finalVisualization, "Resultado Hough", houghParams + " | Count: " + finalCircles.size()));

        // --- SCROLL PANE ---
        // Necessário pois podem haver muitos filtros
        JScrollPane scrollPane = new JScrollPane(mainPanel);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(20);

        add(scrollPane, BorderLayout.CENTER);

        // Configurações da Janela
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private JPanel createStepPanel(Mat mat, String title, String params) {
        JPanel panel = new JPanel(new BorderLayout());
        
        // Borda com Título da Etapa
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), title, TitledBorder.CENTER, TitledBorder.TOP, 
                new Font("Arial", Font.BOLD, 14), Color.BLUE));
        
        // Conversão de Imagem
        BufferedImage img = matToBufferedImage(mat);
        JLabel imageLabel = new JLabel(new ImageIcon(img));
        imageLabel.setHorizontalAlignment(JLabel.CENTER);
        
        // Rótulo de Parâmetros (Embaixo da imagem)
        JLabel paramsLabel = new JLabel("<html><center>" + params + "</center></html>", SwingConstants.CENTER);
        paramsLabel.setFont(new Font("Monospaced", Font.PLAIN, 12));
        paramsLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        paramsLabel.setOpaque(true);
        paramsLabel.setBackground(new Color(240, 240, 240));

        panel.add(imageLabel, BorderLayout.CENTER);
        panel.add(paramsLabel, BorderLayout.SOUTH);
        
        return panel;
    }

    private void drawCircles(Mat img, List<Circle> circles) {
        for (Circle c : circles) {
            Point center = new Point(c.x, c.y);
            Imgproc.circle(img, center, 3, new Scalar(0, 255, 0), -1);
            Imgproc.circle(img, center, (int) c.r, new Scalar(0, 0, 255), 2);
        }
    }

    public static BufferedImage matToBufferedImage(Mat m) {
        if (m == null || m.empty()) return null;
        int type = BufferedImage.TYPE_BYTE_GRAY;
        if (m.channels() > 1) type = BufferedImage.TYPE_3BYTE_BGR;
        int bufferSize = m.channels() * m.cols() * m.rows();
        byte[] b = new byte[bufferSize];
        m.get(0, 0, b);
        BufferedImage image = new BufferedImage(m.cols(), m.rows(), type);
        final byte[] targetPixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        System.arraycopy(b, 0, targetPixels, 0, b.length);
        return image;
    }
}