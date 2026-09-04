package filters.implementations;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class HoughCirclesJavaOpt {

    public static class CircleData implements Comparable<CircleData> {
        public float cx, cy, radius;
        public int votes;

        public CircleData(float cx, float cy, float radius, int votes) {
            this.cx = cx;
            this.cy = cy;
            this.radius = radius;
            this.votes = votes;
        }

        @Override
        public int compareTo(CircleData other) {
            if (this.votes != other.votes) return Integer.compare(other.votes, this.votes);
            if (this.radius != other.radius) return Float.compare(other.radius, this.radius);
            if (this.cx != other.cx) return Float.compare(this.cx, other.cx);
            return Float.compare(this.cy, other.cy);
        }
    }

    public static List<CircleData> houghCirclesGradientOpt(
            Mat image, float dp, float minDist,
            int minRadius, int maxRadius,
            int cannyThreshold, int accThreshold,
            Mat debugEdges) { // // new parameter for debug

        final float finalDp = Math.max(dp, 1.0f);
        final float idp = 1.0f / finalDp;

        final int width = image.cols();
        final int height = image.rows();

        Mat dx = new Mat();
        Mat dy = new Mat();
        Mat edges = new Mat();

        Imgproc.Sobel(image, dx, CvType.CV_16S, 1, 0, 3, 1, 0, Core.BORDER_REPLICATE);
        Imgproc.Sobel(image, dy, CvType.CV_16S, 0, 1, 3, 1, 0, Core.BORDER_REPLICATE);
        Imgproc.Canny(dx, dy, edges, Math.max(1, cannyThreshold / 2), cannyThreshold, false);

        // Export Canny image for debug
        if (debugEdges != null) {
            edges.copyTo(debugEdges);
        }

        short[] dxData = new short[width * height];
        short[] dyData = new short[width * height];
        byte[] edgeData = new byte[width * height];

        dx.get(0, 0, dxData);
        dy.get(0, 0, dyData);
        edges.get(0, 0, edgeData);

        dx.release();
        dy.release();
        edges.release();

        int acols = (int) Math.ceil(width * idp);
        int arows = (int) Math.ceil(height * idp);
        int astep = acols + 2;
        int[] accum = new int[(arows + 2) * astep];

        final int[] edgeX = new int[width * height];
        final int[] edgeY = new int[width * height];
        int edgeCount = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int idx = y * width + x;
                if (edgeData[idx] == 0) continue;

                edgeX[edgeCount] = x;
                edgeY[edgeCount] = y;
                edgeCount++;

                float vx = dxData[idx];
                float vy = dyData[idx];

                if (vx == 0 && vy == 0) continue;

                float mag = (float) Math.sqrt(vx * vx + vy * vy);
                if (mag < 1.0f) continue;

                int sx = Math.round((vx * idp) * 1024 / mag);
                int sy = Math.round((vy * idp) * 1024 / mag);
                int x0 = Math.round((x * idp) * 1024);
                int y0 = Math.round((y * idp) * 1024);

                for (int k1 = 0; k1 < 2; k1++) {
                    int x1 = x0 + minRadius * sx;
                    int y1 = y0 + minRadius * sy;

                    for (int r = minRadius; r <= maxRadius; x1 += sx, y1 += sy, r++) {
                        int x2 = x1 >> 10;
                        int y2 = y1 >> 10;
                        if (x2 >= 0 && x2 < acols && y2 >= 0 && y2 < arows) {
                            accum[(y2 + 1) * astep + (x2 + 1)]++;
                        } else {
                            break;
                        }
                    }
                    sx = -sx;
                    sy = -sy;
                }
            }
        }

        List<Integer> centers = new ArrayList<>();
        for (int y = 1; y <= arows; y++) {
            for (int x = 1; x <= acols; x++) {
                int base = y * astep + x;
                if (accum[base] > accThreshold &&
                        accum[base] > accum[base - 1] && accum[base] >= accum[base + 1] &&
                        accum[base] > accum[base - astep] && accum[base] >= accum[base + astep]) {
                    centers.add(base);
                }
            }
        }

        final int nBinsPerDr = 10;
        final int nBins = Math.round((maxRadius - minRadius) / finalDp * nBinsPerDr) + 1;
        final float minR2 = minRadius * minRadius;
        final float maxR2 = maxRadius * maxRadius;
        final int totalEdges = edgeCount;

        final ThreadLocal<int[]> localBins = ThreadLocal.withInitial(() -> new int[nBins]);

        List<CircleData> circlesEst = centers.parallelStream().map(ofs -> {
            int y = ofs / astep;
            int x = ofs - y * astep;

            float cx = (x - 1 + 0.5f) * finalDp;
            float cy = (y - 1 + 0.5f) * finalDp;

            int[] bins = localBins.get();
            Arrays.fill(bins, 0);

            int maxCount = 0;
            float rBest = 0;

            for (int i = 0; i < totalEdges; i++) {
                float dxPt = cx - edgeX[i];
                float dyPt = cy - edgeY[i];
                float r2 = dxPt * dxPt + dyPt * dyPt;

                if (r2 >= minR2 && r2 <= maxR2) {
                    float r = (float) Math.sqrt(r2);
                    int bin = Math.max(0, Math.min(nBins - 1, Math.round((r - minRadius) * idp * nBinsPerDr)));
                    bins[bin]++;
                }
            }

            for (int j = nBins - 1; j > 0; j--) {
                if (bins[j] > 0) {
                    int upbin = j;
                    int curCount = 0;
                    for (; j > upbin - nBinsPerDr && j >= 0; j--) {
                        curCount += bins[j];
                    }
                    float rCur = (upbin + j) / 2.0f / nBinsPerDr * finalDp + minRadius;
                    if ((curCount * rBest >= maxCount * rCur) || (rBest < 1e-6f && curCount >= maxCount)) {
                        rBest = rCur;
                        maxCount = curCount;
                    }
                }
            }

            if (maxCount > accThreshold) {
                return new CircleData(cx, cy, rBest, maxCount);
            }
            return null;
        }).filter(Objects::nonNull).sorted().collect(Collectors.toList());

        removeInnerCircles(circlesEst);

        List<CircleData> finalCircles = new ArrayList<>();
        float minDist2 = minDist * minDist;

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
            if (goodPoint) {
                finalCircles.add(circle);
            }
        }

        return finalCircles;
    }

    private static void removeInnerCircles(List<CircleData> circles) {
        if (circles.size() <= 1) return;

        boolean[] keep = new boolean[circles.size()];
        Arrays.fill(keep, true);

        final float margin = 2.0f;
        final float overlapTolerance = 0.3f;

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
            if (keep[i]) {
                filtered.add(circles.get(i));
            }
        }
        circles.clear();
        circles.addAll(filtered);
    }
}