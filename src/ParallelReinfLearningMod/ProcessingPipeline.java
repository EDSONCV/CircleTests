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
    
    // Parâmetros "Core" do Hough (que não são filtros, mas são decisão)
    private OptParam houghDp = new OptParam("H_dp", 2.0, 0.5, 3.0, 0.1, false);
    private OptParam houghMinDist = new OptParam("H_minDist", 10, 40, 110, 10, false);
    private OptParam houghP1 = new OptParam("H_p1", 10, 10, 140, 20, false);
    private OptParam houghP2 = new OptParam("H_p2", 0.9, 0.1, 0.9, 0.1, false);
    private OptParam houghMinR = new OptParam("H_minR", 5, 5, 30, 2, true);
    private OptParam houghMaxR = new OptParam("H_maxR", 60, 30, 160, 5, true);

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
     * Executa toda a cadeia de filtros sequencialmente.
     */
    public Mat executePipeline(Mat originalImage) {
        Mat currentImage = originalImage.clone();
        
        // Se a primeira etapa exigir grayscale e a imagem for colorida, converte
        if (currentImage.channels() > 1) {
             Imgproc.cvtColor(currentImage, currentImage, Imgproc.COLOR_BGR2GRAY);
        }

        // Passa por cada filtro na ordem
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
    
    // Getters específicos para o Hough chamar
    public double getDp() { return houghDp.getValue(); }
    public double getMinDist() { return houghMinDist.getValue(); }
    public double getParam1() { return houghP1.getValue(); }
    public double getParam2() { return houghP2.getValue(); }
    public int getMinRadius() { return (int)houghMinR.getValue(); }
    public int getMaxRadius() { return (int)houghMaxR.getValue(); }
}
