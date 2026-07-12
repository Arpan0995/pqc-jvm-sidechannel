package org.pqcsidechannel.targets.slhdsa;

import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.pqc.crypto.slhdsa.SLHDSAKeyGenerationParameters;
import org.bouncycastle.pqc.crypto.slhdsa.SLHDSAKeyPairGenerator;
import org.bouncycastle.pqc.crypto.slhdsa.SLHDSAParameters;
import org.bouncycastle.pqc.crypto.slhdsa.SLHDSAPrivateKeyParameters;
import org.bouncycastle.pqc.crypto.slhdsa.SLHDSAPublicKeyParameters;
import org.bouncycastle.pqc.crypto.slhdsa.SLHDSASigner;
import org.pqcsidechannel.util.DeterministicSecureRandom;

import java.security.SecureRandom;

/**
 * A fixed SLH-DSA key pair derived deterministically from a seed, plus signing helpers. Shared by the
 * SLH-DSA measurement targets.
 *
 * <p>SLH-DSA is a stateless hash-based signature: signing performs a fixed number of hash evaluations
 * with no rejection sampling and no secret-dependent branch, so signing time is expected to be
 * independent of both the message and the key. Determinism of the signer does not affect timing here
 * (the work is fixed regardless of the randomizer), but the plain-private-key init used below is
 * deterministic, which the tests verify for reproducibility.
 */
final class SlhDsaKeys {

    final SLHDSAPublicKeyParameters publicKey;
    final SLHDSAPrivateKeyParameters privateKey;

    SlhDsaKeys(long seed, SLHDSAParameters params) {
        SecureRandom keyRandom = new DeterministicSecureRandom(seed);
        SLHDSAKeyPairGenerator kpg = new SLHDSAKeyPairGenerator();
        kpg.init(new SLHDSAKeyGenerationParameters(keyRandom, params));
        AsymmetricCipherKeyPair kp = kpg.generateKeyPair();
        this.publicKey = (SLHDSAPublicKeyParameters) kp.getPublic();
        this.privateKey = (SLHDSAPrivateKeyParameters) kp.getPrivate();
    }

    /** A signer initialized once for signing with this key; reusable across messages. */
    SLHDSASigner newSigner() {
        SLHDSASigner signer = new SLHDSASigner();
        signer.init(true, privateKey);
        return signer;
    }

    byte[] sign(byte[] message) {
        return newSigner().generateSignature(message);
    }

    boolean verify(byte[] message, byte[] signature) {
        SLHDSASigner verifier = new SLHDSASigner();
        verifier.init(false, publicKey);
        return verifier.verifySignature(message, signature);
    }
}
