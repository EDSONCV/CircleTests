package ParallelReinfLearningMod;

import java.util.ArrayList;
import java.util.List;

import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import filters.BrightnessContrastFilter;
import filters.GaussianBlurFilter;
import filters.ImageFilter;
import filters.OptParam;

class ProcessingPipeline {
    private List<ImageFilter> filters = new ArrayList<>();
    private boolean usePureJavaHough = false; // False use native Hough circles by default
    private int houghMethod = Imgproc.HOUGH_GRADIENT_ALT; // Default

    // "Core" parameters of Hough  method (not filters, but they are decision variables)
    private OptParam houghDp = new OptParam("H_dp", 2.0, 0.5, 3.0, 0.1, false);
    //private OptParam houghMinDist = new OptParam("H_minDist", 10, 40, 110, 5, true);
    //private OptParam houghP1 = new OptParam("H_p1", 10, 10, 140, 10, false);
    //private OptParam houghP2 = new OptParam("H_p2", 0.9, 0.1, 0.9, 0.1, false);
    //private OptParam houghMinR = new OptParam("H_minR", 5, 5, 30, 1, true);
    //private OptParam houghMaxR = new OptParam("H_maxR", 60, 30, 160, 2, true);
    private OptParam houghP1 = new OptParam("H_p1", 130, 10, 140, 10, false);
    private OptParam houghP2 = new OptParam("H_p2", 0.9, 0.1, 0.9, 0.1, false);
    private OptParam houghMinDist = new OptParam("H_minDist", 1, 1, 110, 2, true);
    private OptParam houghMinR = new OptParam("H_minR", 1, 1, 15, 1, true);
    private OptParam houghMaxR = new OptParam("H_maxR", 54, 15, 40, 2, true);


    /*
    //old values
    private OptParam houghDp = new OptParam("H_dp", 1.0, 0.5, 3.0, 0.1, false);
    private OptParam houghMinDist = new OptParam("H_minDist", 60, 40, 110, 10, false);
    private OptParam houghP1 = new OptParam("H_p1", 100, 20, 140, 20, false);
    private OptParam houghP2 = new OptParam("H_p2", 0.5, 0.1, 0.9, 0.1, false);
    private OptParam houghMinR = new OptParam("H_minR", 10, 5, 30, 2, true);
    private OptParam houghMaxR = new OptParam("H_maxR", 100, 20, 160, 5, true);
     */




    public void addFilter(ImageFilter filter) {
        filters.add(filter);
    }

    /**
     * run the filter chain in a sequence
     */
    public Mat executePipeline(Mat originalImage) {
        Mat currentImage = originalImage.clone();
        
        //converts to grayscale if needed by the filter
        if (currentImage.channels() > 1) {
             Imgproc.cvtColor(currentImage, currentImage, Imgproc.COLOR_BGR2GRAY);
        }

        // runs each filter in a sequence
        for (ImageFilter filter : filters) {
            Mat nextImage = filter.process(currentImage);
            currentImage.release(); // Libera memória da etapa anterior
            currentImage = nextImage;
        }
        return currentImage;
    }

    /**
     * Retorna TODOS os parâmetros (Filtros + Hough) em uma lista plana.
     * O Agente RL vai interagir com essa lista.
     */
    public List<OptParam> getAllParameters() {
        List<OptParam> allParams = new ArrayList<>();
        // Adiciona params dos filtros
        for (ImageFilter filter : filters) {
            allParams.addAll(filter.getParams());
        }
        // Adiciona params do Hough
        allParams.add(houghDp);
        allParams.add(houghMinDist);
        allParams.add(houghP1);
        allParams.add(houghP2);
        allParams.add(houghMinR);
        allParams.add(houghMaxR);
        return allParams;
    }
    
    public List<StepResult> runPipelineWithDebug(Mat originalImage) {
        List<StepResult> steps = new ArrayList<>();
        
        // 1. Etapa Zero: Imagem Original
        // Adicionamos um clone para garantir que não seja alterada depois
        steps.add(new StepResult(originalImage.clone(), "Original", "Input"));

        Mat currentImage = originalImage.clone();
        
        // Se a primeira etapa exigir grayscale e a imagem for colorida, converte
        // (Isso pode ser considerado uma etapa implícita ou parte do setup)
        if (currentImage.channels() > 1) {
             Imgproc.cvtColor(currentImage, currentImage, Imgproc.COLOR_BGR2GRAY);
        }

        // 2. Loop pelos Filtros
        for (ImageFilter filter : filters) {
            // Processa
            Mat nextImage = filter.process(currentImage);
            
            // Monta a string de parâmetros deste filtro específico
            StringBuilder paramStr = new StringBuilder();
            for (OptParam p : filter.getParams()) {
                paramStr.append(p.toString()).append(" ");
            }

            // Salva no histórico (Clonando para garantir persistência visual)
            steps.add(new StepResult(nextImage.clone(), filter.getName(), paramStr.toString()));

            // Prepara próxima iteração
            currentImage.release(); // Libera a anterior
            currentImage = nextImage;
        }

        return steps; // Retorna lista: [Original, Filtro1, Filtro2, ..., ResultadoFinalPipeline]
    }
 // Na classe ProcessingPipeline

