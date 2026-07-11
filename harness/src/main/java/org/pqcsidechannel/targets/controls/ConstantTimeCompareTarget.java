package org.pqcsidechannel.targets.controls;

import org.pqcsidechannel.measure.MeasurementTarget;

import java.util.random.RandomGenerator;

/**
 * NEGATIVE control — a constant-time operation that the pipeline MUST NOT flag.
 *
 * <p>Same setup as the positive control (same length, same fixed-vs-random slot classes), but the
 * comparison accumulates all byte differences into a single value with no data-dependent branch and a
 * fixed iteration count. Its timing does not depend on the input, so a correct pipeline reports no
 * leakage. Both class slots hold random values distinct from the secret, so both take the identical
 * full-scan path with nonzero XOR — there is no all-equal special case.
 *
 * <p>Together with the positive control this bounds the pipeline's behaviour from both sides: it must
 * catch the obvious leak and must clear the obvious non-leak.
 */
public final class ConstantTimeCompareTarget implements MeasurementTarget {

    private static final int DEFAULT_LENGTH = 4096;

    private final int length;
    private byte[] secret;

    public ConstantTimeCompareTarget() {
        this(DEFAULT_LENGTH);
    }

    public ConstantTimeCompareTarget(int length) {
        this.length = length;
    }

    @Override
    public String name() {
        return "negative-control:constant-time-compare";
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
        // Both classes are random and distinct from the secret; the class label carries no
        // content-independent difference. Seed the RNG stream identically per class is unnecessary —
        // the constant-time op does not depend on the bytes at all.
        for (int i = 0; i < length; i++) {
            backing[offset + i] = (byte) rng.nextInt();
        }
    }

    @Override
    public Object compute(byte[] backing, int offset, int slotBytes) {
        return constantTimeEquals(secret, backing, offset);
    }

    /** Fixed-length, branch-free accumulation: time is independent of the byte values. */
    private static boolean constantTimeEquals(byte[] secret, byte[] backing, int offset) {
        int diff = 0;
        for (int i = 0; i < secret.length; i++) {
            diff |= secret[i] ^ backing[offset + i];
        }
        return diff == 0;
    }
}
