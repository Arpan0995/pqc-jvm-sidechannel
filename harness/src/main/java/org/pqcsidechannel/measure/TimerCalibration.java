package org.pqcsidechannel.measure;

/**
 * Characterizes {@link System#nanoTime()} on the current host: the call overhead and the smallest
 * non-zero delta it reports (its effective resolution). If the operation under test runs faster than
 * this resolution, single-shot timing is dominated by quantisation noise and the measurement design
 * must batch several operations per sample. Recording this makes that decision auditable.
 */
public final class TimerCalibration {

    public record Result(long overheadNanos, long minNonZeroDeltaNanos, long samples) {
        public String render() {
            return String.format(
                    "nanoTime calibration: back-to-back overhead ~%d ns, min non-zero delta %d ns (n=%d)",
                    overheadNanos, minNonZeroDeltaNanos, samples);
        }
    }

    private TimerCalibration() {
    }

    public static Result measure(int samples) {
        // Warm the call site.
        long acc = 0;
        for (int i = 0; i < 100_000; i++) {
            acc += System.nanoTime();
        }

        long minNonZero = Long.MAX_VALUE;
        long overheadSum = 0;
        long overheadCount = 0;
        long prev = System.nanoTime();
        for (int i = 0; i < samples; i++) {
            long now = System.nanoTime();
            long delta = now - prev;
            prev = now;
            if (delta > 0) {
                if (delta < minNonZero) {
                    minNonZero = delta;
                }
                overheadSum += delta;
                overheadCount++;
            }
        }
        // Prevent the warm-up loop from being optimised away.
        if (acc == Long.MIN_VALUE) {
            System.out.print("");
        }
        long overhead = overheadCount == 0 ? 0 : overheadSum / overheadCount;
        return new Result(overhead, minNonZero == Long.MAX_VALUE ? 0 : minNonZero, samples);
    }
}
