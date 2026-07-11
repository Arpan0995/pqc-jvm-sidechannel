package org.pqcsidechannel.targets.mlkem;

import org.bouncycastle.crypto.SecretWithEncapsulation;
import org.junit.jupiter.api.Test;
import org.pqcsidechannel.measure.Measurement;

import java.util.Arrays;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Correctness checks for the ML-KEM plumbing — that the BouncyCastle API is driven correctly and the
 * two input classes mean what the leakage targets assume. Timing is not involved here.
 */
class MlKemTargetTest {

    private static RandomGenerator rng() {
        return RandomGeneratorFactory.of("L64X128MixRandom").create(7L);
    }

    @Test
    void validCiphertextDecapsulatesToTheRealSharedSecret() {
        MlKemKeys keys = new MlKemKeys(1L);
        SecretWithEncapsulation enc = keys.encapsulate();
        byte[] recovered = keys.decapsulate(enc.getEncapsulation());
        assertArrayEquals(enc.getSecret(), recovered,
                "decapsulating a valid ciphertext must recover the encapsulated shared secret");
        assertEquals(32, recovered.length, "ML-KEM-768 shared secret is 32 bytes");
    }

    @Test
    void corruptedCiphertextTriggersImplicitRejection() {
        MlKemKeys keys = new MlKemKeys(1L);
        SecretWithEncapsulation enc = keys.encapsulate();
        byte[] corrupted = enc.getEncapsulation().clone();
        corrupted[corrupted.length - 1] ^= 0xFF;

        byte[] rejected = keys.decapsulate(corrupted); // must NOT throw
        assertEquals(32, rejected.length, "implicit rejection still returns a 32-byte value");
        assertFalse(Arrays.equals(enc.getSecret(), rejected),
                "a rejected ciphertext must not yield the real shared secret");
    }

    @Test
    void keysAreReproducibleFromSeed() {
        MlKemKeys a = new MlKemKeys(42L);
        MlKemKeys b = new MlKemKeys(42L);
        assertArrayEquals(a.publicKey.getEncoded(), b.publicKey.getEncoded(),
                "same seed must produce the same key pair");
    }

    @Test
    void validTargetComputesSharedSecretsForBothClasses() {
        MlKem768DecapValidTarget target = new MlKem768DecapValidTarget();
        target.setup(rng());
        int sb = target.slotBytes();
        assertEquals(1088, sb, "ML-KEM-768 ciphertext length");

        byte[] backing = new byte[2 * sb];
        target.fillSlot(backing, 0, 0, rng());
        target.fillSlot(backing, sb, 1, rng());

        byte[] c0 = (byte[]) target.compute(backing, 0, sb);
        byte[] c1 = (byte[]) target.compute(backing, sb, sb);
        assertEquals(32, c0.length);
        assertEquals(32, c1.length);
    }

    @Test
    void rejectionTargetRunsThroughTheMeasurementHarness() {
        // Small end-to-end smoke run: both classes must be populated with timings.
        Measurement.Samples s = new Measurement(
                new MlKem768DecapRejectionTarget(), Measurement.Config.of(4_000)).run();
        assertTrue(s.class0().length > 0 && s.class1().length > 0,
                "both classes should receive measurements");
    }
}
