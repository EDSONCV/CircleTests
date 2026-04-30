package CirclesGenerators;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.highgui.HighGui;
import org.opencv.core.CvType;
import org.opencv.core.Scalar;

import java.io.File;

public class ShadedSphere {


    public static int ILUM_DIRECIONAL = 0;
    public static int ILUM_RING_LIGHT = 1;
    public static int ILUM_DEGRADE_BORDA = 2;



    // Propriedades imutáveis após a construção
    private final int cx;
    private final int cy;
    private final int raio;
    private final int corB;
    private final int corG;
    private final int corR;
    private final double luzAmbiente;

    // Controle do modo de iluminação
    private final int tipoIluminacao;

    // Propriedades: Luz Direcional
    private final double lx;
    private final double ly;
    private final double lz;

    // Propriedades: Ring Light
    private final double raioAnelNormalizado;
    private final double larguraAnel;

    // Propriedades: Degradê de Borda
    private final double fatorDecaimentoBorda;

    // Construtor privado: só o Builder pode instanciar
    private ShadedSphere(Builder builder) {
        this.cx = builder.cx;
        this.cy = builder.cy;
        this.raio = builder.raio;
        this.corB = builder.corB;
        this.corG = builder.corG;
        this.corR = builder.corR;
        this.luzAmbiente = builder.luzAmbiente;
        this.tipoIluminacao = builder.tipoIluminacao;

        this.lx = builder.lx;
        this.ly = builder.ly;
        this.lz = builder.lz;

        this.raioAnelNormalizado = builder.raioAnelNormalizado;
        this.larguraAnel = builder.larguraAnel;

        this.fatorDecaimentoBorda = builder.fatorDecaimentoBorda;
    }

    /**
     * Desenha a esfera na matriz de destino com alta performance.
     */
    public void desenhar(Mat imagem) {
        int width = imagem.cols();
        int height = imagem.rows();

        // 1. Calcula o Bounding Box garantindo que não saia da imagem
        int inicioX = Math.max(0, cx - raio);
        int fimX = Math.min(width, cx + raio);
        int inicioY = Math.max(0, cy - raio);
        int fimY = Math.min(height, cy + raio);

        int roiWidth = fimX - inicioX;
        int roiHeight = fimY - inicioY;

        if (roiWidth <= 0 || roiHeight <= 0) return; // Esfera fora da tela

        // 2. Extrai a região de interesse (ROI) para a RAM
        Mat roi = imagem.submat(inicioY, fimY, inicioX, fimX);
        int canais = roi.channels();
        byte[] buffer = new byte[roiWidth * roiHeight * canais];
        roi.get(0, 0, buffer);

        // 3. Processa a iluminação pixel a pixel
        for (int y = 0; y < roiHeight; y++) {
            for (int x = 0; x < roiWidth; x++) {
                int realX = inicioX + x;
                int realY = inicioY + y;
                double dx = realX - cx;
                double dy = realY - cy;
                double distQuadrada = dx * dx + dy * dy;

                if (distQuadrada <= raio * raio) {
                    double intensidade = 0.0;
                    double brilhoExtra = 0.0;

                    // --- LÓGICA CONDICIONAL DE ILUMINAÇÃO ---
                    if (tipoIluminacao == ILUM_DIRECIONAL) {
                        double z = Math.sqrt(raio * raio - distQuadrada);
                        double nx = dx / raio;
                        double ny = dy / raio;
                        double nz = z / raio;

                        double dot = (nx * lx) + (ny * ly) + (nz * lz);
                        double luzDifusa = Math.max(0, dot);
                        intensidade = Math.min(1.0, luzAmbiente + luzDifusa);

                    } else if (tipoIluminacao == ILUM_RING_LIGHT) {
                        double d = Math.sqrt(distQuadrada);
                        double rNormalizado = d / raio;

                        double expoente = -Math.pow((rNormalizado - raioAnelNormalizado), 2) / (2 * Math.pow(larguraAnel, 2));
                        double luzRingLight = Math.exp(expoente);

                        intensidade = Math.min(1.0, luzAmbiente + luzRingLight);
                        brilhoExtra = Math.max(0, intensidade - 0.7) * 2;

                    } else if (tipoIluminacao == ILUM_DEGRADE_BORDA) {
                        double d = Math.sqrt(distQuadrada);
                        double rNormalizado = d / raio; // 1.0 na borda, 0.0 no centro

                        // Elevamos o raio a uma potência para controlar a curva de decaimento
                        double luzDegrade = Math.pow(rNormalizado, fatorDecaimentoBorda);

                        intensidade = Math.min(1.0, luzAmbiente + luzDegrade);
                        // Efeito de clareamento (branco) leve nas bordas extremas
                        brilhoExtra = Math.max(0, intensidade - 0.8) * 1.5;
                    }

                    int indicePixel = (y * roiWidth + x) * canais;

                    buffer[indicePixel]     = (byte) Math.min(255, (corB * intensidade) + (255 * brilhoExtra)); // Blue
                    buffer[indicePixel + 1] = (byte) Math.min(255, (corG * intensidade) + (255 * brilhoExtra)); // Green
                    buffer[indicePixel + 2] = (byte) Math.min(255, (corR * intensidade) + (255 * brilhoExtra)); // Red
                }
            }
        }

        // 4. Devolve o buffer processado para a imagem
        roi.put(0, 0, buffer);
    }

