package org.pqcsidechannel.targets.controls;

import org.pqcsidechannel.measure.MeasurementTarget;

import java.util.random.RandomGenerator;

/**
 * POSITIVE control — a deliberately non-constant-time operation that the pipeline MUST flag.
 *
 * <p>The operation is an early-exit byte comparison against a fixed secret (the classic {@code memcmp}
 * side channel). A class-0 slot equals the secret, so the comparison scans the entire array; a class-1
 * slot mismatches at the first byte, so it returns almost immediately. The timing gap between the two
 * classes is large and unmistakable.
 *
 * <p>This control calibrates the detection floor: if the harness cannot flag this obvious leak, no
 * negative result it produces on real cryptography can be trusted.
 */
public final class EarlyExitCompareTarget implements MeasurementTarget {

    /** Large enough that a full scan is well above nanoTime resolution. */
    private static final int DEFAULT_LENGTH = 4096;

    private final int length;
    private byte[] secret;

    public EarlyExitCompareTarget() {
        this(DEFAULT_LENGTH);
    }

    public EarlyExitCompareTarget(int length) {
        this.length = length;
    }

    @Override
    public String name() {
        return "positive-control:early-exit-compare";
    }

    @Override
    public void setup(RandomGenerator rng) {
        secret = new byte[length];
        rng.nextBytes(secret);
    }

    @Override
    public int slotBytes() {
        return length;
    }

    @Override
    public void fillSlot(byte[] backing, int offset, int klass, RandomGenerator rng) {
        // Class 0 equals the secret -> the early-exit scan runs the full length.
        System.arraycopy(secret, 0, backing, offset, length);
        if (klass == 1) {
            // Class 1 mismatches at byte 0 -> immediate exit.
            backing[offset] = (byte) (secret[0] ^ 0xFF);
        }
    }

    @Override
    public Object compute(byte[] backing, int offset, int slotBytes) {
        return earlyExitEquals(secret, backing, offset);
    }

    /** Returns early on the first mismatching byte — the source of the timing dependence. */
    private static boolean earlyExitEquals(byte[] secret, byte[] backing, int offset) {
        for (int i = 0; i < secret.length; i++) {
            if (secret[i] != backing[offset + i]) {
                return false;
            }
        }
        return true;
    }
}
