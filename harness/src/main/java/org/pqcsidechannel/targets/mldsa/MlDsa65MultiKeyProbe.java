package org.pqcsidechannel.targets.mldsa;

import org.bouncycastle.pqc.crypto.mldsa.MLDSASigner;

import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

/**
 * Paired multi-key timing probe for the RQ-D4 key-dependence question.
 *
 * <p>Signs the <em>same</em> set of random messages with several independently generated keys,
 * interleaved so thermal/frequency drift is shared across keys, and reports each key's mean signing
 * time. Because every key signs the identical message set, the (large) message effect is controlled
 * and any difference in per-key means is attributable to the key. A spread across keys beyond the
 * standard error of the mean is evidence that ML-DSA signing time is key-dependent.
 *
 * <pre>java ... org.pqcsidechannel.targets.mldsa.MlDsa65MultiKeyProbe [numMessages] [numKeys]</pre>
 */
public final class MlDsa65MultiKeyProbe {

    private MlDsa65MultiKeyProbe() {
    }

    public static void main(String[] args) {
        int numMessages = args.length > 0 ? Integer.parseInt(args[0]) : 25_000;
        int numKeys = args.length > 1 ? Integer.parseInt(args[1]) : 6;

        MLDSASigner[] signers = new MLDSASigner[numKeys];
        for (int k = 0; k < numKeys; k++) {
            signers[k] = new MlDsa65Keys(100L + k).newDeterministicSigner();
        }

        RandomGenerator rng = RandomGeneratorFactory.of("L64X128MixRandom").create(999L);
        byte[][] messages = new byte[numMessages][32];
        for (byte[] m : messages) {
            rng.nextBytes(m);
        }

        long sink = 0;
        for (int w = 0; w < Math.min(3_000, numMessages); w++) { // warm-up, untimed
            for (MLDSASigner signer : signers) {
                signer.reset();
                signer.update(messages[w], 0, 32);
                sink += MlDsa65Keys.generate(signer)[0];
            }
        }

        long[] totalNs = new long[numKeys];
        for (int i = 0; i < numMessages; i++) {
            byte[] m = messages[i];
            for (int k = 0; k < numKeys; k++) { // interleaved so drift is shared across keys
                MLDSASigner signer = signers[k];
                signer.reset();
                signer.update(m, 0, 32);
                long t0 = System.nanoTime();
                byte[] sig = MlDsa65Keys.generate(signer);
                totalNs[k] += System.nanoTime() - t0;
                sink += sig[0];
            }
        }

        System.out.printf("Per-key mean ML-DSA-65 signing time over %d shared messages:%n", numMessages);
        double base = (double) totalNs[0] / numMessages;
        for (int k = 0; k < numKeys; k++) {
            double mean = (double) totalNs[k] / numMessages;
            System.out.printf("  key seed %d : mean %,.0f ns   (%+.2f%% vs key0)%n",
                    100 + k, mean, 100.0 * (mean - base) / base);
        }
        if (sink == 42) {
            System.out.println(sink); // keep the JIT from eliminating the signing work
        }
    }
}
