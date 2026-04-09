package ParallelReinfLearningMod;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.opencv.core.*;
import org.opencv.core.Point;
import org.opencv.imgproc.Imgproc;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

public class ResultVisualizerCompare extends JFrame {

    private static final DecimalFormat df = new DecimalFormat("#0.00");

    public ResultVisualizerCompare(List<StepResult> pipelineSteps, 
                            List<Circle> detectedCircles, 
                            List<Circle> groundTruth, 
                            String houghParams, 
                            String title) {
        setTitle(title);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // --- 0. PROCESSAMENTO DE DADOS (MATCHING) ---
        // Realizamos o pareamento AGORA para garantir que os IDs da imagem
        // sejam idênticos aos IDs da tabela.
        List<CircleMatch> matches = matchAndSortCircles(detectedCircles, groundTruth);

        // --- 1. PAINEL SUPERIOR: IMAGENS ---
        JPanel imagesPanel = new JPanel();
        imagesPanel.setLayout(new BoxLayout(imagesPanel, BoxLayout.X_AXIS));
        imagesPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Adiciona etapas intermediárias do pipeline
        for (StepResult step : pipelineSteps) {
            imagesPanel.add(createStepPanel(step.image, step.stepName, step.paramsDescription));
            imagesPanel.add(Box.createRigidArea(new Dimension(10, 0)));
        }

        // Adiciona Resultado Final Desenhado
        Mat lastImage = pipelineSteps.get(pipelineSteps.size() - 1).image;
        
        // Passamos a lista de 'matches' já calculada para desenhar os IDs corretamente
        Mat finalVisualization = prepareFinalImage(lastImage, matches);
        
        imagesPanel.add(createStepPanel(finalVisualization, "Comparativo Visual", 
                houghParams + " | Det: " + detectedCircles.size() + " / Real: " + groundTruth.size()));

        JScrollPane scrollImages = new JScrollPane(imagesPanel);
        scrollImages.setPreferredSize(new Dimension(1200, 450));
        
        // --- 2. PAINEL INFERIOR: TABELA ---
        // Passamos a mesma lista de matches para gerar a tabela
        JPanel analysisPanel = createAnalysisPanel(matches);

        // --- MONTAGEM FINAL ---
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, scrollImages, analysisPanel);
        splitPane.setResizeWeight(0.65);
        
        add(splitPane, BorderLayout.CENTER);

        setExtendedState(JFrame.MAXIMIZED_BOTH);
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    /**
     * Gera a imagem final com as modificações visuais solicitadas:
     * 1. Centro com ponto sólido.
     * 2. Borda fina.
     * 3. ID no canto inferior esquerdo.
     */
    private Mat prepareFinalImage(Mat source, List<CircleMatch> matches) {
        Mat canvas = new Mat();
        // Garante que o canvas seja colorido (BGR) para desenharmos em vermelho/verde
        if (source.channels() == 1) Imgproc.cvtColor(source, canvas, Imgproc.COLOR_GRAY2BGR);
        else source.copyTo(canvas);

        // A. Desenha Ground Truth (Verde) - Apenas borda para referência
        for (CircleMatch m : matches) {
            if (m.gt != null) {
                Imgproc.circle(canvas, new Point(m.gt.x, m.gt.y), (int)m.gt.r, new Scalar(0, 255, 0), 1);
            }
        }

        // B. Desenha Detectados (Vermelho) com as novas regras
        for (CircleMatch m : matches) {
            if (m.det != null) {
                Point center = new Point(m.det.x, m.det.y);
                int radius = (int) m.det.r;
                Scalar color = new Scalar(0, 0, 255); // Vermelho

                // REQUISITO 1: Centro com ponto (não X)
                // thickness = -1 significa preenchido (FILLED)
                Imgproc.circle(canvas, center, 2, color, -1); 

                // REQUISITO 2: Traço fino
                // thickness = 1
                Imgproc.circle(canvas, center, radius, color, 1);

                // REQUISITO 3: ID do lado esquerdo inferior
                // Calculamos a posição: X = (CentroX - Raio), Y = (CentroY + Raio + margem)
                Point textPos = new Point(m.det.x - radius, m.det.y + radius + 15);
                
                String label = "ID:" + m.id;
                
                // Fonte pequena (0.4)
                Imgproc.putText(canvas, label, textPos, 
                        Imgproc.FONT_HERSHEY_SIMPLEX, 0.4, color, 1);
            }
        }
        
        // Legenda simples no topo
        Imgproc.putText(canvas, "Verde: Gabarito | Vermelho: Detectado", new Point(10, 20), 
                Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, new Scalar(255,255,255), 4); // Contorno branco
        Imgproc.putText(canvas, "Verde: Gabarito | Vermelho: Detectado", new Point(10, 20), 
                Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, new Scalar(0,0,0), 1);       // Texto preto

        return canvas;
    }

    private JPanel createAnalysisPanel(List<CircleMatch> matches) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Análise Quantitativa"));

