package filters;

import java.util.List;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

/**
 * Interface Strategy: Every filter should know how to process itself and list its parameters.
 */
public interface ImageFilter {
    Mat process(Mat input);
    List<OptParam> getParams();
    String getName();
}

