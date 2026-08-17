package CirclesGenerators;

import org.opencv.core.*;
import org.opencv.core.Point;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class AdvancedModularCircleDetectorApp extends JFrame {

    private static final long serialVersionUID = 1L;

    // --- CLASSES MODULARES INTERNAS ---

    // 1. Classe para Parâmetros
    class FilterParam {
        String name; double value, min, max;
        JTextField textField; // Ligação direta com a GUI

        public FilterParam(String name, double value, double min, double max) {
            this.name = name; this.value = value; this.min = min; this.max = max;
        }
        // Lê o valor do JTextField digitado pelo usuário
        public void updateFromUI() {
            try {
                double val = Double.parseDouble(textField.getText().replace(",", "."));
                this.value = Math.max(min, Math.min(max, val)); // Limita entre min e max
                textField.setText(String.valueOf(this.value)); // Atualiza a UI com o valor filtrado
            } catch (Exception e) {
                textField.setText(String.valueOf(this.value)); // Reverte se der erro
            }
        }
        public String toString() { return name + "=" + value; }
    }

    // 2. Interface de Filtros
    interface ImageFilter {
        String getName();
        List<FilterParam> getParams();
        Mat process(Mat input);
    }

    // 3. Resultado de cada etapa
    class StepResult {
        String stepName, paramsDescription;
        Mat image;
        public StepResult(String name, String params, Mat img) {
            this.stepName = name; this.paramsDescription = params; this.image = img.clone();
        }
    }

    // --- IMPLEMENTAÇÃO DE FILTROS ---

    class GaussianBlurFilter implements ImageFilter {
        FilterParam kernel = new FilterParam("Kernel", 3, 1, 31);
        FilterParam sigma = new FilterParam("Sigma", 1.5, 0.1, 10.0);
        @Override public String getName() { return "Gaussian Blur"; }
        @Override public List<FilterParam> getParams() { return List.of(kernel, sigma); }
        @Override public Mat process(Mat input) {
            Mat output = new Mat();
            int k = (int) kernel.value;
            if (k % 2 == 0) k++; // Garante kernel ímpar
            Imgproc.GaussianBlur(input, output, new Size(k, k), sigma.value);
            return output;
        }
    }

    class CLAHEFilter implements ImageFilter {
        FilterParam clipLimit = new FilterParam("Clip Limit", 2.0, 0.1, 10.0);
        FilterParam gridSize = new FilterParam("Grid Size", 8, 2, 32);
        @Override public String getName() { return "CLAHE"; }
        @Override public List<FilterParam> getParams() { return List.of(clipLimit, gridSize); }
        @Override public Mat process(Mat input) {
            Mat output = new Mat();
            org.opencv.imgproc.CLAHE clahe = Imgproc.createCLAHE(clipLimit.value, new Size(gridSize.value, gridSize.value));
            clahe.apply(input, output);
            return output;
        }
    }

    // --- VARIÁVEIS DA APLICAÇÃO ---

    private Mat originalImage;
    private List<StepResult> pipelineHistory = new ArrayList<>();
    private List<ImageFilter> filterPipeline = new ArrayList<>();
    private List<FilterParam> houghParams = new ArrayList<>();

    // Componentes GUI
    private JPanel imagesContainerPanel;
    private JTable resultsTable;
    private DefaultTableModel tableModel;
    private JFileChooser fileChooser;

    public AdvancedModularCircleDetectorApp() {
        setTitle("Detector Modular Avançado de Círculos");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1300, 800);
        setLayout(new BorderLayout());

        setupPipelineAndParams();
        initializeUI();
    }

    /**
     * CADASTRO DE FILTROS E PARÂMETROS
     * Modifique esta função para adicionar/remover filtros da interface.
     */
    private void setupPipelineAndParams() {
        // 1. Cadastra os Filtros do Pipeline
        //filterPipeline.add(new CLAHEFilter());
        filterPipeline.add(new GaussianBlurFilter());
        // Adicione Bilateral, Canny, Morphological aqui futuramente...

        // 2. Parâmetros do Hough Transform
        houghParams.add(new FilterParam("dp", 1.0, 0.1, 5.0));
        houghParams.add(new FilterParam("minDist", 50, 1, 1000));
        houghParams.add(new FilterParam("param1 (Canny)", 100, 10, 300));
        houghParams.add(new FilterParam("param2 (Accum)", 0.1, 0.1, 0.9));
        houghParams.add(new FilterParam("minRadius", 10, 1, 1000));
        houghParams.add(new FilterParam("maxRadius", 100, 1, 2000));
    }

    private void initializeUI() {
        // --- 1. PAINEL LATERAL ESQUERDO: Parâmetros Dinâmicos ---
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Botões de Ação Superiores
        JPanel actionPanel = new JPanel(new GridLayout(3, 1, 5, 5));
        JButton btnLoad = new JButton("1. Carregar Imagem");
        btnLoad.addActionListener(this::loadImageAction);
        JButton btnDetect = new JButton("2. Rodar Detecção");
        btnDetect.addActionListener(this::detectCirclesAction);
        JButton btnExport = new JButton("3. Exportar Resultados");
        btnExport.addActionListener(this::exportAction);

        actionPanel.add(btnLoad);
        actionPanel.add(btnDetect);
        actionPanel.add(btnExport);
        leftPanel.add(actionPanel);
        leftPanel.add(Box.createRigidArea(new Dimension(0, 15)));

        // Seção: Filtros Dinâmicos
        for (ImageFilter filter : filterPipeline) {
            JPanel filterPanel = createParamGroupPanel(filter.getName(), filter.getParams());
            leftPanel.add(filterPanel);
            leftPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        }

        // Seção: Parâmetros do Hough
        JPanel houghPanel = createParamGroupPanel("Hough Circles", houghParams);
        leftPanel.add(houghPanel);

        JScrollPane scrollLeftPanel = new JScrollPane(leftPanel);
        scrollLeftPanel.setPreferredSize(new Dimension(250, 0));
        scrollLeftPanel.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollLeftPanel, BorderLayout.WEST);

        // --- 2. PAINEL CENTRAL: Imagens lado a lado ---
        imagesContainerPanel = new JPanel();
        imagesContainerPanel.setLayout(new BoxLayout(imagesContainerPanel, BoxLayout.X_AXIS));
        imagesContainerPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JScrollPane imagesScrollPane = new JScrollPane(imagesContainerPanel);
        imagesScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        imagesScrollPane.getHorizontalScrollBar().setUnitIncrement(20);
        add(imagesScrollPane, BorderLayout.CENTER);

        // --- 3. PAINEL INFERIOR: Tabela de Resultados ---
        String[] columnNames = {"ID", "Centro X (px)", "Centro Y (px)", "Raio (px)"};
        tableModel = new DefaultTableModel(columnNames, 0);
        resultsTable = new JTable(tableModel);

        JScrollPane tableScrollPane = new JScrollPane(resultsTable);
        tableScrollPane.setPreferredSize(new Dimension(1000, 150));
        tableScrollPane.setBorder(BorderFactory.createTitledBorder("Círculos Detectados"));

        add(tableScrollPane, BorderLayout.SOUTH);

        fileChooser = new JFileChooser();
    }

    /**
     * Cria dinamicamente um painel de parâmetros (Label + TextField)
     */
    private JPanel createParamGroupPanel(String title, List<FilterParam> params) {
        JPanel panel = new JPanel(new GridLayout(params.size(), 2, 5, 5));
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), title, TitledBorder.LEFT, TitledBorder.TOP,
                new Font("Arial", Font.BOLD, 12), Color.BLUE));

        for (FilterParam p : params) {
            panel.add(new JLabel(p.name + ":"));
            p.textField = new JTextField(String.valueOf(p.value));
            panel.add(p.textField);
        }
        return panel;
    }

    // --- AÇÕES DOS BOTÕES ---

    private void loadImageAction(ActionEvent e) {
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            originalImage = Imgcodecs.imread(selectedFile.getAbsolutePath());

            if (originalImage.empty()) {
                JOptionPane.showMessageDialog(this, "Erro ao carregar imagem.", "Erro", JOptionPane.ERROR_MESSAGE);
                return;
            }

            imagesContainerPanel.removeAll();
            imagesContainerPanel.add(createImagePanel(originalImage, "Original", "Imagem carregada"));
            imagesContainerPanel.revalidate();
            imagesContainerPanel.repaint();
            tableModel.setRowCount(0);
        }
    }

    private void detectCirclesAction(ActionEvent e) {
        if (originalImage == null || originalImage.empty()) {
            JOptionPane.showMessageDialog(this, "Carregue uma imagem primeiro.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }

        pipelineHistory.clear();
        imagesContainerPanel.removeAll();
        tableModel.setRowCount(0);

        try {
            // 1. Início do Pipeline (Força tons de cinza)
            Mat currentMat = new Mat();
            if (originalImage.channels() > 1) {
                Imgproc.cvtColor(originalImage, currentMat, Imgproc.COLOR_BGR2GRAY);
            } else {
                originalImage.copyTo(currentMat);
            }

            pipelineHistory.add(new StepResult("0_Escala_de_Cinza", "Conversão BGR2GRAY", currentMat));

            // 2. Aplica Filtros Dinâmicos Sequencialmente
            for (ImageFilter filter : filterPipeline) {
                // Atualiza os valores lendo dos JTextFields
                for(FilterParam p : filter.getParams()) p.updateFromUI();

                currentMat = filter.process(currentMat);

                // Formata os parâmetros para exibir debaixo da imagem
                String paramsStr = filter.getParams().toString().replace("[", "").replace("]", "");
                pipelineHistory.add(new StepResult(filter.getName(), paramsStr, currentMat));
            }

            // 3. Atualiza os parâmetros do Hough a partir da UI
            for(FilterParam p : houghParams) p.updateFromUI();

            double dp = houghParams.get(0).value;
            double minDist = houghParams.get(1).value;
            double p1 = houghParams.get(2).value;
            double p2 = houghParams.get(3).value;
            int minR = (int) houghParams.get(4).value;
            int maxR = (int) houghParams.get(5).value;

            // 4. Executa Hough Circles
            Mat circles = new Mat();
            Imgproc.HoughCircles(currentMat, circles, Imgproc.HOUGH_GRADIENT_ALT, dp, minDist, p1, p2, minR, maxR);

            // 5. Gera Imagem Final (Colorida para desenhar os círculos vermelhos)
            Mat finalImage = new Mat();
            Imgproc.cvtColor(currentMat, finalImage, Imgproc.COLOR_GRAY2BGR);

            for (int i = 0; i < circles.cols(); i++) {
                double[] c = circles.get(0, i);
                Point center = new Point(Math.round(c[0]), Math.round(c[1]));
                int radius = (int) Math.round(c[2]);

                Imgproc.circle(finalImage, center, 3, new Scalar(0, 255, 0), -1); // Centro verde
                Imgproc.circle(finalImage, center, radius, new Scalar(0, 0, 255), 2); // Borda vermelha

                tableModel.addRow(new Object[]{i + 1, Math.round(c[0]), Math.round(c[1]), radius});
            }

            String houghParamsStr = houghParams.toString().replace("[", "").replace("]", "");
            pipelineHistory.add(new StepResult("Resultado_Final", "Detecção: " + circles.cols() + " círculos | " + houghParamsStr, finalImage));

            // 6. Atualiza a UI com todas as imagens do Histórico
            for (StepResult step : pipelineHistory) {
                imagesContainerPanel.add(createImagePanel(step.image, step.stepName, step.paramsDescription));
                imagesContainerPanel.add(Box.createRigidArea(new Dimension(10, 0)));
            }

            imagesContainerPanel.revalidate();
            imagesContainerPanel.repaint();

        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Erro no processamento:\n" + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void exportAction(ActionEvent e) {
        if (pipelineHistory.isEmpty() || tableModel.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this, "Não há resultados para exportar. Rode a detecção primeiro.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser exportChooser = new JFileChooser();
        exportChooser.setDialogTitle("Selecione a pasta para exportação");
        exportChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        if (exportChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            String basePath = exportChooser.getSelectedFile().getAbsolutePath() + File.separator;

            try {
                // Exporta Imagens
                for (int i = 0; i < pipelineHistory.size(); i++) {
                    StepResult step = pipelineHistory.get(i);
                    String safeName = step.stepName.replaceAll("[^a-zA-Z0-9.-]", "_");
                    String filename = String.format("%s%02d_%s.png", basePath, i, safeName);
                    Imgcodecs.imwrite(filename, step.image);
                }

                // Exporta CSV
                String csvPath = basePath + "Circulos_Detectados.csv";
                try (PrintWriter writer = new PrintWriter(new File(csvPath))) {
                    writer.println("ID,Centro_X,Centro_Y,Raio");
                    for (int i = 0; i < tableModel.getRowCount(); i++) {
                        writer.printf("%s,%s,%s,%s\n",
                                tableModel.getValueAt(i, 0), tableModel.getValueAt(i, 1),
                                tableModel.getValueAt(i, 2), tableModel.getValueAt(i, 3));
                    }
                }

                JOptionPane.showMessageDialog(this, "Exportação concluída em:\n" + basePath, "Sucesso", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Erro ao exportar:\n" + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // --- MÉTODOS AUXILIARES ---

    private JPanel createImagePanel(Mat mat, String title, String params) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), title, TitledBorder.CENTER, TitledBorder.TOP,
                new Font("Arial", Font.BOLD, 14), Color.BLUE));

        BufferedImage img = matToBufferedImage(mat);
        JLabel imageLabel = new JLabel(new ImageIcon(img));
        imageLabel.setHorizontalAlignment(JLabel.CENTER);

        JLabel paramsLabel = new JLabel("<html><center>" + params + "</center></html>", SwingConstants.CENTER);
        paramsLabel.setFont(new Font("Monospaced", Font.PLAIN, 11));
        paramsLabel.setBorder(new EmptyBorder(5, 5, 5, 5));

        panel.add(imageLabel, BorderLayout.CENTER);
        panel.add(paramsLabel, BorderLayout.SOUTH);
        return panel;
    }

    public static BufferedImage matToBufferedImage(Mat m) {
        if (m == null || m.empty()) return null;
        int type = m.channels() > 1 ? BufferedImage.TYPE_3BYTE_BGR : BufferedImage.TYPE_BYTE_GRAY;
        int bufferSize = m.channels() * m.cols() * m.rows();
        byte[] b = new byte[bufferSize];
        m.get(0, 0, b);
        BufferedImage image = new BufferedImage(m.cols(), m.rows(), type);
        final byte[] targetPixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        System.arraycopy(b, 0, targetPixels, 0, b.length);
        return image;
    }

    public static void main(String[] args) {
        // Carrega DLL Nativa do OpenCV (Ajuste o caminho conforme o seu setup anterior)
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

        SwingUtilities.invokeLater(() -> {
            new AdvancedModularCircleDetectorApp().setVisible(true);
        });
    }
}