package org.pqcsidechannel.targets.mlkem;

import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.SecretWithEncapsulation;
import org.bouncycastle.pqc.crypto.mlkem.MLKEMExtractor;
import org.bouncycastle.pqc.crypto.mlkem.MLKEMGenerator;
import org.bouncycastle.pqc.crypto.mlkem.MLKEMKeyGenerationParameters;
import org.bouncycastle.pqc.crypto.mlkem.MLKEMKeyPairGenerator;
import org.bouncycastle.pqc.crypto.mlkem.MLKEMParameters;
import org.bouncycastle.pqc.crypto.mlkem.MLKEMPrivateKeyParameters;
import org.bouncycastle.pqc.crypto.mlkem.MLKEMPublicKeyParameters;
import org.pqcsidechannel.util.DeterministicSecureRandom;

import java.security.SecureRandom;

/**
 * A fixed ML-KEM-768 key pair plus the decapsulation extractor and an encapsulation source, all
 * derived deterministically from a seed so that the whole experiment is reproducible. Shared by the
 * ML-KEM measurement targets.
 *
 * <p>Decapsulation ({@link MLKEMExtractor#extractSecret(byte[])}) is the operation under test. Note
 * that ML-KEM decapsulation never throws for a correctly sized ciphertext: a valid ciphertext yields
 * the real shared secret, and an invalid one yields a pseudorandom value via the Fujisaki–Okamoto
 * implicit-rejection branch. Whether that branch is constant-time is exactly what the rejection
 * target probes.
 */
final class MlKemKeys {

    final MLKEMPublicKeyParameters publicKey;
    final MLKEMPrivateKeyParameters privateKey;
    final MLKEMExtractor extractor;
    final int encapsulationLength;

    private final SecureRandom encapRandom;

    MlKemKeys(long seed) {
        SecureRandom keyRandom = new DeterministicSecureRandom(seed);
        MLKEMKeyPairGenerator kpg = new MLKEMKeyPairGenerator();
        kpg.init(new MLKEMKeyGenerationParameters(keyRandom, MLKEMParameters.ml_kem_768));
        AsymmetricCipherKeyPair kp = kpg.generateKeyPair();

        this.publicKey = (MLKEMPublicKeyParameters) kp.getPublic();
        this.privateKey = (MLKEMPrivateKeyParameters) kp.getPrivate();
        this.extractor = new MLKEMExtractor(privateKey);
        this.encapsulationLength = extractor.getEncapsulationLength();
        // Distinct stream for encapsulations, so ciphertexts vary but remain reproducible.
        this.encapRandom = new DeterministicSecureRandom(seed ^ 0x5555_AAAAL);
    }

    /** Produce one valid encapsulation (shared secret + ciphertext) against the public key. */
    SecretWithEncapsulation encapsulate() {
        return new MLKEMGenerator(encapRandom).generateEncapsulated(publicKey);
    }

    /** Decapsulate a ciphertext, returning the shared secret (real or implicit-rejection value). */
    byte[] decapsulate(byte[] ciphertext) {
        return extractor.extractSecret(ciphertext);
    }
}
