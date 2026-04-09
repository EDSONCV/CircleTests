package lensCorrection;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ImageCorrectorApp extends JFrame {



    private static File lastDirectory = new File(System.getProperty("user.home"));

    private Mat matOriginal, matFlatField, matResultFlat, matResultNumerical;
    private JLabel lblImageOriginal, lblImageFlat, lblResultFlat, lblResultNumerical;
    private JTextField txtPathOriginal, txtPathFlat, txtParamK, txtParamBlur;
    
    public ImageCorrectorApp() {
        setTitle("ImageJ(ava) Corrector v3 - Clamp Fix Final");
        setSize(1400, 800);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // --- PAINEL SUPERIOR ---
        JPanel topPanel = new JPanel(new GridLayout(2, 1));
        
        JPanel filePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        txtPathOriginal = new JTextField(20);
        txtPathFlat = new JTextField(20);
        JButton btnLoadOriginal = new JButton("Carregar Imagem");
        JButton btnLoadFlat = new JButton("Carregar Flat-Field");
        
        filePanel.add(new JLabel("Imagem:"));
        filePanel.add(txtPathOriginal);
        filePanel.add(btnLoadOriginal);
        filePanel.add(Box.createHorizontalStrut(20));
        filePanel.add(new JLabel("Flat-Field:"));
        filePanel.add(txtPathFlat);
        filePanel.add(btnLoadFlat);

        JPanel paramPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        txtParamBlur = new JTextField("20", 5);
        JButton btnApplyFlat = new JButton("1. Aplicar Flat-Field");
        
        txtParamK = new JTextField("0.5", 10);
        JButton btnApplyNum = new JButton("2. Aplicar Correção Numérica");

        paramPanel.add(new JLabel("Blur Sigma (Flat):"));
        paramPanel.add(txtParamBlur);
        paramPanel.add(btnApplyFlat);
        paramPanel.add(new JSeparator(SwingConstants.VERTICAL));
        paramPanel.add(new JLabel("Força K (Numérica):"));
        paramPanel.add(txtParamK);
        paramPanel.add(btnApplyNum);

        topPanel.add(filePanel);
        topPanel.add(paramPanel);
        add(topPanel, BorderLayout.NORTH);

        // --- PAINEL CENTRAL ---
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

        // --- PAINEL INFERIOR ---
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnSaveFlatRes = new JButton("Salvar Resultado Flat");
        JButton btnSaveNumRes = new JButton("Salvar Resultado Numérico");
        
        bottomPanel.add(btnSaveFlatRes);
        bottomPanel.add(btnSaveNumRes);
        add(bottomPanel, BorderLayout.SOUTH);

        // --- LISTENERS ---
        btnLoadOriginal.addActionListener(e -> {
            File f = chooseFile();
            if (f != null) {
                txtPathOriginal.setText(f.getAbsolutePath());
                matOriginal = Imgcodecs.imread(f.getAbsolutePath());
                displayImage(matOriginal, lblImageOriginal);
            }
        });

        btnLoadFlat.addActionListener(e -> {
            File f = chooseFile();
            if (f != null) {
                txtPathFlat.setText(f.getAbsolutePath());
                matFlatField = Imgcodecs.imread(f.getAbsolutePath());
                displayImage(matFlatField, lblImageFlat);
            }
        });

        btnApplyFlat.addActionListener(e -> processFlatField());
        btnApplyNum.addActionListener(e -> processNumericalCorrection());
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

    // --- PROCESSAMENTO CORRIGIDO ---

    private void processFlatField() {
        if (matOriginal == null || matFlatField == null) return;
        try {
            Mat src32 = new Mat();
            matOriginal.convertTo(src32, CvType.CV_32F);
            Mat flat32 = new Mat();
            matFlatField.convertTo(flat32, CvType.CV_32F);

            int blurK = Integer.parseInt(txtParamBlur.getText());
            if (blurK > 0) {
                int kSize = (blurK % 2 == 0) ? blurK + 1 : blurK; 
                Imgproc.GaussianBlur(flat32, flat32, new Size(kSize, kSize), 0);
            }

            Scalar meanScalar = Core.mean(flat32);
            Mat res32 = new Mat();
            Core.divide(src32, flat32, res32);
            Core.multiply(res32, meanScalar, res32);

            // --- FIX: Clamp manual para evitar estouro ---
            clampTo255(res32);

            matResultFlat = new Mat();
            res32.convertTo(matResultFlat, CvType.CV_8U); 
            displayImage(matResultFlat, lblResultFlat);

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void processNumericalCorrection() {
        Mat input = (matResultFlat != null) ? matResultFlat : matOriginal;
        if (input == null) return;

        try {
            double k = Double.parseDouble(txtParamK.getText());
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

            // --- FIX: Clamp manual para evitar estouro ---
            clampTo255(res32);

            matResultNumerical = new Mat();
            // Agora a conversão é segura pois nada excede 255.0
            res32.convertTo(matResultNumerical, CvType.CV_8U); 
            
            displayImage(matResultNumerical, lblResultNumerical);

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Método auxiliar crucial: Força qualquer valor float acima de 255.0 a ser exatamente 255.0.
     * Isso previne que a conversão para byte gere artefatos de "wrap around" (cores erradas).
     */
    private void clampTo255(Mat floatMat) {
        Scalar saturationLimit;
        if (floatMat.channels() > 1) {
            saturationLimit = new Scalar(255.0, 255.0, 255.0, 255.0); // BGR(A)
        } else {
            saturationLimit = new Scalar(255.0); // Grayscale
        }
        // Core.min compara cada pixel com o escalar e pega o menor.
        // Se pixel for 300.0, o resultado é 255.0. Se for 100.0, fica 100.0.
        Core.min(floatMat, saturationLimit, floatMat);
    }


    private void saveImage(Mat matToSave) {
        if (matToSave == null) return;
        JFileChooser fc = new JFileChooser(lastDirectory);
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            String path = fc.getSelectedFile().getAbsolutePath();
            if (!path.toLowerCase().endsWith(".png") && !path.toLowerCase().endsWith(".jpg")) path += ".png";
            Imgcodecs.imwrite(path, matToSave);
        }
    }

    private void displayImage(Mat mat, JLabel label) {
        if (mat == null) return;
        BufferedImage img = matToBufferedImage(mat);
        if (img != null) {
             // Redimensiona para caber no painel se for muito grande, mantendo aspecto
            int maxWidth = 350; // Largura aproximada do painel
            if (img.getWidth() > maxWidth) {
                int newHeight = (int) ((double) img.getHeight() / img.getWidth() * maxWidth);
                Image scaled = img.getScaledInstance(maxWidth, newHeight, Image.SCALE_FAST);
                label.setIcon(new ImageIcon(scaled));
            } else {
                label.setIcon(new ImageIcon(img));
            }
        }
        label.revalidate();
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
        SwingUtilities.invokeLater(() -> new ImageCorrectorApp().setVisible(true));
    }
}
