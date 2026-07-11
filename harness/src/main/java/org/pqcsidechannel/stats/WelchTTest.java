package org.pqcsidechannel.stats;

/**
 * Welch's two-sample t-test (unequal variances). This is the statistical core shared by the TVLA
 * leakage assessment and by the dudect cropped-percentile test.
 *
 * <p>The t-statistic is {@code (mean0 - mean1) / sqrt(var0/n0 + var1/n1)}. Under the constant-time
 * null hypothesis the two input classes have the same expected timing, so {@code t} is near zero; a
 * large {@code |t|} is evidence of secret-dependent timing. The conventional dudect decision
 * threshold is {@code |t| > 4.5}.
 */
public record WelchTTest(double t,
                         double df,
                         double mean0, double var0, long n0,
                         double mean1, double var1, long n1) {

    /** Compute the test directly from two samples of timing measurements (nanoseconds). */
    public static WelchTTest fromArrays(long[] a, long[] b) {
        Moments ma = Moments.of(a);
        Moments mb = Moments.of(b);
        return fromMoments(ma.n, ma.sum, ma.sumSq, mb.n, mb.sum, mb.sumSq);
    }

    /**
     * Compute the test from sufficient statistics. This lets the dudect cropped test evaluate many
     * percentile crops in O(log N) each via prefix sums, instead of re-scanning the samples.
     */
    public static WelchTTest fromMoments(long n0, double sum0, double sumSq0,
                                         long n1, double sum1, double sumSq1) {
        double mean0 = sum0 / n0;
        double mean1 = sum1 / n1;
        // Sample variance with Bessel's correction (ddof = 1).
        double var0 = (sumSq0 - sum0 * sum0 / n0) / (n0 - 1);
        double var1 = (sumSq1 - sum1 * sum1 / n1) / (n1 - 1);
        if (var0 < 0) var0 = 0; // guard against tiny negative from floating-point cancellation
        if (var1 < 0) var1 = 0;

        double se0 = var0 / n0;
        double se1 = var1 / n1;
        double denom = Math.sqrt(se0 + se1);
        double t = denom == 0.0 ? 0.0 : (mean0 - mean1) / denom;

        // Welch–Satterthwaite degrees of freedom (reported; the decision rule uses |t|, not df).
        double dfNum = (se0 + se1) * (se0 + se1);
        double dfDen = (se0 * se0) / (n0 - 1) + (se1 * se1) / (n1 - 1);
        double df = dfDen == 0.0 ? Double.NaN : dfNum / dfDen;

        return new WelchTTest(t, df, mean0, var0, n0, mean1, var1, n1);
    }

    public double absT() {
        return Math.abs(t);
    }

    /** First and second raw moments of a sample, used to feed {@link #fromMoments}. */
    record Moments(long n, double sum, double sumSq) {
        static Moments of(long[] x) {
            double sum = 0.0;
            double sumSq = 0.0;
            for (long v : x) {
                double d = (double) v;
                sum += d;
                sumSq += d * d;
            }
            return new Moments(x.length, sum, sumSq);
        }
    }
}