    /**
     * Cria uma cópia profunda (ou funcional) do pipeline e aplica os novos parâmetros.
     * Essencial para Multithreading.
     */
    public ProcessingPipeline cloneWithParams(List<OptParam> newParams) {
        // 1. Cria uma nova instância do pipeline (vazio)
        ProcessingPipeline clone = new ProcessingPipeline();
        
        // 2. Adiciona os mesmos filtros (novas instâncias)
        for (ImageFilter filter : this.filters) {
            // Assumindo que seus filtros tenham um método ou construtor de cópia.
            // Se não tiver, você precisa criar novas instâncias manualmente aqui.
            // Exemplo genérico:
            if (filter instanceof GaussianBlurFilter) clone.addFilter(new GaussianBlurFilter());
            else if (filter instanceof BrightnessContrastFilter) clone.addFilter(new BrightnessContrastFilter());
            // ... outros filtros ...
        }
        
        // 3. Sincroniza os valores baseados na lista 'newParams' recebida.
        // A lista 'newParams' contém nomes como "G_Kernel", "H_param1", etc.
        // O 'clone' tem seus próprios OptParams internos com esses mesmos nomes.
        clone.syncParameters(newParams);
        
        return clone;
    }

    /**
     * Percorre os parâmetros internos deste pipeline e atualiza seus valores
     * caso encontre um correspondente (pelo nome) na lista fornecida.
     */
    public void syncParameters(List<OptParam> sourceParams) {
        // Lista de todos os params DESTE pipeline (Hough + Filtros)
        List<OptParam> myParams = this.getAllParameters();
        
        for (OptParam myParam : myParams) {
            // Procura na lista recebida se tem alguém com o mesmo nome
            for (OptParam sourceParam : sourceParams) {
                if (myParam.getName().equals(sourceParam.getName())) {
                    // Atualiza o valor (força o valor bruto)
                    myParam.setValue(sourceParam.getValue());
                    break;
                }
            }
        }
    }
    
    public String getHoughParamsString() {
        return String.format("dp=%.1f minDist=%.0f p1=%.0f p2=%.0f minR=%d maxR=%d",
                getDp(), getMinDist(), getParam1(), getParam2(), getMinRadius(), getMaxRadius());
    }
    
    // Getters to be called by Hough
    public double getDp() { return houghDp.getValue(); }
    public double getMinDist() { return houghMinDist.getValue(); }
    public double getParam1() { return houghP1.getValue(); }
    public double getParam2() { return houghP2.getValue(); }
    public int getMinRadius() { return (int)houghMinR.getValue(); }
    public int getMaxRadius() { return (int)houghMaxR.getValue(); }

    /**
     * Defines the Houghs' strategy and adjust the P2 parameter acornding to Opencv documentation
     * For HOUGH_GRADIENT_ALT P2 must be smaller than 1
     */
    public void setHoughMethod(int method) {
        this.houghMethod = method;

        // Atualiza os limites do parâmetro 2 dinamicamente
        if (this.houghMethod == Imgproc.HOUGH_GRADIENT) {
            // For HOUGH_GRADIENT, P2 is the accumulator threshold ( higher, circles more precise)
            // Typical range between  10.0 a 100.0 (or more depending on the noise)
            // Substitua 'param2Opt' pelo nome real da sua variável OptParam interna
            this.houghP2 = new OptParam("param2", 10.0, 150.0, 30.0, 1.0,false);
        } else {
            // for HOUGH_GRADIENT_ALT, P2 is the measure of "perfection" (values close to 1 = more perfect).
            // Faixa típica: 0.5 a 1.0 (NUNCA maior que 1.0)
            this.houghP2 = new OptParam("H_p2", 0.9, 0.1, 1, 0.1, false);
        }
    }

    public int getHoughMethod() {
        return this.houghMethod;
    }
    public void setUsePureJavaHough(boolean usePureJava) {
        this.usePureJavaHough = usePureJava;
    }

    public boolean isUsePureJavaHough() {
        return this.usePureJavaHough;
    }
}

