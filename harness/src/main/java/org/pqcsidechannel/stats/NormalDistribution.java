package org.pqcsidechannel.stats;

/** Standard normal CDF via an erf approximation (Abramowitz & Stegun 7.1.26), used for p-values. */
public final class NormalDistribution {

    private static final double SQRT2 = Math.sqrt(2.0);

    private NormalDistribution() {
    }

    public static double cdf(double x) {
        return 0.5 * (1.0 + erf(x / SQRT2));
    }

    static double erf(double x) {
        double sign = Math.signum(x);
        double ax = Math.abs(x);
        double t = 1.0 / (1.0 + 0.3275911 * ax);
        double y = 1.0 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t
                - 0.284496736) * t + 0.254829592) * t * Math.exp(-ax * ax);
        return sign * y;
    }
}
