package org.pqcsidechannel.stats;

import java.util.Arrays;

/**
 * Mann–Whitney U (Wilcoxon rank-sum) test — the non-parametric backstop for the leakage assessment.
 *
 * <p>Unlike Welch's t, it makes no normality assumption and is robust to the heavy right tail of JVM
 * timing distributions. It is deterministic and evaluates the full sample in O(N log N). We report
 * the normal approximation with tie correction, appropriate at our sample sizes (N well into the
 * thousands or millions), where ties are common because timings are quantised to nanoseconds.
 */
public final class MannWhitneyU {

    private MannWhitneyU() {
    }

    public record Result(double u0, double z, double pTwoSided, long n0, long n1) {
        public double absZ() {
            return Math.abs(z);
        }
    }

    public static Result run(long[] class0, long[] class1) {
        int n0 = class0.length;
        int n1 = class1.length;
        int n = n0 + n1;

        // Tag each value with its class (0 or 1), then sort by value to assign ranks.
        long[][] tagged = new long[n][];
        for (int i = 0; i < n0; i++) tagged[i] = new long[]{class0[i], 0};
        for (int i = 0; i < n1; i++) tagged[n0 + i] = new long[]{class1[i], 1};
        Arrays.sort(tagged, (x, y) -> Long.compare(x[0], y[0]));

        // Assign average ranks (1-based), handling ties, and accumulate the tie correction term
        // sum(t^3 - t) over tie groups of size t.
        double rankSum0 = 0.0;
        double tieTerm = 0.0;
        int i = 0;
        while (i < n) {
            int j = i;
            while (j + 1 < n && tagged[j + 1][0] == tagged[i][0]) {
                j++;
            }
            int groupSize = j - i + 1;
            double avgRank = (i + 1 + j + 1) / 2.0; // average of ranks (i+1)..(j+1)
            for (int k = i; k <= j; k++) {
                if (tagged[k][1] == 0) {
                    rankSum0 += avgRank;
                }
            }
            double t = groupSize;
            tieTerm += t * t * t - t;
            i = j + 1;
        }

        double u0 = rankSum0 - (double) n0 * (n0 + 1) / 2.0;

        double mu = (double) n0 * n1 / 2.0;
        // Variance with tie correction.
        double variance = ((double) n0 * n1 / 12.0)
                * ((n + 1) - tieTerm / ((double) n * (n - 1)));
        double sigma = Math.sqrt(variance);

        double z;
        if (sigma == 0.0) {
            z = 0.0;
        } else {
            // Continuity correction toward the mean.
            double diff = u0 - mu;
            double corrected = diff - Math.signum(diff) * 0.5;
            z = corrected / sigma;
        }

        double p = 2.0 * (1.0 - NormalDistribution.cdf(Math.abs(z)));
        return new Result(u0, z, p, n0, n1);
    }
}
