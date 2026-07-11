package org.pqcsidechannel.targets.mlkem;

import org.pqcsidechannel.measure.MeasurementTarget;

import java.util.random.RandomGenerator;

/**
 * Primary ML-KEM-768 target: <b>fixed valid vs. random valid</b> ciphertexts.
 *
 * <p>Class 0 is one fixed valid ciphertext; class 1 is a fresh valid ciphertext per slot. Both
 * decapsulate down the success path of the Fujisaki–Okamoto transform (re-encryption matches). The
 * question this answers is the first-order one: does ML-KEM decapsulation time depend on <em>which</em>
 * (valid) ciphertext is being decapsulated — i.e., on secret-dependent intermediate values computed
 * from the ciphertext? A constant-time implementation shows no difference.
 */
public final class MlKem768DecapValidTarget implements MeasurementTarget {

    private final long seed;
    private MlKemKeys keys;
    private byte[] fixedCiphertext; // class 0
    private byte[] ciphertext;      // reusable decap input, fixed address for both classes

    public MlKem768DecapValidTarget() {
        this(0xC0DECAFEL);
    }

    public MlKem768DecapValidTarget(long seed) {
        this.seed = seed;
    }

    @Override
    public String name() {
        return "ml-kem-768:decap-valid-fixed-vs-random";
    }

    @Override
    public void setup(RandomGenerator rng) {
        keys = new MlKemKeys(seed);
        fixedCiphertext = keys.encapsulate().getEncapsulation();
        ciphertext = new byte[keys.encapsulationLength];
    }

    @Override
    public int slotBytes() {
        return keys.encapsulationLength;
    }

    @Override
    public void fillSlot(byte[] backing, int offset, int klass, RandomGenerator rng) {
        byte[] src = (klass == 0) ? fixedCiphertext : keys.encapsulate().getEncapsulation();
        System.arraycopy(src, 0, backing, offset, src.length);
    }

    @Override
    public Object compute(byte[] backing, int offset, int slotBytes) {
        // Copy the slot into a single fixed-address buffer, then decapsulate. The copy is a small,
        // constant, class-independent overhead relative to decapsulation (~tens of microseconds).
        System.arraycopy(backing, offset, ciphertext, 0, slotBytes);
        return keys.decapsulate(ciphertext);
    }
}
