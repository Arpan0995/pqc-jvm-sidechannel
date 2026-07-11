package org.pqcsidechannel.measure;

import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

/**
 * Collects interleaved fixed-vs-random timing samples for a {@link MeasurementTarget}, following the
 * dudect protocol with a scattered input pool.
 *
 * <p>Design points that make the measurement trustworthy:
 * <ul>
 *   <li><b>Scattered slot pool.</b> A fixed number of input slots live in one flat backing array.
 *       Each slot is assigned a class at random, so both classes are spread across the same set of
 *       addresses and cache states — memory layout is decorrelated from the class label rather than
 *       confounded with it.</li>
 *   <li><b>Randomized access order.</b> Each measurement pass visits the slots in a fresh random
 *       order, so slow drift (thermal, DVFS, background load) is shared evenly between the classes
 *       instead of aliasing into the class variable.</li>
 *   <li><b>Warm-up.</b> Untimed iterations run first so the JIT reaches a steady tier before timing
 *       (a no-op under {@code -Xint}).</li>
 *   <li><b>DCE guard.</b> Every result is pushed to a {@link Sink} whose final value is exposed so
 *       the compiler cannot delete the timed work.</li>
 * </ul>
 */
public final class Measurement {

    public record Samples(String targetName, long[] class0, long[] class1, long sinkValue) {
    }

    public record Config(long measurements, long warmup, long seed, int poolSlots) {
        public static Config of(long measurements) {
            long warmup = Math.min(measurements / 10, 200_000);
            int pool = (int) Math.min(measurements, 2048);
            return new Config(measurements, warmup, 0xA11CE5EEDL, pool);
        }
    }

    private final MeasurementTarget target;
    private final Config config;

    public Measurement(MeasurementTarget target, Config config) {
        this.target = target;
        this.config = config;
    }

    public Samples run() {
        RandomGenerator rng = RandomGeneratorFactory.of("L64X128MixRandom").create(config.seed());
        target.setup(rng);

        int slotBytes = target.slotBytes();
        int poolSlots = Math.max(2, config.poolSlots());

        // Build the scattered input pool: one flat array, one class label per slot chosen at random.
        byte[] pool = new byte[poolSlots * slotBytes];
        int[] slotClass = new int[poolSlots];
        for (int s = 0; s < poolSlots; s++) {
            slotClass[s] = rng.nextBoolean() ? 1 : 0;
            target.fillSlot(pool, s * slotBytes, slotClass[s], rng);
        }

        int[] order = new int[poolSlots];
        for (int s = 0; s < poolSlots; s++) {
            order[s] = s;
        }

        Sink sink = new Sink();

        // Warm-up over the pool (untimed).
        for (long i = 0; i < config.warmup(); i++) {
            int s = (int) (i % poolSlots);
            sink.consume(target.compute(pool, s * slotBytes, slotBytes));
        }

        long total = config.measurements();
        long[] c0 = new long[(int) Math.min(total, Integer.MAX_VALUE)];
        long[] c1 = new long[(int) Math.min(total, Integer.MAX_VALUE)];
        int n0 = 0;
        int n1 = 0;

        long done = 0;
        while (done < total) {
            shuffle(order, rng);
            for (int k = 0; k < poolSlots && done < total; k++) {
                int s = order[k];
                int offset = s * slotBytes;

                long start = System.nanoTime();
                Object result = target.compute(pool, offset, slotBytes);
                long elapsed = System.nanoTime() - start;

                sink.consume(result);

                if (slotClass[s] == 1) {
                    c1[n1++] = elapsed;
                } else {
                    c0[n0++] = elapsed;
                }
                done++;
            }
        }

        return new Samples(target.name(), trim(c0, n0), trim(c1, n1), sink.value());
    }

    private static void shuffle(int[] a, RandomGenerator rng) {
        for (int i = a.length - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int t = a[i];
            a[i] = a[j];
            a[j] = t;
        }
    }

    private static long[] trim(long[] arr, int n) {
        if (n == arr.length) {
            return arr;
        }
        long[] out = new long[n];
        System.arraycopy(arr, 0, out, 0, n);
        return out;
    }
}
