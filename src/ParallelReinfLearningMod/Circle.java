package ParallelReinfLearningMod;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Circle Structure
 */
class Circle {
    public double x, y, r;

    public Circle(double x, double y, double r) {
        this.x = x;
        this.y = y;
        this.r = r;
    }

    // Calculate overlap area roughly to check if it's the "same" circle
    public boolean matches(Circle other, double tolerance) {
        double dist = Math.sqrt(Math.pow(this.x - other.x, 2) + Math.pow(this.y - other.y, 2));
        double radiusDiff = Math.abs(this.r - other.r);
        return dist < tolerance && radiusDiff < tolerance;
    }
}
