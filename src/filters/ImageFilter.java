package filters;

import java.util.List;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

/**
 * Interface Strategy: Todo filtro deve saber se processar e listar seus parâmetros.
 */
public interface ImageFilter {
    Mat process(Mat input);
    List<OptParam> getParams();
    String getName();
}

