package filters.implementations;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class HoughCirclesJavaAlt {

    public static class CircleData implements Comparable<CircleData> {
        public float cx, cy, radius;
        public int votes;
        public long mask;

        public CircleData(float cx, float cy, float radius, int votes, long mask) {
            this.cx = cx;
            this.cy = cy;
            this.radius = radius;
            this.votes = votes;
            this.mask = mask;
        }

        @Override
        public int compareTo(CircleData other) {
            if (this.votes != other.votes) return Integer.compare(other.votes, this.votes);
            if (this.radius != other.radius) return Float.compare(other.radius, this.radius);
            if (this.cx != other.cx) return Float.compare(this.cx, other.cx);
            return Float.compare(this.cy, other.cy);
        }
    }

    public static List<CircleData> houghCirclesAlt(
            Mat image, double dp, double minDist,
            double minRadius, double maxRadius,
            double cannyThreshold, double perfectionRatio,
            Mat debugEdges) { // NOVO PARÂMETRO ADICIONADO

        final float finalDp = (float) Math.max(dp, 1.0);
        final float idp = 1.0f / finalDp;
        final int width = image.cols();
        final int height = image.rows();

        Mat dx = new Mat();
        Mat dy = new Mat();
        Mat edges = new Mat();

        Imgproc.Scharr(image, dx, CvType.CV_16S, 1, 0);
        Imgproc.Scharr(image, dy, CvType.CV_16S, 0, 1);
        Imgproc.Canny(dx, dy, edges, cannyThreshold / 2, cannyThreshold, true);

        // EXPORTAÇÃO DA IMAGEM DO CANNY PARA DEPURAÇÃO
        if (debugEdges != null) {
            edges.copyTo(debugEdges);
        }

        short[] dxData = new short[width * height];
        short[] dyData = new short[width * height];
        byte[] edgeData = new byte[width * height];

        dx.get(0, 0, dxData);
        dy.get(0, 0, dyData);
        edges.get(0, 0, edgeData);

        dx.release(); dy.release(); edges.release();

        int acols = (int) Math.ceil(width * idp);
        int arows = (int) Math.ceil(height * idp);
        float[] accum = new float[(arows + 1) * (acols + 1)];

        final float[] nzX = new float[width * height];
        final float[] nzY = new float[width * height];
        final float[] nzVx = new float[width * height];
        final float[] nzVy = new float[width * height];
        int nzCount = 0;

        final double minCos2 = perfectionRatio * perfectionRatio;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int idx = y * width + x;
                if (edgeData[idx] == 0) continue;

                float vx = dxData[idx];
                float vy = dyData[idx];
                if (vx == 0 && vy == 0) continue;

                nzX[nzCount] = x;
                nzY[nzCount] = y;
                nzVx[nzCount] = vx;
                nzVy[nzCount] = vy;
                nzCount++;

                float mag = (float) Math.sqrt(vx * vx + vy * vy);
                float sx = vx / mag;
                float sy = vy / mag;

                float x0 = x * idp;
                float y0 = y * idp;

                for (int k1 = 0; k1 < 2; k1++) {
                    float x1 = x0 + (float)minRadius * sx;
                    float y1 = y0 + (float)minRadius * sy;

                    for (int r = (int)minRadius; r <= maxRadius; x1 += sx, y1 += sy, r++) {
                        int x2 = (int) x1;
                        int y2 = (int) y1;
                        if (x2 >= 0 && x2 < acols - 1 && y2 >= 0 && y2 < arows - 1) {
                            float dx_frac = x1 - x2;
                            float dy_frac = y1 - y2;
                            int base = y2 * acols + x2;

                            accum[base] += (1 - dx_frac) * (1 - dy_frac);
                            accum[base + 1] += dx_frac * (1 - dy_frac);
                            accum[base + acols] += (1 - dx_frac) * dy_frac;
                            accum[base + acols + 1] += dx_frac * dy_frac;
                        }
                    }
                    sx = -sx; sy = -sy;
                }
            }
        }

        Mat accumMat = new Mat(arows, acols, CvType.CV_32F);
        accumMat.put(0, 0, accum);
        Mat accumMax = new Mat();
        int niters = Math.max((int) Math.ceil(minDist * idp), 1);
        Imgproc.dilate(accumMat, accumMax, new Mat(), new Point(-1, -1), niters, Core.BORDER_CONSTANT, new Scalar(0));

        float[] maxData = new float[arows * acols];
        accumMax.get(0, 0, maxData);
        accumMat.release(); accumMax.release();

        List<Point> centers = new ArrayList<>();
        for (int y = 0; y < arows; y++) {
            for (int x = 0; x < acols; x++) {
                int base = y * acols + x;
                if (accum[base] > 10.0f && accum[base] == maxData[base]) {
                    centers.add(new Point(x * finalDp, y * finalDp));
                }
            }
        }

        final int totalEdges = nzCount;
        final float minR2 = (float) (minRadius * minRadius);
        final float maxR2 = (float) (maxRadius * maxRadius);

        List<CircleData> circlesEst = centers.parallelStream().map(center -> {
            float cx = (float) center.x;
            float cy = (float) center.y;

            int numBins = (int) Math.ceil(maxRadius - minRadius) + 1;
            int[] binWeights = new int[numBins];
            long[] binMasks = new long[numBins];
            float[] binSumR = new float[numBins];

            for (int i = 0; i < totalEdges; i++) {
                float dxC = nzX[i] - cx;
                float dyC = nzY[i] - cy;
                float r2 = dxC * dxC + dyC * dyC;

                if (r2 < minR2 || r2 > maxR2) continue;

                float mag2 = nzVx[i]*nzVx[i] + nzVy[i]*nzVy[i];
                float dv = dxC * nzVx[i] + dyC * nzVy[i];

                if ((dv * dv) < (minCos2 * mag2 * r2)) continue;

                float r = (float) Math.sqrt(r2);
                int bin = (int) Math.round(r - minRadius);

                if (bin >= 0 && bin < numBins) {
                    binWeights[bin]++;
                    binSumR[bin] += r;

                    float angle = (float) (Math.atan2(dyC, dxC) * (64.0 / (2 * Math.PI)));
                    if (angle < 0) angle += 64;
                    int bitPos = ((int) angle) % 64;
                    binMasks[bin] |= (1L << bitPos);
                }
            }

            int bestWeight = 0;
            long bestMask = 0;
            float bestRadius = 0;
            int maxPopcount = 0;

            for (int b = 0; b < numBins; b++) {
                if (binWeights[b] == 0) continue;

                long combinedMask = binMasks[b];
                int combinedWeight = binWeights[b];
                float combinedSumR = binSumR[b];

                if (b > 0) {
                    combinedMask |= binMasks[b - 1];
                    combinedWeight += binWeights[b - 1];
                    combinedSumR += binSumR[b - 1];
                }
                if (b < numBins - 1) {
                    combinedMask |= binMasks[b + 1];
                    combinedWeight += binWeights[b + 1];
                    combinedSumR += binSumR[b + 1];
                }

                int popcount = Long.bitCount(combinedMask);

                if (popcount >= 30) {
                    if (popcount > maxPopcount || (popcount == maxPopcount && combinedWeight > bestWeight)) {
                        maxPopcount = popcount;
                        bestWeight = combinedWeight;
                        bestMask = combinedMask;
                        bestRadius = combinedSumR / combinedWeight;
                    }
                }
            }

            if (maxPopcount >= 30) {
                return new CircleData(cx, cy, bestRadius, bestWeight, bestMask);
            }
            return null;
        }).filter(Objects::nonNull).sorted().collect(Collectors.toList());

        removeOverlappingCircles(circlesEst, 0.3f);

        List<CircleData> finalCircles = new ArrayList<>();
        float minDist2 = (float) (minDist * minDist);

        for (CircleData circle : circlesEst) {
            boolean goodPoint = true;
            for (CircleData keptCircle : finalCircles) {
                float dxC = circle.cx - keptCircle.cx;
                float dyC = circle.cy - keptCircle.cy;
                if (dxC * dxC + dyC * dyC < minDist2) {
                    goodPoint = false;
                    break;
                }
            }
            if (goodPoint) finalCircles.add(circle);
        }

        removeOverlappingCircles(finalCircles, 0.3f);
        return finalCircles;
    }

    private static void removeOverlappingCircles(List<CircleData> circles, float overlapTolerance) {
        if (circles.size() <= 1) return;

        boolean[] keep = new boolean[circles.size()];
        Arrays.fill(keep, true);
        final float margin = 2.0f;

        for (int i = 0; i < circles.size(); i++) {
            if (!keep[i]) continue;
            for (int j = i + 1; j < circles.size(); j++) {
                if (!keep[j]) continue;

                float dx = circles.get(i).cx - circles.get(j).cx;
                float dy = circles.get(i).cy - circles.get(j).cy;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                float ri = circles.get(i).radius;
                float rj = circles.get(j).radius;

                if (dist + rj <= ri + margin) {
                    keep[j] = false;
                    continue;
                } else if (dist + ri <= rj + margin) {
                    keep[i] = false;
                    break;
                }

                if (dist < (ri + rj) * overlapTolerance) {
                    keep[j] = false;
                }
            }
        }

        List<CircleData> filtered = new ArrayList<>();
        for (int i = 0; i < circles.size(); i++) {
            if (keep[i]) filtered.add(circles.get(i));
        }
        circles.clear();
        circles.addAll(filtered);
    }
}