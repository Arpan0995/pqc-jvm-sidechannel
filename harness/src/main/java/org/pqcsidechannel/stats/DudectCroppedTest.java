package org.pqcsidechannel.stats;

import java.util.Arrays;

/**
 * The dudect leakage test: Welch's t computed both on the full sample and on many high-percentile
 * "crops" of the timing distribution, reporting the maximum {@code |t|} across all crops.
 *
 * <p>Cropping discards the slow tail of measurements (caused by preemption, GC, migration) at a set
 * of thresholds concentrated near the top of the distribution. A genuine data-dependent difference
 * usually shows up most strongly once tail noise is removed, so the max-over-crops statistic is more
 * sensitive than a single uncropped t-test. This mirrors the percentile scheme in the reference
 * dudect implementation (Reparaz, Balasch, Verbauwhede, 2017).
 *
 * <p>Each crop is evaluated in O(log N) using per-class sorted arrays and prefix sums, so scanning
 * many thresholds over millions of samples stays cheap.
 */
public final class DudectCroppedTest {

    /** Number of percentile crops, matching the reference dudect default. */
    public static final int NUMBER_PERCENTILES = 100;

    /** Minimum samples per class within a crop for a crop's t-test to be considered valid. */
    private static final int MIN_PER_CLASS = 2;

    private DudectCroppedTest() {
    }

    public record Result(double maxAbsT,
                         double thresholdAtMax,
                         double percentileRankAtMax,
                         WelchTTest uncropped,
                         int cropsEvaluated) {
    }

    public static Result run(long[] class0, long[] class1) {
        long[] a = class0.clone();
        long[] b = class1.clone();
        Arrays.sort(a);
        Arrays.sort(b);

        double[] prefixSumA = prefixSum(a);
        double[] prefixSqA = prefixSumSq(a);
        double[] prefixSumB = prefixSum(b);
        double[] prefixSqB = prefixSumSq(b);

        // Uncropped test over the full samples.
        WelchTTest uncropped = WelchTTest.fromMoments(
                a.length, prefixSumA[a.length], prefixSqA[a.length],
                b.length, prefixSumB[b.length], prefixSqB[b.length]);

        double maxAbsT = uncropped.absT();
        double thresholdAtMax = Double.POSITIVE_INFINITY;
        double percentileRankAtMax = 1.0;
        int cropsEvaluated = 1;

        long[] thresholds = percentileThresholds(a, b);
        for (int i = 0; i < thresholds.length; i++) {
            long tau = thresholds[i];

            int cA = countLessOrEqual(a, tau);
            int cB = countLessOrEqual(b, tau);
            if (cA < MIN_PER_CLASS || cB < MIN_PER_CLASS) {
                continue;
            }

            WelchTTest cropped = WelchTTest.fromMoments(
                    cA, prefixSumA[cA], prefixSqA[cA],
                    cB, prefixSumB[cB], prefixSqB[cB]);
            cropsEvaluated++;

            if (cropped.absT() > maxAbsT) {
                maxAbsT = cropped.absT();
                thresholdAtMax = tau;
                percentileRankAtMax = percentileRankFor(i);
            }
        }

        return new Result(maxAbsT, thresholdAtMax, percentileRankAtMax, uncropped, cropsEvaluated);
    }

    /**
     * Thresholds concentrated toward the high end of the merged distribution, matching dudect's
     * mapping percentile_rank(i) = 1 - 0.5^(10 * (i+1) / NUMBER_PERCENTILES).
     */
    private static long[] percentileThresholds(long[] sortedA, long[] sortedB) {
        long[] merged = new long[sortedA.length + sortedB.length];
        System.arraycopy(sortedA, 0, merged, 0, sortedA.length);
        System.arraycopy(sortedB, 0, merged, sortedA.length, sortedB.length);
        Arrays.sort(merged);

        long[] thresholds = new long[NUMBER_PERCENTILES];
        for (int i = 0; i < NUMBER_PERCENTILES; i++) {
            double rank = percentileRankFor(i);
            int idx = (int) (rank * merged.length);
            if (idx >= merged.length) idx = merged.length - 1;
            thresholds[i] = merged[idx];
        }
        return thresholds;
    }

    private static double percentileRankFor(int i) {
        return 1.0 - Math.pow(0.5, 10.0 * (i + 1) / NUMBER_PERCENTILES);
    }

    /** Number of elements in {@code sorted} that are <= tau (sorted ascending). */
    private static int countLessOrEqual(long[] sorted, long tau) {
        int lo = 0;
        int hi = sorted.length; // first index strictly greater than tau
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (sorted[mid] <= tau) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    /** prefix[k] = sum of the first k sorted elements (prefix[0] = 0). */
    private static double[] prefixSum(long[] sorted) {
        double[] p = new double[sorted.length + 1];
        for (int i = 0; i < sorted.length; i++) {
            p[i + 1] = p[i] + (double) sorted[i];
        }
        return p;
    }

    private static double[] prefixSumSq(long[] sorted) {
        double[] p = new double[sorted.length + 1];
        for (int i = 0; i < sorted.length; i++) {
            double d = (double) sorted[i];
            p[i + 1] = p[i] + d * d;
        }
        return p;
    }
}
