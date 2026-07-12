package org.pqcsidechannel.targets.mldsa;

import org.bouncycastle.pqc.crypto.mldsa.MLDSASigner;
import org.junit.jupiter.api.Test;
import org.pqcsidechannel.measure.Measurement;

import java.util.Arrays;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Correctness checks for the ML-DSA plumbing: that signing is deterministic (so randomness is truly
 * fixed), that a signer can be reused across messages via reset (the measurement path depends on it),
 * and that signatures verify. Timing is not involved here.
 */
class MlDsaTargetTest {

    private static RandomGenerator rng() {
        return RandomGeneratorFactory.of("L64X128MixRandom").create(3L);
    }

    private static byte[] msg(int b) {
        byte[] m = new byte[32];
        Arrays.fill(m, (byte) b);
        return m;
    }

    @Test
    void signingIsDeterministic() {
        MlDsa65Keys keys = new MlDsa65Keys(5L);
        byte[] m = msg(1);
        byte[] s1 = keys.signDeterministic(m);
        byte[] s2 = keys.signDeterministic(m);
        assertArrayEquals(s1, s2, "deterministic signing must produce identical signatures");
        assertTrue(keys.verify(m, s1), "signature must verify");
    }

    @Test
    void differentMessagesGiveDifferentSignatures() {
        MlDsa65Keys keys = new MlDsa65Keys(5L);
        byte[] sA = keys.signDeterministic(msg(1));
        byte[] sB = keys.signDeterministic(msg(2));
        assertFalse(Arrays.equals(sA, sB), "different messages must give different signatures");
    }

    @Test
    void signerCanBeReusedViaResetAcrossMessages() {
        // The measurement path signs many messages with one signer using reset()+update()+generate().
        MlDsa65Keys keys = new MlDsa65Keys(5L);
        MLDSASigner signer = keys.newDeterministicSigner();

        byte[] mA = msg(1);
        signer.reset();
        signer.update(mA, 0, mA.length);
        byte[] sA = MlDsa65Keys.generate(signer);

        byte[] mB = msg(2);
        signer.reset();
        signer.update(mB, 0, mB.length);
        byte[] sB = MlDsa65Keys.generate(signer);

        assertTrue(keys.verify(mA, sA), "first reused-signer signature must verify");
        assertTrue(keys.verify(mB, sB), "second reused-signer signature must verify");
        assertFalse(Arrays.equals(sA, sB), "the two messages must give different signatures");
    }

    @Test
    void keysAreReproducibleFromSeed() {
        MlDsa65Keys a = new MlDsa65Keys(42L);
        MlDsa65Keys b = new MlDsa65Keys(42L);
        assertArrayEquals(a.publicKey.getEncoded(), b.publicKey.getEncoded(),
                "same seed must produce the same key pair");
    }

    @Test
    void messageTargetRunsThroughTheMeasurementHarness() {
        Measurement.Samples s = new Measurement(
                new MlDsa65SignMessageTarget(), Measurement.Config.of(2_000)).run();
        assertTrue(s.class0().length > 0 && s.class1().length > 0,
                "both classes should receive measurements");
    }

    @Test
    void keyDependenceUsesTwoDistinctKeys() {
        // The RQ-D4 target contrasts two independently generated keys; they must actually differ.
        MlDsa65Keys a = new MlDsa65Keys(0x0D5A65L);
        MlDsa65Keys b = new MlDsa65Keys(0x0D5A66L);
        assertFalse(Arrays.equals(a.publicKey.getEncoded(), b.publicKey.getEncoded()),
                "the two key-dependence keys must be different");
    }

    @Test
    void keyDependenceTargetRunsThroughTheMeasurementHarness() {
        Measurement.Samples s = new Measurement(
                new MlDsa65SignKeyDependenceTarget(), Measurement.Config.of(2_000)).run();
        assertTrue(s.class0().length > 0 && s.class1().length > 0,
                "both classes should receive measurements");
    }
}
