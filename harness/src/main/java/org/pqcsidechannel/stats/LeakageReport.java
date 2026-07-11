package org.pqcsidechannel.stats;

import java.util.Locale;

/**
 * Combines the three leakage tests over one pair of timing samples into a single-run verdict.
 *
 * <p>The dudect cropped-t maximum is the primary, most-sensitive statistic, but taking the maximum
 * over ~100 percentile crops inflates its false-positive rate near the threshold: on unpinned
 * hardware a genuinely constant-time operation can graze {@code |t| > 4.5} while its first-order
 * signal and effect size are essentially zero. To avoid crying leak on such noise, the single-run
 * verdict is three-level:
 * <ul>
 *   <li><b>LEAKY</b> — the dudect flag is corroborated by the first-order TVLA t or the Mann–Whitney
 *       z also crossing the threshold.</li>
 *   <li><b>MARGINAL</b> — only the dudect cropped-max crosses; not corroborated. Treat as noise near
 *       threshold and resolve with the reproducibility protocol (design doc §7): a real leak crosses
 *       reproducibly and grows with N, noise does not.</li>
 *   <li><b>CLEAN</b> — the dudect max is below threshold.</li>
 * </ul>
 * Cohen's d reports the effect size in standard-deviation units, so a reader can see when a
 * statistically flagged difference is operationally meaningless.
 */
public record LeakageReport(String targetName,
                            long n0, long n1,
                            DudectCroppedTest.Result dudect,
                            WelchTTest tvla,
                            MannWhitneyU.Result mannWhitney,
                            double cohenD,
                            double threshold,
                            Verdict verdict) {

    public static final double DEFAULT_THRESHOLD = 4.5;

    public enum Verdict {
        LEAKY, MARGINAL, CLEAN
    }

    public static LeakageReport of(String targetName, long[] class0, long[] class1) {
        DudectCroppedTest.Result dudect = DudectCroppedTest.run(class0, class1);
        WelchTTest tvla = WelchTTest.fromArrays(class0, class1);
        MannWhitneyU.Result mwu = MannWhitneyU.run(class0, class1);
        double d = cohenD(tvla);

        boolean dudectFlag = dudect.maxAbsT() > DEFAULT_THRESHOLD;
        boolean corroborated = tvla.absT() > DEFAULT_THRESHOLD || mwu.absZ() > DEFAULT_THRESHOLD;
        Verdict verdict = dudectFlag ? (corroborated ? Verdict.LEAKY : Verdict.MARGINAL) : Verdict.CLEAN;

        return new LeakageReport(targetName, class0.length, class1.length,
                dudect, tvla, mwu, d, DEFAULT_THRESHOLD, verdict);
    }

    /** True only for a corroborated leak. MARGINAL and CLEAN both return false. */
    public boolean leaky() {
        return verdict == Verdict.LEAKY;
    }

    private static double cohenD(WelchTTest w) {
        double pooledVar = ((w.n0() - 1) * w.var0() + (w.n1() - 1) * w.var1())
                / (w.n0() + w.n1() - 2);
        double sd = Math.sqrt(pooledVar);
        return sd == 0.0 ? 0.0 : (w.mean0() - w.mean1()) / sd;
    }

    public String render() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "Leakage report: %s%n", targetName));
        sb.append(String.format(Locale.ROOT, "  samples: class0=%d  class1=%d%n", n0, n1));
        sb.append(String.format(Locale.ROOT,
                "  dudect cropped-t : max|t|=%.3f  (crop p=%.5f, threshold=%s ns, crops=%d)%n",
                dudect.maxAbsT(), dudect.percentileRankAtMax(),
                Double.isInfinite(dudect.thresholdAtMax()) ? "none" : String.valueOf((long) dudect.thresholdAtMax()),
                dudect.cropsEvaluated()));
        sb.append(String.format(Locale.ROOT,
                "  TVLA Welch-t     : t=%.3f  (mean0=%.1f ns, mean1=%.1f ns, df=%.0f)%n",
                tvla.t(), tvla.mean0(), tvla.mean1(), tvla.df()));
        sb.append(String.format(Locale.ROOT,
                "  Mann-Whitney U   : z=%.3f  (p=%.3e)%n",
                mannWhitney.z(), mannWhitney.pTwoSided()));
        sb.append(String.format(Locale.ROOT, "  effect size      : Cohen's d=%.4f%n", cohenD));
        sb.append(String.format(Locale.ROOT, "  VERDICT          : %s%s (threshold |t|>%.1f)%n",
                verdict, verdictHint(), threshold));
        return sb.toString();
    }

    private String verdictHint() {
        return switch (verdict) {
            case LEAKY -> " — dudect flag corroborated by first-order test";
            case MARGINAL -> " — dudect grazed threshold but uncorroborated (TVLA t="
                    + String.format(Locale.ROOT, "%.2f", tvla.t()) + ", d="
                    + String.format(Locale.ROOT, "%.4f", cohenD) + "); confirm via repeated runs";
            case CLEAN -> " (no leakage detected)";
        };
    }
}
