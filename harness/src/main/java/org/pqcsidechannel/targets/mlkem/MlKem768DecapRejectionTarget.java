package org.pqcsidechannel.targets.mlkem;

import org.pqcsidechannel.measure.MeasurementTarget;

import java.util.random.RandomGenerator;

/**
 * The known-sensitive ML-KEM-768 target: <b>valid vs. rejected</b> ciphertexts.
 *
 * <p>Class 0 is a fresh valid ciphertext (Fujisaki–Okamoto re-encryption matches → real shared
 * secret). Class 1 is the same freshly generated ciphertext with one byte flipped, so decapsulation
 * runs to completion but the FO re-encryption check fails and the implicit-rejection branch returns a
 * pseudorandom value. Both classes vary per slot, so the only systematic difference is the branch
 * outcome — isolating the implicit-rejection path.
 *
 * <p>FIPS 203 requires the selection between the real and rejection secrets to be constant-time. If it
 * is not (a data-dependent branch or a non-constant-time comparison, the KyberSlash / FO-oracle family
 * of concerns), decapsulation time differs between a valid and a rejected ciphertext, and this target
 * detects it.
 */
public final class MlKem768DecapRejectionTarget implements MeasurementTarget {

    private final long seed;
    private MlKemKeys keys;
    private byte[] ciphertext; // reusable decap input, fixed address for both classes

    public MlKem768DecapRejectionTarget() {
        this(0xC0DECAFEL);
    }

    public MlKem768DecapRejectionTarget(long seed) {
        this.seed = seed;
    }

    @Override
    public String name() {
        return "ml-kem-768:decap-valid-vs-rejected";
    }

    @Override
    public void setup(RandomGenerator rng) {
        keys = new MlKemKeys(seed);
        ciphertext = new byte[keys.encapsulationLength];
    }

    @Override
    public int slotBytes() {
        return keys.encapsulationLength;
    }

    @Override
    public void fillSlot(byte[] backing, int offset, int klass, RandomGenerator rng) {
        byte[] ct = keys.encapsulate().getEncapsulation();
        if (klass == 1) {
            // Flip one byte so re-encryption mismatches -> FO implicit-rejection branch.
            ct[ct.length - 1] ^= 0xFF;
        }
        System.arraycopy(ct, 0, backing, offset, ct.length);
    }

    @Override
    public Object compute(byte[] backing, int offset, int slotBytes) {
        System.arraycopy(backing, offset, ciphertext, 0, slotBytes);
        return keys.decapsulate(ciphertext);
    }
}
