package org.pqcsidechannel.targets.mldsa;

import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.CryptoException;
import org.bouncycastle.pqc.crypto.mldsa.MLDSAKeyGenerationParameters;
import org.bouncycastle.pqc.crypto.mldsa.MLDSAKeyPairGenerator;
import org.bouncycastle.pqc.crypto.mldsa.MLDSAParameters;
import org.bouncycastle.pqc.crypto.mldsa.MLDSAPrivateKeyParameters;
import org.bouncycastle.pqc.crypto.mldsa.MLDSAPublicKeyParameters;
import org.bouncycastle.pqc.crypto.mldsa.MLDSASigner;
import org.pqcsidechannel.util.DeterministicSecureRandom;

import java.security.SecureRandom;

/**
 * A fixed ML-DSA-65 key pair derived deterministically from a seed, plus signing helpers. Shared by
 * the ML-DSA measurement targets.
 *
 * <p>Signing uses {@link MLDSASigner#init(boolean, org.bouncycastle.crypto.CipherParameters)} with the
 * plain private key, which selects <b>deterministic</b> signing (FIPS 204 rnd = 0). With randomness
 * fixed, signing time is a deterministic function of (key, message), so any timing variation across
 * messages reflects the rejection-sampling iteration count — the quantity of interest.
 */
final class MlDsa65Keys {

    final MLDSAPublicKeyParameters publicKey;
    final MLDSAPrivateKeyParameters privateKey;

    MlDsa65Keys(long seed) {
        SecureRandom keyRandom = new DeterministicSecureRandom(seed);
        MLDSAKeyPairGenerator kpg = new MLDSAKeyPairGenerator();
        kpg.init(new MLDSAKeyGenerationParameters(keyRandom, MLDSAParameters.ml_dsa_65));
        AsymmetricCipherKeyPair kp = kpg.generateKeyPair();
        this.publicKey = (MLDSAPublicKeyParameters) kp.getPublic();
        this.privateKey = (MLDSAPrivateKeyParameters) kp.getPrivate();
    }

    /** A signer initialized once for deterministic signing with this key (key expanded once). */
    MLDSASigner newDeterministicSigner() {
        MLDSASigner signer = new MLDSASigner();
        signer.init(true, privateKey);
        return signer;
    }

    /** Deterministically sign a message with a fresh signer (used by correctness tests). */
    byte[] signDeterministic(byte[] message) {
        MLDSASigner signer = newDeterministicSigner();
        signer.update(message, 0, message.length);
        return generate(signer);
    }

    boolean verify(byte[] message, byte[] signature) {
        MLDSASigner verifier = new MLDSASigner();
        verifier.init(false, publicKey);
        verifier.update(message, 0, message.length);
        return verifier.verifySignature(signature);
    }

    /** Wrap the checked CryptoException so callers on the MeasurementTarget path stay clean. */
    static byte[] generate(MLDSASigner signer) {
        try {
            return signer.generateSignature();
        } catch (CryptoException e) {
            throw new IllegalStateException("ML-DSA signing failed", e);
        }
    }
}
