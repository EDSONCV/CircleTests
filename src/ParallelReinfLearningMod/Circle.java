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
    //
    public boolean matchesStrict(Circle other, double centerTolerance, double radiusTolerance) {
        // Distância Euclidiana entre os centros
        double distCenter = Math.sqrt(Math.pow(this.x - other.x, 2) + Math.pow(this.y - other.y, 2));

        // Diferença absoluta entre os raios
        double distRadius = Math.abs(this.r - other.r);

        return distCenter <= centerTolerance && distRadius <= radiusTolerance;
    }
    /**
     * Calculates Intersection over Union (IoU) between 2 cricles.
     * Returns values between 0.0 (no overlap, no match) and 1.0 (perfect match).
     */
    public double getIoU(Circle other) {
        double d = Math.sqrt(Math.pow(this.x - other.x, 2) + Math.pow(this.y - other.y, 2));
        double r1 = this.r;
        double r2 = other.r;

        // No overlap
        if (d >= r1 + r2) {
            return 0.0;
        }

        // complete overlap
        if (d <= Math.abs(r1 - r2)) {
            double minR = Math.min(r1, r2);
            double intersectionArea = Math.PI * minR * minR;
            double maxR = Math.max(r1, r2);
            double unionArea = Math.PI * maxR * maxR; // A união é a área do maior
            return intersectionArea / unionArea;
        }

        // Patial overlap
        // calculate angles (cossine laws
        double d2 = d * d;
        double r1Sq = r1 * r1;
        double r2Sq = r2 * r2;

        double angle1 = 2 * Math.acos((r1Sq + d2 - r2Sq) / (2 * r1 * d));
        double angle2 = 2 * Math.acos((r2Sq + d2 - r1Sq) / (2 * r2 * d));

        // Área de Interseção = (Área do Setor 1 - Área do Triângulo 1) + (Área do Setor 2 - Área do Triângulo 2)
        double area1 = 0.5 * r1Sq * (angle1 - Math.sin(angle1));
        double area2 = 0.5 * r2Sq * (angle2 - Math.sin(angle2));
        double intersectionArea = area1 + area2;

        // Área Total de Ambos (A + B)
        double areaTotalC1 = Math.PI * r1Sq;
        double areaTotalC2 = Math.PI * r2Sq;

        // Fórmula da União: A + B - Interseção
        double unionArea = areaTotalC1 + areaTotalC2 - intersectionArea;

        return intersectionArea / unionArea;
    }
}
