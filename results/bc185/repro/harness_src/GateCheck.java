import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.pqc.crypto.mldsa.*;
import org.pqcsidechannel.util.DeterministicSecureRandom;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;

/**
 * Validation gates B1a / B1b, run identically against a stock bcprov 1.84 or 1.85 jar.
 *
 * <p>B1a: for each pinned seed, the ML-DSA-65 public key must be byte-identical across versions.
 * B1b: signing the same (key, message) twice must yield a byte-identical signature, which is what
 * proves deterministic (FIPS 204 rnd = 0) signing is actually engaged.
 *
 * <p>Output is a stable, greppable digest listing so the two versions' outputs can be diffed
 * directly rather than eyeballed.
 */
public final class GateCheck {

    /** The seeds that produced the original six-key probe, plus the two dudect key-dependence keys. */
    private static final long[] SEEDS = {
            100L, 101L, 102L, 103L, 104L, 105L, 0x0D5A65L, 0x0D5A66L
    };

    public static void main(String[] args) throws Exception {
        System.out.println("bcprov.version.marker=" + bcVersion());

        for (long seed : SEEDS) {
            SecureRandom rnd = new DeterministicSecureRandom(seed);

            // Record the raw 32-byte xi actually consumed for this seed, so the key input is pinned
            // as a concrete value and not only as an opaque seed.
            byte[] xi = new byte[32];
            new DeterministicSecureRandom(seed).nextBytes(xi);

            MLDSAKeyPairGenerator kpg = new MLDSAKeyPairGenerator();
            kpg.init(new MLDSAKeyGenerationParameters(rnd, MLDSAParameters.ml_dsa_65));
            AsymmetricCipherKeyPair kp = kpg.generateKeyPair();
            MLDSAPublicKeyParameters pk = (MLDSAPublicKeyParameters) kp.getPublic();
            MLDSAPrivateKeyParameters sk = (MLDSAPrivateKeyParameters) kp.getPrivate();

            byte[] pkEnc = pk.getEncoded();
            byte[] skEnc = sk.getEncoded();

            // B1b: deterministic signing -- sign the same message twice with independent signers.
            byte[] msg = new byte[32];
            for (int i = 0; i < 32; i++) {
                msg[i] = (byte) (i * 7 + 1);
            }
            byte[] sig1 = sign(sk, msg);
            byte[] sig2 = sign(sk, msg);
            boolean deterministic = java.util.Arrays.equals(sig1, sig2);

            // Signature must verify under the matching public key.
            MLDSASigner v = new MLDSASigner();
            v.init(false, pk);
            v.update(msg, 0, msg.length);
            boolean verified = v.verifySignature(sig1);

            System.out.printf(Locale.ROOT,
                    "seed=%d xi=%s pk_len=%d pk=%s sk_len=%d sk=%s sig_len=%d sig=%s det=%s verify=%s%n",
                    seed, hex(xi), pkEnc.length, sha256(pkEnc), skEnc.length, sha256(skEnc),
                    sig1.length, sha256(sig1), deterministic, verified);
        }
    }

    private static byte[] sign(MLDSAPrivateKeyParameters sk, byte[] msg) throws Exception {
        MLDSASigner s = new MLDSASigner();
        s.init(true, sk); // plain private key => deterministic signing (rnd = 0)
        s.update(msg, 0, msg.length);
        return s.generateSignature();
    }

    /** Best-effort marker so the output records which jar actually loaded. */
    private static String bcVersion() {
        Package p = MLDSASigner.class.getPackage();
        String impl = p == null ? null : p.getImplementationVersion();
        return impl == null ? "(unknown)" : impl;
    }

    private static String sha256(byte[] b) throws Exception {
        return hex(MessageDigest.getInstance("SHA-256").digest(b));
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) {
            sb.append(String.format(Locale.ROOT, "%02x", x));
        }
        return sb.toString();
    }

    private GateCheck() {
    }
}
