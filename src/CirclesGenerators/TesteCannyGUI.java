package CirclesGenerators;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;

public class TesteCannyGUI extends JFrame {

    // Componentes da Interface
    private JTextField txtParam1, txtKernelSize, txtSigma;
    private JSlider sliderZoom;
    private Mat matOriginal;

    // Nossos painéis customizados para suportar Zoom
    private PainelImagem painelOriginal;
    private PainelImagem painelBlur;
    private PainelImagem painelCanny;

    public TesteCannyGUI() {
        setTitle("Pipeline Avançado: Zoom e Scroll Sincronizados");
        setSize(1400, 750);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // 1. --- PAINEL SUPERIOR (Norte) ---
        JPanel painelNorte = new JPanel();
        painelNorte.setLayout(new GridLayout(2, 1, 5, 5));
        painelNorte.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Linha 1: Botões e Zoom
        JPanel painelAcoes = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 0));
        JButton btnCarregar = new JButton("Carregar Imagem");
        JButton btnExecutar = new JButton("Executar Pipeline");

        painelAcoes.add(btnCarregar);
        painelAcoes.add(btnExecutar);
        painelAcoes.add(new JLabel("  |  Zoom:"));

        // Slider de Zoom (10% a 300%, começa em 100%)
        sliderZoom = new JSlider(10, 300, 100);
        sliderZoom.setPaintTicks(true);
        sliderZoom.setMajorTickSpacing(50);
        painelAcoes.add(sliderZoom);

        // Linha 2: Parâmetros
        JPanel painelParametros = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 0));
        painelParametros.add(new JLabel("Kernel (ímpar):"));
        txtKernelSize = new JTextField("5", 4);
        painelParametros.add(txtKernelSize);

        painelParametros.add(new JLabel("Sigma:"));
        txtSigma = new JTextField("0", 4);
        painelParametros.add(txtSigma);

        painelParametros.add(new JLabel("Canny (param1):"));
        txtParam1 = new JTextField("100", 4);
        painelParametros.add(txtParam1);

        painelNorte.add(painelAcoes);
        painelNorte.add(painelParametros);
        add(painelNorte, BorderLayout.NORTH);

        // 2. --- PAINEL CENTRAL (Centro) ---
        JPanel painelCentral = new JPanel(new GridLayout(1, 3, 10, 10));
        painelCentral.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));

        // Inicializa os painéis customizados
        painelOriginal = new PainelImagem();
        painelBlur = new PainelImagem();
        painelCanny = new PainelImagem();

        // ScrollPanes (Apenas o Original mostra barras de rolagem visualmente, conforme solicitado)
        JScrollPane scrollOriginal = new JScrollPane(painelOriginal);
        JScrollPane scrollBlur = new JScrollPane(painelBlur);
        JScrollPane scrollCanny = new JScrollPane(painelCanny);

        // Oculta as barras dos outros dois para deixar a interface limpa
        scrollBlur.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        scrollBlur.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollCanny.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        scrollCanny.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        // --- A MÁGICA DA SINCRONIZAÇÃO DO SCROLL ---
        // Quando a "lente" (Viewport) do Original se mover, movemos as outras duas
        scrollOriginal.getViewport().addChangeListener(e -> {
            Point posicao = scrollOriginal.getViewport().getViewPosition();
            scrollBlur.getViewport().setViewPosition(posicao);
            scrollCanny.getViewport().setViewPosition(posicao);
        });

        // Adiciona títulos acima dos ScrollPanes
        painelCentral.add(criarPainelComTitulo("Original (Controle)", scrollOriginal));
        painelCentral.add(criarPainelComTitulo("Blur Gaussiano", scrollBlur));
        painelCentral.add(criarPainelComTitulo("Canny Edge", scrollCanny));

        add(painelCentral, BorderLayout.CENTER);

        // 3. --- EVENTOS ---

        // Evento: Controle de Zoom
        sliderZoom.addChangeListener(e -> {
            double escala = sliderZoom.getValue() / 100.0;
            painelOriginal.setEscala(escala);
            painelBlur.setEscala(escala);
            painelCanny.setEscala(escala);

            // Revalida os ScrollPanes para ajustarem as barras de rolagem ao novo tamanho
            scrollOriginal.revalidate();
            scrollBlur.revalidate();
            scrollCanny.revalidate();
        });

        btnCarregar.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileFilter(new FileNameExtensionFilter("Imagens", "jpg", "jpeg", "png"));
            if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                matOriginal = Imgcodecs.imread(fileChooser.getSelectedFile().getAbsolutePath());
                if (!matOriginal.empty()) {
                    painelOriginal.setImagem(matToBufferedImage(matOriginal));
                    painelBlur.setImagem(null);
                    painelCanny.setImagem(null);
                    sliderZoom.setValue(100); // Reseta o zoom
                }
            }
        });

        btnExecutar.addActionListener(e -> executarPipeline());
    }

    private void executarPipeline() {
        if (matOriginal == null || matOriginal.empty()) return;

        try {
            int kernelSize = Integer.parseInt(txtKernelSize.getText().trim());
            double sigma = Double.parseDouble(txtSigma.getText().trim());
            double param1 = Double.parseDouble(txtParam1.getText().trim());

            if (kernelSize <= 0 || kernelSize % 2 == 0) {
                JOptionPane.showMessageDialog(this, "Kernel deve ser ímpar e maior que zero.");
                return;
            }

            Mat gray = new Mat();
            Mat blurred = new Mat();
            Mat edges = new Mat();

            Imgproc.cvtColor(matOriginal, gray, Imgproc.COLOR_BGR2GRAY);
            Imgproc.GaussianBlur(gray, blurred, new Size(kernelSize, kernelSize), sigma);
            Imgproc.Canny(blurred, edges, param1 / 2.0, param1);

            // Atualiza os painéis com as novas imagens
            painelBlur.setImagem(matToBufferedImage(blurred));
            painelCanny.setImagem(matToBufferedImage(edges));

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Verifique os parâmetros numéricos.");
        }
    }

    // Método auxiliar para criar bordas e títulos
    private JPanel criarPainelComTitulo(String titulo, Component componente) {
        JPanel painel = new JPanel(new BorderLayout());
        JLabel lblTitulo = new JLabel(titulo, SwingConstants.CENTER);
        lblTitulo.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
        painel.add(lblTitulo, BorderLayout.NORTH);
        painel.add(componente, BorderLayout.CENTER);
        return painel;
    }

    // Conversão de Mat para BufferedImage
    private BufferedImage matToBufferedImage(Mat mat) {
        try {
            MatOfByte mob = new MatOfByte();
            Imgcodecs.imencode(".png", mat, mob);
            return ImageIO.read(new ByteArrayInputStream(mob.toArray()));
        } catch (Exception e) {
            return null;
        }
    }

    // =========================================================
    // CLASSE INTERNA: Painel de Imagem Customizado (Alta Performance)
    // =========================================================
    class PainelImagem extends JPanel {
        private BufferedImage imagem;
        private double escala = 1.0;

        public void setImagem(BufferedImage imagem) {
            this.imagem = imagem;
            revalidate();
            repaint();
        }

        public void setEscala(double escala) {
            this.escala = escala;
            repaint();
        }

        // Este método informa ao JScrollPane qual é o tamanho "real" da imagem após o zoom
        @Override
        public Dimension getPreferredSize() {
            if (imagem == null) return new Dimension(400, 400);
            return new Dimension((int) (imagem.getWidth() * escala), (int) (imagem.getHeight() * escala));
        }

        // Desenha a imagem usando aceleração 2D nativa do Java
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (imagem != null) {
                Graphics2D g2d = (Graphics2D) g.create();
                // Melhora a qualidade do desenho durante o zoom
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2d.scale(escala, escala);
                g2d.drawImage(imagem, 0, 0, this);
                g2d.dispose();
            }
        }
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
        SwingUtilities.invokeLater(() -> new TesteCannyGUI().setVisible(true));
    }
}