        String[] columnNames = { "ID", "Status", "GT (X, Y, R)", "Det (X, Y, R)", "Δ Centro", "Δ Raio", "Erro Raio %", "IoU" };
        DefaultTableModel model = new DefaultTableModel(columnNames, 0);

        for (CircleMatch m : matches) {
            Object[] row = new Object[8];
            row[0] = m.id;
            row[1] = m.status;
            row[2] = (m.gt != null) ? String.format("%.2f, %.2f, %.2f", m.gt.x, m.gt.y, m.gt.r) : "-";
            row[3] = (m.det != null) ? String.format("%.2f, %.2f, %.2f", m.det.x, m.det.y, m.det.r) : "-";

            if (m.isMatched()) {
                row[4] = df.format(m.centerError);
                row[5] = df.format(m.radiusErrorAbs);
                row[6] = df.format(m.radiusErrorPct) + "%";
                row[7] = df.format(m.iou);
            } else {
                row[4] = "-"; row[5] = "-"; row[6] = "-"; row[7] = "-";
            }
            model.addRow(row);
        }

        JTable table = new JTable(model);
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        for(int x=0; x<columnNames.length; x++) table.getColumnModel().getColumn(x).setCellRenderer(centerRenderer);

        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    private List<CircleMatch> matchAndSortCircles(List<Circle> detected, List<Circle> truth) {
        // (A lógica de matching permanece IDÊNTICA à resposta anterior)
        // Vou omitir aqui para economizar espaço, mas deve ser copiada da resposta anterior
        // Lembre-se: Ordena GT, parea por distância mínima, marca Ruídos.
        
        // --- COPIAR MÉTODO matchAndSortCircles DA RESPOSTA ANTERIOR ---
        List<CircleMatch> results = new ArrayList<>();
        List<Circle> detCopy = new ArrayList<>(detected);
        List<Circle> gtCopy = new ArrayList<>(truth);
        gtCopy.sort(Comparator.comparingDouble(c -> c.x)); // Ordena GT por X

        int idCounter = 1;

        // 1. Matches de GT com Detectados
        for (Circle gt : gtCopy) {
            Circle bestMatch = null;
            double minDist = Double.MAX_VALUE;
            int bestIndex = -1;

            for (int i = 0; i < detCopy.size(); i++) {
                Circle det = detCopy.get(i);
                double dist = Math.hypot(gt.x - det.x, gt.y - det.y);
                if (dist < 50 && dist < minDist) {
                    minDist = dist; bestMatch = det; bestIndex = i;
                }
            }

            CircleMatch match = new CircleMatch();
            match.id = idCounter++;
            match.gt = gt;

            if (bestMatch != null) {
                match.det = bestMatch;
                match.status = "Detectado";
                match.calculateMetrics();
                detCopy.remove(bestIndex);
            } else {
                match.status = "Falso Negativo";
            }
            results.add(match);
        }

        // 2. Sobras (Falsos Positivos)
        for (Circle noise : detCopy) {
            CircleMatch fp = new CircleMatch();
            fp.id = idCounter++;
            fp.det = noise;
            fp.status = "Ruído";
            results.add(fp);
        }

        // 3. Ordenação Final
        results.sort((m1, m2) -> {
            double x1 = (m1.gt != null) ? m1.gt.x : m1.det.x;
            double x2 = (m2.gt != null) ? m2.gt.x : m2.det.x;
            return Double.compare(x1, x2);
        });

        return results;
    }

    // Classe auxiliar CircleMatch (copie também a lógica de métricas da resposta anterior)
    private class CircleMatch {
        int id; String status; Circle gt; Circle det;
        double centerError, radiusErrorAbs, radiusErrorPct, iou;
        boolean isMatched() { return gt != null && det != null; }
        void calculateMetrics() {
             if (!isMatched()) return;
             this.centerError = Math.hypot(gt.x - det.x, gt.y - det.y);
             this.radiusErrorAbs = Math.abs(gt.r - det.r);
             if (gt.r > 0) this.radiusErrorPct = (this.radiusErrorAbs / gt.r) * 100.0;
             double intersectionR = Math.min(gt.r, det.r) - (centerError / 2.0);
             if (intersectionR < 0) intersectionR = 0;
             double areaGt = Math.PI * gt.r * gt.r;
             double areaDet = Math.PI * det.r * det.r;
             double areaInter = Math.PI * intersectionR * intersectionR; 
             this.iou = areaInter / (areaGt + areaDet - areaInter);
        }
    }

    private JPanel createStepPanel(Mat mat, String title, String params) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), title, TitledBorder.CENTER, TitledBorder.TOP));
        BufferedImage img = matToBufferedImage(mat);
        panel.add(new JLabel(new ImageIcon(img)), BorderLayout.CENTER);
        JLabel lbl = new JLabel("<html><center>" + params + "</center></html>", SwingConstants.CENTER);
        lbl.setOpaque(true); lbl.setBackground(new Color(240,240,240));
        panel.add(lbl, BorderLayout.SOUTH);
        return panel;
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