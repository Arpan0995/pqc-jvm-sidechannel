package org.pqcsidechannel;

import org.junit.jupiter.api.Test;
import org.pqcsidechannel.measure.Measurement;
import org.pqcsidechannel.measure.MeasurementTarget;
import org.pqcsidechannel.stats.LeakageReport;
import org.pqcsidechannel.targets.controls.ConstantTimeCompareTarget;
import org.pqcsidechannel.targets.controls.EarlyExitCompareTarget;

import java.util.Arrays;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The gate that validates the whole pipeline end to end: the positive control (a real timing leak)
 * must be flagged, and the negative control (a constant-time operation) must not.
 *
 * <p><b>Why repeated runs.</b> On an unpinned machine (no core affinity, no fixed CPU frequency) the
 * dudect max-over-crops statistic for a genuinely constant-time operation fluctuates around the 4.5
 * threshold from measurement noise — a single run can cross it by chance. Under the constant-time
 * null the t-statistic does not grow with sample size, so it does not reproduce; a real leak does.
 * These tests therefore judge the <em>median</em> max|t| across several independent measurement
 * processes, which is the same reproducibility rule the real ML-KEM study uses (design doc §7).
 */
class ControlValidationTest {

    private static final int LENGTH = 4096;
    private static final long N = 300_000;

    /** Median dudect max|t| across {@code reps} independent measurements with distinct seeds. */
    private static double medianMaxT(Supplier<MeasurementTarget> factory, int reps) {
        double[] ts = new double[reps];
        for (int r = 0; r < reps; r++) {
            Measurement.Config cfg = new Measurement.Config(
                    N, Math.min(N / 10, 200_000), 1000L + r, (int) Math.min(N, 2048));
            Measurement.Samples s = new Measurement(factory.get(), cfg).run();
            ts[r] = LeakageReport.of(s.targetName(), s.class0(), s.class1()).dudect().maxAbsT();
        }
        Arrays.sort(ts);
        return ts[reps / 2];
    }

    @Test
    void positiveControlReproduciblyLeaks() {
        double median = medianMaxT(() -> new EarlyExitCompareTarget(LENGTH), 3);
        System.out.printf("positive control: median max|t| = %.1f%n", median);
        assertTrue(median > 50.0,
                "positive control leak must be large and reproducible, got median max|t|=" + median);
    }

    @Test
    void negativeControlDoesNotReproduciblyLeak() {
        double median = medianMaxT(() -> new ConstantTimeCompareTarget(LENGTH), 5);
        System.out.printf("negative control: median max|t| = %.2f%n", median);
        assertTrue(median < LeakageReport.DEFAULT_THRESHOLD,
                "constant-time control must not reproducibly cross the threshold, got median max|t|=" + median);
    }

    @Test
    void pipelineStronglyDiscriminatesControls() {
        double positive = medianMaxT(() -> new EarlyExitCompareTarget(LENGTH), 3);
        double negative = medianMaxT(() -> new ConstantTimeCompareTarget(LENGTH), 3);
        System.out.printf("discrimination: positive=%.1f  negative=%.2f%n", positive, negative);
        assertTrue(positive > negative * 10.0,
                "leaky control should dwarf the constant-time control: positive=" + positive + " negative=" + negative);
    }
}
