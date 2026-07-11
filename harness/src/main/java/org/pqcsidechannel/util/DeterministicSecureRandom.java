package org.pqcsidechannel.util;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

/**
 * A {@link SecureRandom} whose output is fully determined by a seed, so ML-KEM key pairs and
 * ciphertexts are reproducible across runs. This is for experiment reproducibility only — it is not
 * cryptographically secure and must never be used to protect real data.
 *
 * <p>BouncyCastle's lightweight ML-KEM generators draw all their randomness through
 * {@link SecureRandom#nextBytes(byte[])}, which is overridden here to stream from a seeded
 * {@link RandomGenerator}. The seed used is recorded with every result.
 */
public final class DeterministicSecureRandom extends SecureRandom {

    private final RandomGenerator rng;

    public DeterministicSecureRandom(long seed) {
        super();
        this.rng = RandomGeneratorFactory.of("L64X128MixRandom").create(seed);
    }

    @Override
    public void nextBytes(byte[] bytes) {
        int i = 0;
        while (i < bytes.length) {
            long r = rng.nextLong();
            for (int b = 0; b < 8 && i < bytes.length; b++, i++) {
                bytes[i] = (byte) (r & 0xFF);
                r >>>= 8;
            }
        }
    }

    @Override
    public long nextLong() {
        return rng.nextLong();
    }

    @Override
    public int nextInt() {
        return rng.nextInt();
    }

    @Override
    public byte[] generateSeed(int numBytes) {
        byte[] out = new byte[numBytes];
        nextBytes(out);
        return out;
    }
}