    // --- CLASSE BUILDER ---
    public static class Builder {
        private final int cx;
        private final int cy;
        private final int raio;

        private int corB = 255, corG = 255, corR = 255;
        private double luzAmbiente = 0.15;

        private int tipoIluminacao = ILUM_DIRECIONAL;

        // Propriedades padrão de cada modo
        private double lx = -1.0, ly = 0.2, lz = 0.5;
        private double raioAnelNormalizado = 0.8;
        private double larguraAnel = 0.1;
        private double fatorDecaimentoBorda = 2.0;

        public Builder(int cx, int cy, int raio) {
            this.cx = cx;
            this.cy = cy;
            this.raio = raio;
            normalizarVetorLuz();
        }

        public Builder comCorBGR(int b, int g, int r) {
            this.corB = Math.max(0, Math.min(255, b));
            this.corG = Math.max(0, Math.min(255, g));
            this.corR = Math.max(0, Math.min(255, r));
            return this;
        }

        public Builder comLuzAmbiente(double luzAmbiente) {
            this.luzAmbiente = Math.max(0.0, Math.min(1.0, luzAmbiente));
            return this;
        }

        public Builder comDirecaoLuz(double lx, double ly, double lz) {
            if (lx == 0 && ly == 0 && lz == 0) {
                throw new IllegalArgumentException("O vetor de luz não pode ser nulo (0,0,0).");
            }
            this.lx = lx;
            this.ly = ly;
            this.lz = lz;
            normalizarVetorLuz();
            this.tipoIluminacao = ILUM_DIRECIONAL;
            return this;
        }

        public Builder comRingLight(double raioAnel, double largura) {
            this.raioAnelNormalizado = Math.max(0.0, Math.min(1.0, raioAnel));
            this.larguraAnel = Math.max(0.01, largura);
            this.tipoIluminacao = ILUM_RING_LIGHT;
            return this;
        }

        /**
         * Define o modo de iluminação como DEGRADE_BORDA.
         * @param decaimento Quanto maior (ex: 3.0 ou 4.0), mais concentrada na borda fica a luz.
         * Um valor de 1.0 cria um degradê linear constante do centro à borda.
         */
        public Builder comDegradeBorda(double decaimento) {
            this.fatorDecaimentoBorda = Math.max(0.1, decaimento);
            this.tipoIluminacao = ILUM_DEGRADE_BORDA;
            return this;
        }

        private void normalizarVetorLuz() {
            double tamanhoLuz = Math.sqrt(this.lx * this.lx + this.ly * this.ly + this.lz * this.lz);
            this.lx /= tamanhoLuz;
            this.ly /= tamanhoLuz;
            this.lz /= tamanhoLuz;
        }

        public ShadedSphere build() {
            return new ShadedSphere(this);
        }
    }

    public static void main(String[] args) {
        try {
            String filePre = "";
            String fileExt = ".dll";
            final File nativeLibrary = new File("lib/java/x64/" + filePre + Core.NATIVE_LIBRARY_NAME + fileExt);
            System.load(nativeLibrary.getAbsolutePath());
        } catch (UnsatisfiedLinkError e) {
            // Option B: Load directly from absolute path if Option A fails
            // System.load("C:/path/to/opencv/build/java/x64/opencv_java4x.dll");
            System.err.println("Native code library failed to load. \n" + e);
            System.exit(1);
        }

        // Aumentei o tamanho da tela para caber 3 esferas
        Mat imagem = new Mat(400, 1000, CvType.CV_8UC3, new Scalar(30, 30, 30));

        // 1. LUZ DIRECIONAL (Laranja)
        ShadedSphere direcional = new ShadedSphere.Builder(200, 200, 120)
                .comCorBGR(0, 165, 255)
                .comDirecaoLuz(-1.0, -1.0, 0.5)
                .comLuzAmbiente(0.2)
                .build();

        // 2. RING LIGHT (Verde)
        ShadedSphere ringLight = new ShadedSphere.Builder(500, 200, 120)
                .comCorBGR(100, 255, 50)
                .comRingLight(0.8, 0.1)
                .comLuzAmbiente(0.1)
                .build();

        // 3. DEGRADÊ DE BORDA (Azul) - O novo efeito!
        ShadedSphere degradeBorda = new ShadedSphere.Builder(800, 200, 120)
                .comCorBGR(255, 100, 50) // Azul em BGR
                .comDegradeBorda(2.5)    // 2.5 dá um decaimento suave. Experimente 1.0 (linear) ou 4.0 (borda fina)
                .comLuzAmbiente(0.1)
                .build();

        direcional.desenhar(imagem);
        ringLight.desenhar(imagem);
        degradeBorda.desenhar(imagem);
        HighGui.imshow("Três Modos de Iluminação (OpenCV)", imagem);
        HighGui.waitKey(0);
        System.exit(0);
    }
}