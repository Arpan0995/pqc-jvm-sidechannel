package org.pqcsidechannel.targets.slhdsa;

import org.bouncycastle.pqc.crypto.slhdsa.SLHDSAParameters;
import org.junit.jupiter.api.Test;
import org.pqcsidechannel.measure.Measurement;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Correctness checks for the SLH-DSA plumbing: signing verifies, is deterministic (plain private key),
 * keys are reproducible, and the target runs through the measurement harness. Timing is not involved.
 */
class SlhDsaTargetTest {

    private static byte[] msg(int b) {
        byte[] m = new byte[32];
        Arrays.fill(m, (byte) b);
        return m;
    }

    @Test
    void signVerifyRoundTrip() {
        SlhDsaKeys keys = new SlhDsaKeys(1L, SLHDSAParameters.sha2_128f);
        byte[] m = msg(1);
        byte[] sig = keys.sign(m);
        assertTrue(keys.verify(m, sig), "signature must verify");
        assertFalse(keys.verify(msg(2), sig), "signature must not verify for a different message");
    }

    @Test
    void signingIsDeterministic() {
        SlhDsaKeys keys = new SlhDsaKeys(1L, SLHDSAParameters.sha2_128f);
        byte[] m = msg(1);
        assertArrayEquals(keys.sign(m), keys.sign(m),
                "plain-private-key SLH-DSA signing should be deterministic");
    }

    @Test
    void keysAreReproducibleFromSeed() {
        SlhDsaKeys a = new SlhDsaKeys(42L, SLHDSAParameters.sha2_128f);
        SlhDsaKeys b = new SlhDsaKeys(42L, SLHDSAParameters.sha2_128f);
        assertArrayEquals(a.publicKey.getEncoded(), b.publicKey.getEncoded(),
                "same seed must produce the same key pair");
    }

    @Test
    void messageTargetRunsThroughTheMeasurementHarness() {
        // Small run — SLH-DSA signing is slow (~ms), so keep N tiny for the smoke test.
        Measurement.Samples s = new Measurement(
                new SlhDsaSha2128fSignMessageTarget(), Measurement.Config.of(200)).run();
        assertTrue(s.class0().length > 0 && s.class1().length > 0,
                "both classes should receive measurements");
    }
}
