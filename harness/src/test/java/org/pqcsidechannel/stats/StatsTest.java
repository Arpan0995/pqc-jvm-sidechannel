package org.pqcsidechannel.stats;

import org.junit.jupiter.api.Test;

import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deterministic checks on the statistics themselves, using synthetic data (no timing). These pin the
 * math so a pipeline verdict can be trusted independently of the noisy measurement layer.
 */
class StatsTest {

    private static RandomGenerator rng() {
        return RandomGeneratorFactory.of("L64X128MixRandom").create(12345L);
    }

    private static long[] gaussian(RandomGenerator rng, int n, double mean, double sd) {
        long[] out = new long[n];
        for (int i = 0; i < n; i++) {
            out[i] = Math.round(mean + sd * rng.nextGaussian());
        }
        return out;
    }

    @Test
    void welchIsNearZeroForSameDistribution() {
        RandomGenerator rng = rng();
        long[] a = gaussian(rng, 50_000, 1000, 50);
        long[] b = gaussian(rng, 50_000, 1000, 50);
        WelchTTest w = WelchTTest.fromArrays(a, b);
        assertTrue(w.absT() < 4.5, "same distribution should not cross the leakage threshold, got t=" + w.t());
    }

    @Test
    void welchDetectsAShiftedMean() {
        RandomGenerator rng = rng();
        long[] a = gaussian(rng, 50_000, 1000, 50);
        long[] b = gaussian(rng, 50_000, 1010, 50); // 10 ns shift, ~0.2 SD
        WelchTTest w = WelchTTest.fromArrays(a, b);
        assertTrue(w.absT() > 4.5, "a real mean shift should be detected, got t=" + w.t());
    }

    @Test
    void dudectFlagsShiftAndClearsNoShift() {
        RandomGenerator rng = rng();
        long[] a = gaussian(rng, 40_000, 1000, 40);
        long[] same = gaussian(rng, 40_000, 1000, 40);
        long[] shifted = gaussian(rng, 40_000, 1015, 40);

        assertTrue(DudectCroppedTest.run(a, same).maxAbsT() < 4.5);
        assertTrue(DudectCroppedTest.run(a, shifted).maxAbsT() > 4.5);
    }

    @Test
    void mannWhitneyIsSymmetricAroundZeroForSameDistribution() {
        RandomGenerator rng = rng();
        long[] a = gaussian(rng, 30_000, 500, 30);
        long[] b = gaussian(rng, 30_000, 500, 30);
        MannWhitneyU.Result r = MannWhitneyU.run(a, b);
        assertTrue(r.absZ() < 4.0, "same distribution should give small |z|, got z=" + r.z());
    }

    @Test
    void mannWhitneyDetectsShift() {
        RandomGenerator rng = rng();
        long[] a = gaussian(rng, 30_000, 500, 30);
        long[] b = gaussian(rng, 30_000, 520, 30);
        MannWhitneyU.Result r = MannWhitneyU.run(a, b);
        assertTrue(r.absZ() > 4.0, "a real shift should give large |z|, got z=" + r.z());
    }

    @Test
    void erfMatchesKnownValues() {
        // erf(0)=0, erf(1)~0.8427, erf(2)~0.9953
        assertEquals(0.0, NormalDistribution.erf(0.0), 1e-6);
        assertEquals(0.8427, NormalDistribution.erf(1.0), 1e-3);
        assertEquals(0.9953, NormalDistribution.erf(2.0), 1e-3);
    }
}
