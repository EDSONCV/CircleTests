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
     * Returns ALL parameters (Filters + Hough) in a flat list.
     * The RL Agent will interact with this list.
     */
    public List<OptParam> getAllParameters() {
        List<OptParam> allParams = new ArrayList<>();
        // Add filters parameters
        for (ImageFilter filter : filters) {
            allParams.addAll(filter.getParams());
        }
        // Add Hough  params
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

        // Step Zero: Original Image
        // We added a clone to ensure it won't be altered later
        steps.add(new StepResult(originalImage.clone(), "Original", "Input"));

        Mat currentImage = originalImage.clone();

        // If the first step requires grayscale and the image is in color, convert it
        // (This can be considered an implicit step or part of the setup)
        if (currentImage.channels() > 1) {
             Imgproc.cvtColor(currentImage, currentImage, Imgproc.COLOR_BGR2GRAY);
        }

        // 2. Loop among the Filters
        for (ImageFilter filter : filters) {
            // Processes
            Mat nextImage = filter.process(currentImage);
            
            // Assemble the parameter string for this specific filter.
            StringBuilder paramStr = new StringBuilder();
            for (OptParam p : filter.getParams()) {
                paramStr.append(p.toString()).append(" ");
            }

            // Saved to history (Cloning to ensure visual persistence)
            steps.add(new StepResult(nextImage.clone(), filter.getName(), paramStr.toString()));

            //Prepare for the next iteration.
            currentImage.release(); // Libera a anterior
            currentImage = nextImage;
        }

        return steps;
    }

    /**
     * Creates a deep (or functional) copy of the pipeline and applies the new parameters.
     * Essential for multithreading.
     */
    public ProcessingPipeline cloneWithParams(List<OptParam> newParams) {
        // 1. Cria uma nova instância do pipeline (vazio)
        ProcessingPipeline clone = new ProcessingPipeline();

        // 2. Add the same filters (new instances)
        for (ImageFilter filter : this.filters) {
            // Assuming your filters have a copy method or constructor.
            // If not, you need to create new instances manually here.
        // Generic example:
            if (filter instanceof GaussianBlurFilter) clone.addFilter(new GaussianBlurFilter());
            else if (filter instanceof BrightnessContrastFilter) clone.addFilter(new BrightnessContrastFilter());
            // ... other filters
        }

        // 3. Synchronizes the values ​​based on the received 'newParams' list.
        // The 'newParams' list contains names like "G_Kernel", "H_param1", etc.
        // The 'clone' has its own internal OptParams with these same names.
        clone.syncParameters(newParams);
        
        return clone;
    }

    /**
     * Iterates through the internal parameters of this pipeline and updates their values
     * if it finds a match (by name) in the provided list.
     */
    public void syncParameters(List<OptParam> sourceParams) {
        // List of all parameters for THIS pipeline (Hough + Filters)
        List<OptParam> myParams = this.getAllParameters();
        
        for (OptParam myParam : myParams) {
            // Search the received list to see if there is anyone with the same name.
            for (OptParam sourceParam : sourceParams) {
                if (myParam.getName().equals(sourceParam.getName())) {
                    // Updates the value (forces the raw value)
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
     * Defines the Houghs' strategy and adjusts the P2 parameter according to Opencv documentation
     * For HOUGH_GRADIENT_ALT P2 must be smaller than 1
     */
    public void setHoughMethod(int method) {
        this.houghMethod = method;

        // Updates the limits of parameter 2 dynamically
        if (this.houghMethod == Imgproc.HOUGH_GRADIENT) {
            // For HOUGH_GRADIENT, P2 is the accumulator threshold ( higher, circles more precise)
            // Typical range between 10.0 to 100.0 (or more depending on the noise)
            // Replace 'param2Opt' with the actual name of your internal OptParam variable
            this.houghP2 = new OptParam("param2", 10.0, 150.0, 30.0, 1.0,false);
        } else {
            // for HOUGH_GRADIENT_ALT, P2 is the measure of "perfection" (values close to 1 = more perfect).
            //Typical range: 0.5 to 1.0 (NEVER greater than 1.0)
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

