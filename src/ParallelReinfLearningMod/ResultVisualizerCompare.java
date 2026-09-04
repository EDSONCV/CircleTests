// ResultVisualizerCompare.java
package ParallelReinfLearningMod;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.opencv.core.*;
import org.opencv.core.Point;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import java.io.PrintWriter;

public class ResultVisualizerCompare extends JFrame {

    // Instance variables so we can access them at the time of saving[cite: 3]
    private List<StepResult> pipelineSteps;
    private Mat finalVisualization;
    private List<Circle> finalCircles;
    private String houghParams;
    private List<Circle> groundTruth;
    private static final DecimalFormat df = new DecimalFormat("#0.00");
    // List to store the error metrics that go to the table and CSV file[cite: 3]
    private List<Object[]> metricRows;

    public ResultVisualizerCompare(List<StepResult> pipelineSteps, List<Circle> finalCircles,
                                   List<Circle> groundTruth, String houghParams, String title) {
        this.pipelineSteps = pipelineSteps;
        this.houghParams = houghParams;
        this.finalCircles = finalCircles;
        this.groundTruth = groundTruth;

        setTitle(title);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // --- 1. CENTRAL PANEL (IMAGES) ---[cite: 3]
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.X_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Adds the intermediate steps (Filters)[cite: 3]
        for (StepResult step : pipelineSteps) {
            mainPanel.add(createStepPanel(step.image, step.stepName, step.paramsDescription));
            mainPanel.add(Box.createRigidArea(new Dimension(10, 0)));
        }

        // Final Image[cite: 3]
        Mat lastImage = pipelineSteps.get(pipelineSteps.size() - 1).image;
        finalVisualization = new Mat();
        if (lastImage.channels() == 1) {
            Imgproc.cvtColor(lastImage, finalVisualization, Imgproc.COLOR_GRAY2BGR);
        } else {
            lastImage.copyTo(finalVisualization);
        }

        drawComparison(finalVisualization, finalCircles, groundTruth);
        String finalLabel = String.format("%s | Det: %d / Reais: %d", houghParams, finalCircles.size(), groundTruth.size());
        mainPanel.add(createStepPanel(finalVisualization, "Resultado vs Gabarito", finalLabel));

        JScrollPane imageScrollPane = new JScrollPane(mainPanel);
        imageScrollPane.getHorizontalScrollBar().setUnitIncrement(20);
        add(imageScrollPane, BorderLayout.CENTER);

        // --- 2. METRICS CALCULATION (Detected vs Ground Truth) ---[cite: 3]
        calculateMetrics();

        // --- 3. BOTTOM PANEL (TABLE + BUTTON) ---[cite: 3]
        JPanel bottomContainer = new JPanel(new BorderLayout());
        bottomContainer.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Analytical Table Headers[cite: 3]
        String[] columnNames = {
                "ID",
                "Det_X", "Det_Y", "Det_Raio",
                "Ref_X", "Ref_Y", "Ref_Raio",
                "Erro_X", "Erro_Y", "Erro_Raio",
                "Distância_Euclidiana","IoU"
        };

        DefaultTableModel tableModel = new DefaultTableModel(columnNames, 0);
        JTable resultsTable = new JTable(tableModel);

        // Fills the table with the calculated results[cite: 3]
        for (Object[] row : metricRows) {
            tableModel.addRow(row);
        }

        JScrollPane tableScrollPane = new JScrollPane(resultsTable);
        tableScrollPane.setPreferredSize(new Dimension(getWidth(), 200));

        // Button Panel[cite: 3]
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnSave = new JButton("Salvar Imagens e Exportar CSV");
        btnSave.setFont(new Font("Arial", Font.BOLD, 14));
        btnSave.addActionListener(this::saveDataAction);
        buttonPanel.add(btnSave);

        bottomContainer.add(tableScrollPane, BorderLayout.CENTER);
        bottomContainer.add(buttonPanel, BorderLayout.SOUTH);
        add(bottomContainer, BorderLayout.SOUTH);

        // Window Configurations[cite: 3]
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void saveImagesAction(ActionEvent e) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Selecione a pasta para salvar os resultados");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selectedDirectory = chooser.getSelectedFile();
            String basePath = selectedDirectory.getAbsolutePath() + File.separator;
            try {
                for (int i = 0; i < pipelineSteps.size(); i++) {
                    StepResult step = pipelineSteps.get(i);
                    String safeStepName = step.stepName.replaceAll("[^a-zA-Z0-9.-]", "_");
                    String fileName = String.format("%s%02d_%s.png", basePath, (i + 1), safeStepName);
                    Imgcodecs.imwrite(fileName, step.image);
                }
                String finalFileName = String.format("%s%02d_Resultado_Final.png", basePath, (pipelineSteps.size() + 1));
                Imgcodecs.imwrite(finalFileName, finalVisualization);
                JOptionPane.showMessageDialog(this, "Imagens salvas em:\n" + basePath, "Sucesso", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Erro ao salvar:\n" + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Action triggered when clicking the "Save Images" button.[cite: 3]
     */
    /**
     * Saves the images and exports the analytical table to CSV.[cite: 3]
     */
    private void saveDataAction(ActionEvent e) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Selecione a pasta para guardar as Imagens e os Dados");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selectedDirectory = chooser.getSelectedFile();
            String basePath = selectedDirectory.getAbsolutePath() + File.separator;

            try {
                // 1. Saves Intermediate Images[cite: 3]
                for (int i = 0; i < pipelineSteps.size(); i++) {
                    StepResult step = pipelineSteps.get(i);
                    String safeName = step.stepName.replaceAll("[^a-zA-Z0-9.-]", "_");
                    String fileName = String.format("%s%02d_%s.png", basePath, (i + 1), safeName);
                    Imgcodecs.imwrite(fileName, step.image);
                }

                // 2. Saves Final Comparison Image[cite: 3]
                String finalFileName = String.format("%s%02d_Resultado_Final.png", basePath, (pipelineSteps.size() + 1));
                Imgcodecs.imwrite(finalFileName, finalVisualization);

                // 3. Exports the Analytical Table to the CSV file[cite: 3]
                String csvFileName = basePath + "Metricas_Erro_Circulos.csv";
                try (PrintWriter writer = new PrintWriter(new File(csvFileName))) {
                    // CSV Header[cite: 3]
                    writer.println("ID;Det_X;Det_Y;Det_Raio;Ref_X;Ref_Y;Ref_Raio;Erro_X;Erro_Y;Erro_Raio;Distancia_Euclidiana;IoU");

                    for (Object[] row : metricRows) {
                        // Replaces the European format comma with a dot, and uses a comma as a delimiter[cite: 3]
                        /*String line = String.format("%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s",
                                row[0],
                                row[1].toString().replace(",", "."), row[2].toString().replace(",", "."), row[3].toString().replace(",", "."),
                                row[4].toString().replace(",", "."), row[5].toString().replace(",", "."), row[6].toString().replace(",", "."),
                                row[7].toString().replace(",", "."), row[8].toString().replace(",", "."), row[9].toString().replace(",", "."),
                                row[10].toString().replace(",", ".")
                        );*/
                        String line = String.format("%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s",
                                row[0], row[1], row[2], row[3], row[4], row[5], row[6], row[7], row[8], row[9], row[10],row[11]
                        );
                        writer.println(line);
                    }
                }

                JOptionPane.showMessageDialog(this,
                        "Exportação concluída com sucesso!\n" +
                                "- Imagens guardadas (.png)\n" +
                                "- Dados de métricas exportados (Metricas_Erro_Circulos.csv)\n\n" +
                                "Local: " + basePath,
                        "Sucesso", JOptionPane.INFORMATION_MESSAGE);

            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Ocorreu um erro ao guardar:\n" + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Generates the final image with the requested visual modifications:
     * 1. Center with a solid dot.
     * 2. Thin border.
     * 3. ID in the lower left corner.[cite: 3]
     */
    private Mat prepareFinalImage(Mat source, List<CircleMatch> matches) {
        Mat canvas = new Mat();
        // Ensures the canvas is colored (BGR) to draw in red/green[cite: 3]
        if (source.channels() == 1) Imgproc.cvtColor(source, canvas, Imgproc.COLOR_GRAY2BGR);
        else source.copyTo(canvas);

        // A. Draws Ground Truth (Green) - Border only for reference[cite: 3]
        for (CircleMatch m : matches) {
            if (m.gt != null) {
                Imgproc.circle(canvas, new Point(m.gt.x, m.gt.y), (int)m.gt.r, new Scalar(0, 255, 0), 1);
            }
        }

        // B. Draws Detected (Red) with the new rules[cite: 3]
        for (CircleMatch m : matches) {
            if (m.det != null) {
                Point center = new Point(m.det.x, m.det.y);
                int radius = (int) m.det.r;
                Scalar color = new Scalar(0, 0, 255); // Vermelho

                // REQUIREMENT 1: Center with a dot (not X)[cite: 3]
                // thickness = -1 means FILLED[cite: 3]
                Imgproc.circle(canvas, center, 2, color, -1);

                // REQUIREMENT 2: Thin stroke[cite: 3]
                // thickness = 1[cite: 3]
                Imgproc.circle(canvas, center, radius, color, 1);

                // REQUIREMENT 3: ID on the lower left side[cite: 3]
                // We calculate the position: X = (CenterX - Radius), Y = (CenterY + Radius + margin)[cite: 3]
                Point textPos = new Point(m.det.x - radius, m.det.y + radius + 15);

                String label = "ID:" + m.id;

                // Small font (0.4)[cite: 3]
                Imgproc.putText(canvas, label, textPos,
                        Imgproc.FONT_HERSHEY_SIMPLEX, 0.4, color, 1);
            }
        }

        // Simple legend at the top[cite: 3]
        Imgproc.putText(canvas, "Verde: Gabarito | Vermelho: Detectado", new Point(10, 20),
                Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, new Scalar(255,255,255), 4); // White outline[cite: 3]
        Imgproc.putText(canvas, "Verde: Gabarito | Vermelho: Detectado", new Point(10, 20),
                Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, new Scalar(0,0,0), 1);       // Black text[cite: 3]

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

    private JPanel createStepPanel(Mat mat, String title, String params) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), title, TitledBorder.CENTER, TitledBorder.TOP,
                new Font("Arial", Font.BOLD, 14), Color.BLUE));
        BufferedImage img = matToBufferedImage(mat);
        JLabel imageLabel = new JLabel(new ImageIcon(img));
        imageLabel.setHorizontalAlignment(JLabel.CENTER);
        JLabel paramsLabel = new JLabel("<html><center>" + params + "</center></html>", SwingConstants.CENTER);
        paramsLabel.setFont(new Font("Monospaced", Font.PLAIN, 12));
        paramsLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        panel.add(imageLabel, BorderLayout.CENTER);
        panel.add(paramsLabel, BorderLayout.SOUTH);
        return panel;
    }

    private List<CircleMatch> matchAndSortCircles(List<Circle> detected, List<Circle> truth) {
        // (The matching logic remains IDENTICAL to the previous response)[cite: 3]
        // I will omit it here to save space, but it should be copied from the previous response[cite: 3]
        // Remember: Sorts GT, pairs by minimum distance, marks Noises.[cite: 3]

        // --- COPY matchAndSortCircles METHOD FROM PREVIOUS RESPONSE ---[cite: 3]
        List<CircleMatch> results = new ArrayList<>();
        List<Circle> detCopy = new ArrayList<>(detected);
        List<Circle> gtCopy = new ArrayList<>(truth);
        gtCopy.sort(Comparator.comparingDouble(c -> c.x)); // Sorts GT by X[cite: 3]

        int idCounter = 1;

        // 1. Matches of GT with Detected[cite: 3]
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

        // 2. Leftovers (False Positives)[cite: 3]
        for (Circle noise : detCopy) {
            CircleMatch fp = new CircleMatch();
            fp.id = idCounter++;
            fp.det = noise;
            fp.status = "Ruído";
            results.add(fp);
        }

        // 3. Final Sorting[cite: 3]
        results.sort((m1, m2) -> {
            double x1 = (m1.gt != null) ? m1.gt.x : m1.det.x;
            double x2 = (m2.gt != null) ? m2.gt.x : m2.det.x;
            return Double.compare(x1, x2);
        });

        return results;
    }

    /**
     * Draws the Ground Truth in Blue and the Detected ones in Red.[cite: 3]
     */
    private void drawComparison(Mat img, List<Circle> detected, List<Circle> groundTruth) {
        // 1. Draws the Ground Truth first (Blue) - will be underneath[cite: 3]
        for (Circle gt : groundTruth) {
            Point center = new Point(gt.x, gt.y);
            // Draws only the outline, visual dashed or thin line[cite: 3]
            Imgproc.circle(img, center, (int) gt.r, new Scalar(255, 0, 0), 2); // BGR: Blue[cite: 3]
            // System.out.println("gt inside ResultVisualizer " + "cx: " + gt.x +" cy: " + gt.y + " radius : " + gt.r );
        }

        // 2. Draws those detected by the algorithm on top (Red and Green)[cite: 3]
        for (Circle c : detected) {
            Point center = new Point(c.x, c.y);
            Imgproc.circle(img, center, 3, new Scalar(0, 255, 0), -1); // Green Center[cite: 3]
            Imgproc.circle(img, center, (int) c.r, new Scalar(0, 0, 255), 2); // Red Outline[cite: 3]
        }
    }

    // Auxiliary class CircleMatch (also copy the metrics logic from the previous response)[cite: 3]
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

    /**
     * Calculates the individual error of each detected circle by mapping it
     * to the closest circle in the Ground Truth.[cite: 3]
     */
    private void calculateMetrics() {
        metricRows = new ArrayList<>();
        for (int i = 0; i < finalCircles.size(); i++) {
            Circle det = finalCircles.get(i);
            Circle bestMatch = null;
            double maxIou = 0.0;
            double distAtMaxIou = 0.0;

            // Finds the Ground Truth by the HIGHEST IoU[cite: 3]
            for (Circle gt : groundTruth) {
                double iou = det.getIoU(gt);
                if (iou > maxIou) {
                    maxIou = iou;
                    bestMatch = gt;
                    distAtMaxIou = Math.sqrt(Math.pow(det.x - gt.x, 2) + Math.pow(det.y - gt.y, 2));
                }
            }

            if (bestMatch != null) {
                double errX = Math.abs(det.x - bestMatch.x);
                double errY = Math.abs(det.y - bestMatch.y);
                double errR = Math.abs(det.r - bestMatch.r);

                // <--- NEW IoU COLUMN[cite: 3]
                metricRows.add(new Object[]{
                        (i + 1),
                        String.format("%.1f", det.x), String.format("%.1f", det.y), String.format("%.1f", det.r),
                        String.format("%.1f", bestMatch.x), String.format("%.1f", bestMatch.y), String.format("%.1f", bestMatch.r),
                        String.format("%.2f", errX), String.format("%.2f", errY), String.format("%.2f", errR),
                        String.format("%.2f", distAtMaxIou),
                        String.format("%.4f", maxIou)
                });
            }
        }
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