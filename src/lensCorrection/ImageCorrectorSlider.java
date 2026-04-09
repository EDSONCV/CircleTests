package lensCorrection;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ImageCorrectorSlider extends JFrame {



    private static File lastDirectory = new File(System.getProperty("user.home"));

    // Dados (Mat)
    private Mat matOriginal, matFlatField, matResultFlat, matResultNumerical;

    // Componentes da GUI
    private JLabel lblImageOriginal, lblImageFlat, lblResultFlat, lblResultNumerical;
    private JTextField txtPathOriginal, txtPathFlat;
    
    // Sliders e Labels de Valor
    private JSlider sliderBlur, sliderK;
    private JLabel lblBlurValue, lblKValue;
    
    // Constante para converter int do slider para float do K
    private static final double K_SCALE_FACTOR = 100.0; 

    public ImageCorrectorSlider() {
        setTitle("ImageJ(ava) Corrector v4 - Sliders Control");
        setSize(1400, 850);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // --- PAINEL SUPERIOR (Controles) ---
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // 1. Linha de Arquivos
        JPanel filePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        txtPathOriginal = new JTextField(15);
        txtPathFlat = new JTextField(15);
        JButton btnLoadOriginal = new JButton("Carregar Imagem");
        JButton btnLoadFlat = new JButton("Carregar Flat-Field");
        
        filePanel.add(new JLabel("Imagem:"));
        filePanel.add(txtPathOriginal);
        filePanel.add(btnLoadOriginal);
        filePanel.add(Box.createHorizontalStrut(15));
        filePanel.add(new JLabel("Flat-Field:"));
        filePanel.add(txtPathFlat);
        filePanel.add(btnLoadFlat);

        // 2. Linha de Controle Flat-Field (Slider Blur)
        JPanel flatControlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        flatControlPanel.setBorder(BorderFactory.createTitledBorder("1. Parâmetros Flat-Field"));
        
        sliderBlur = new JSlider(0, 100, 20); // 0 a 100 px
        sliderBlur.setMajorTickSpacing(20);
        sliderBlur.setPaintTicks(true);
        lblBlurValue = new JLabel("Sigma: 20");
        lblBlurValue.setPreferredSize(new Dimension(80, 20)); // Tamanho fixo para não pular
        
        // Listener do Blur
        sliderBlur.addChangeListener(e -> {
            JSlider source = (JSlider)e.getSource();
            lblBlurValue.setText("Sigma: " + source.getValue());
            // SÓ PROCESSA SE SOLTAR O MOUSE
            if (!source.getValueIsAdjusting()) {
                processFlatField();
                // Se já tivermos resultado numérico, precisamos reprocessá-lo em cascata?
                // Opcional: Se quiser atualizar tudo em cadeia, chame processNumericalCorrection() aqui também.
                if (matResultNumerical != null) processNumericalCorrection();
            }
        });

        flatControlPanel.add(new JLabel("Blur (Suavização):"));
        flatControlPanel.add(sliderBlur);
        flatControlPanel.add(lblBlurValue);

        // 3. Linha de Controle Numérico (Slider K)
        JPanel numControlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        numControlPanel.setBorder(BorderFactory.createTitledBorder("2. Parâmetros Numéricos"));
        
        // Slider para K: 0 a 500 (Representando 0.00 a 5.00)
        sliderK = new JSlider(0, 500, 50); 
        sliderK.setMajorTickSpacing(100);
        sliderK.setPaintTicks(true);
        lblKValue = new JLabel("K: 0.50");
        lblKValue.setPreferredSize(new Dimension(80, 20));

        // Listener do K
        sliderK.addChangeListener(e -> {
            JSlider source = (JSlider)e.getSource();
            double val = source.getValue() / K_SCALE_FACTOR;
            lblKValue.setText(String.format("K: %.2f", val));
            
            if (!source.getValueIsAdjusting()) {
                processNumericalCorrection();
            }
        });

        numControlPanel.add(new JLabel("Força K (Vinheta):"));
        numControlPanel.add(sliderK);
        numControlPanel.add(lblKValue);

        // Adiciona sub-painéis ao topo
        topPanel.add(filePanel);
        topPanel.add(flatControlPanel);
        topPanel.add(numControlPanel);
        add(topPanel, BorderLayout.NORTH);

        // --- PAINEL CENTRAL (Visualização) ---
        JPanel centerPanel = new JPanel(new GridLayout(1, 4, 10, 0));
        
        lblImageOriginal = new JLabel();
        lblImageFlat = new JLabel();
        lblResultFlat = new JLabel();
        lblResultNumerical = new JLabel();

        centerPanel.add(createImagePanel("Original", lblImageOriginal));
        centerPanel.add(createImagePanel("Flat-Field", lblImageFlat));
        centerPanel.add(createImagePanel("Res. Flat-Field", lblResultFlat));
        centerPanel.add(createImagePanel("Res. Numérico", lblResultNumerical));

        add(centerPanel, BorderLayout.CENTER);

        // --- PAINEL INFERIOR (Salvar) ---
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnSaveFlatRes = new JButton("Salvar Resultado Flat");
        JButton btnSaveNumRes = new JButton("Salvar Resultado Numérico");
        
        bottomPanel.add(btnSaveFlatRes);
        bottomPanel.add(btnSaveNumRes);
        add(bottomPanel, BorderLayout.SOUTH);

        // --- AÇÕES DOS BOTÕES DE ARQUIVO ---
        btnLoadOriginal.addActionListener(e -> {
            File f = chooseFile();
            if (f != null) {
                txtPathOriginal.setText(f.getAbsolutePath());
                matOriginal = Imgcodecs.imread(f.getAbsolutePath());
                displayImage(matOriginal, lblImageOriginal);
                // Reseta resultados anteriores
                matResultFlat = null; matResultNumerical = null;
                lblResultFlat.setIcon(null); lblResultNumerical.setIcon(null);
            }
        });

        btnLoadFlat.addActionListener(e -> {
            File f = chooseFile();
            if (f != null) {
                txtPathFlat.setText(f.getAbsolutePath());
                matFlatField = Imgcodecs.imread(f.getAbsolutePath());
                displayImage(matFlatField, lblImageFlat);
                // Trigger automático se já tivermos a original?
                // processFlatField(); // Opcional
            }
        });

        btnSaveFlatRes.addActionListener(e -> saveImage(matResultFlat));
        btnSaveNumRes.addActionListener(e -> saveImage(matResultNumerical));
    }

    private JPanel createImagePanel(String title, JLabel imageLabel) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder(title));
        imageLabel.setHorizontalAlignment(JLabel.CENTER);
        JScrollPane scroll = new JScrollPane(imageLabel);
        p.add(scroll, BorderLayout.CENTER);
        return p;
    }

    private File chooseFile() {
        JFileChooser fc = new JFileChooser(lastDirectory);
        fc.setFileFilter(new FileNameExtensionFilter("Imagens", "jpg", "png", "bmp", "tif"));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selected = fc.getSelectedFile();
            lastDirectory = selected.getParentFile();
            return selected;
        }
        return null;
    }

    // --- LÓGICA DE PROCESSAMENTO ---

    private void processFlatField() {
        if (matOriginal == null || matFlatField == null) {
            // Não mostra erro popup para não interromper fluxo do slider, apenas loga ou ignora
            return; 
        }
        try {
            Mat src32 = new Mat();
            matOriginal.convertTo(src32, CvType.CV_32F);
            Mat flat32 = new Mat();
            matFlatField.convertTo(flat32, CvType.CV_32F);

            // Pega valor do Slider de Blur
            int blurK = sliderBlur.getValue();
            if (blurK > 0) {
                int kSize = (blurK % 2 == 0) ? blurK + 1 : blurK; 
                Imgproc.GaussianBlur(flat32, flat32, new Size(kSize, kSize), 0);
            }

            Scalar meanScalar = Core.mean(flat32);
            Mat res32 = new Mat();
            Core.divide(src32, flat32, res32);
            Core.multiply(res32, meanScalar, res32);

            // Clamp para evitar estouro
            clampTo255(res32);

            matResultFlat = new Mat();
            res32.convertTo(matResultFlat, CvType.CV_8U); 
            displayImage(matResultFlat, lblResultFlat);

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void processNumericalCorrection() {
        // Usa o resultado do Flat Field se existir, senão usa o Original
        Mat input = (matResultFlat != null) ? matResultFlat : matOriginal;
        if (input == null) return;

        try {
            // Pega valor do Slider K e converte para float
            double k = sliderK.getValue() / K_SCALE_FACTOR;
            
            int width = input.width();
            int height = input.height();
            double cx = width / 2.0;
            double cy = height / 2.0;
            double maxDist = Math.sqrt(cx * cx + cy * cy);

            float[] gainData = new float[width * height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    double dx = x - cx;
                    double dy = y - cy;
                    double rNorm = Math.sqrt(dx*dx + dy*dy) / maxDist;
                    
                    // Modelo: Gain = 1 + k * r^2
                    gainData[y * width + x] = (float) (1.0 + (k * (rNorm * rNorm)));
                }
            }
            
            Mat gainMat1C = new Mat(height, width, CvType.CV_32FC1);
            gainMat1C.put(0, 0, gainData);
            
            Mat gainMat;
            if (input.channels() == 3) {
                List<Mat> channels = new ArrayList<>();
                channels.add(gainMat1C); channels.add(gainMat1C); channels.add(gainMat1C);
                gainMat = new Mat();
                Core.merge(channels, gainMat);
            } else {
                gainMat = gainMat1C;
            }

            Mat input32 = new Mat();
            input.convertTo(input32, CvType.CV_32F);
            Mat res32 = new Mat();
            Core.multiply(input32, gainMat, res32);

            // Clamp para evitar estouro (triângulos vermelhos)
            clampTo255(res32);

            matResultNumerical = new Mat();
            res32.convertTo(matResultNumerical, CvType.CV_8U); 
            
            displayImage(matResultNumerical, lblResultNumerical);

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void clampTo255(Mat floatMat) {
        Scalar saturationLimit;
        if (floatMat.channels() > 1) {
            saturationLimit = new Scalar(255.0, 255.0, 255.0, 255.0);
        } else {
            saturationLimit = new Scalar(255.0);
        }
        Core.min(floatMat, saturationLimit, floatMat);
    }

    private void saveImage(Mat matToSave) {
        if (matToSave == null) return;
        JFileChooser fc = new JFileChooser(lastDirectory);
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            String path = fc.getSelectedFile().getAbsolutePath();
            if (!path.toLowerCase().endsWith(".png") && !path.toLowerCase().endsWith(".jpg")) path += ".png";
            Imgcodecs.imwrite(path, matToSave);
            JOptionPane.showMessageDialog(this, "Salvo com sucesso!");
        }
    }

    private void displayImage(Mat mat, JLabel label) {
        if (mat == null) return;
        BufferedImage img = matToBufferedImage(mat);
        if (img != null) {
            // Lógica de redimensionamento para preview rápido
            int maxWidth = 320; 
            if (img.getWidth() > maxWidth) {
                int newHeight = (int) ((double) img.getHeight() / img.getWidth() * maxWidth);
                Image scaled = img.getScaledInstance(maxWidth, newHeight, Image.SCALE_FAST);
                label.setIcon(new ImageIcon(scaled));
            } else {
                label.setIcon(new ImageIcon(img));
            }
        }
        label.revalidate();
        label.repaint();
    }

    public static BufferedImage matToBufferedImage(Mat m) {
        if (m == null || m.empty()) return null;
        int type = BufferedImage.TYPE_BYTE_GRAY;
        if (m.channels() > 1) {
            type = BufferedImage.TYPE_3BYTE_BGR;
        }
        int bufferSize = m.channels() * m.cols() * m.rows();
        byte[] b = new byte[bufferSize];
        m.get(0, 0, b); 
        BufferedImage image = new BufferedImage(m.cols(), m.rows(), type);
        final byte[] targetPixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        System.arraycopy(b, 0, targetPixels, 0, b.length);
        return image;
    }

    public static void main(String[] args) {
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
        
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception e) {}
        SwingUtilities.invokeLater(() -> new ImageCorrectorSlider().setVisible(true));
    }
}