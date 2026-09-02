import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.pqc.crypto.mldsa.*;
import org.pqcsidechannel.util.DeterministicSecureRandom;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

/**
 * Collects the per-signature ML-DSA-65 work profile (Part C1/C2) using the instrumented build.
 *
 * <p>This output is <b>host-independent</b>: the rejection-loop iteration count, the reject stage of
 * each iteration, and the norm-check scan counts are deterministic functions of (key, message). They
 * carry no timing component, so background load, DVFS, and thermal state cannot perturb them. That
 * makes the algorithmic floor characterisable exactly, on any host.
 *
 * <pre>
 *   WorkProfile --keys=20 --messages=20000 --keyseed0=100 --msgseed=999 --out=profile.csv
 * </pre>
 */
public final class WorkProfile {

    public static void main(String[] args) throws Exception {
        int numKeys = 20;
        int numMessages = 20_000;
        long keySeed0 = 100L;
        long msgSeed = 999L;
        String out = null;
        boolean selfCheck = false;
        MLDSAParameters params = MLDSAParameters.ml_dsa_65;

        for (String a : args) {
            if (a.startsWith("--keys=")) numKeys = Integer.parseInt(a.substring(7));
            else if (a.startsWith("--messages=")) numMessages = Integer.parseInt(a.substring(11));
            else if (a.startsWith("--keyseed0=")) keySeed0 = Long.parseLong(a.substring(11));
            else if (a.startsWith("--msgseed=")) msgSeed = Long.parseLong(a.substring(10));
            else if (a.startsWith("--out=")) out = a.substring(6);
            else if (a.equals("--selfcheck")) selfCheck = true;
            else if (a.startsWith("--params=")) params = pick(a.substring(9));
            else throw new IllegalArgumentException("unknown arg: " + a);
        }

        byte[][] messages = corpus(numMessages, msgSeed);
        System.err.printf(Locale.ROOT, "corpus: %d x 32B  msgSeed=%d  sha256=%s%n",
                numMessages, msgSeed, corpusDigest(messages));

        System.err.println("param set : " + params.getName());
        MLDSASigner[] signers = new MLDSASigner[numKeys];
        for (int k = 0; k < numKeys; k++) {
            signers[k] = signerFor(keySeed0 + k, params);
        }

        if (selfCheck) {
            selfCheck(signers, messages, keySeed0);
            return;
        }

        try (BufferedWriter w = Files.newBufferedWriter(Path.of(out))) {
            w.write("key_id,key_seed,message_id,iterations,stage_seq,profile_key,"
                    + "rej_z,rej_r0,rej_ct0,rej_hint,norm_scan_coeffs,norm_scan_calls,challenge_bytes\n");
            for (int m = 0; m < messages.length; m++) {
                for (int k = 0; k < numKeys; k++) {
                    MLDSATrace.reset();
                    MLDSASigner s = signers[k];
                    s.reset();
                    s.update(messages[m], 0, 32);
                    byte[] sig = s.generateSignature();
                    if (sig == null) throw new IllegalStateException("signing failed");

                    int[] c = MLDSATrace.stageCounts();
                    // profile_key contains commas; make it CSV-safe (':' + '-' separated) so it
                    // stays a single field. stage_seq uses '|' and is already safe.
                    String profileKey = MLDSATrace.profileKey().replace(',', '-');
                    w.write(String.format(Locale.ROOT, "%d,%d,%d,%d,%s,%s,%d,%d,%d,%d,%d,%d,%d%n",
                            k, keySeed0 + k, m, MLDSATrace.iterations(), MLDSATrace.stageSeq(),
                            profileKey, c[1], c[2], c[3], c[4],
                            MLDSATrace.normScanCoeffs, MLDSATrace.normScanCalls, MLDSATrace.challengeBytes));
                }
                if ((m + 1) % 1000 == 0) System.err.printf("  %d/%d messages%n", m + 1, messages.length);
            }
        }
        System.err.println("wrote " + out);
    }

    /** Gate C1b: the trace must be a deterministic function of (key, message). */
    private static void selfCheck(MLDSASigner[] signers, byte[][] messages, long keySeed0) throws Exception {
        int checked = 0, mismatched = 0;
        for (int m = 0; m < Math.min(200, messages.length); m++) {
            for (int k = 0; k < signers.length; k++) {
                String[] r = new String[2];
                String[] sg = new String[2];
                for (int rep = 0; rep < 2; rep++) {
                    MLDSATrace.reset();
                    signers[k].reset();
                    signers[k].update(messages[m], 0, 32);
                    byte[] sig = signers[k].generateSignature();
                    r[rep] = MLDSATrace.profileKey() + "/" + MLDSATrace.stageSeq()
                            + "/" + MLDSATrace.normScanCoeffs + "/" + MLDSATrace.challengeBytes;
                    sg[rep] = sha256(sig);
                }
                checked++;
                if (!r[0].equals(r[1]) || !sg[0].equals(sg[1])) {
                    mismatched++;
                    System.out.printf("MISMATCH key=%d msg=%d: %s vs %s%n", k, m, r[0], r[1]);
                }
            }
        }
        System.out.printf(Locale.ROOT,
                "GATE C1b: %d (key,message) pairs re-signed; %d trace/signature mismatches -> %s%n",
                checked, mismatched, mismatched == 0 ? "PASS (trace is deterministic)" : "FAIL");
    }

    private static MLDSAParameters pick(String v) {
        switch (v) {
            case "44": return MLDSAParameters.ml_dsa_44;
            case "65": return MLDSAParameters.ml_dsa_65;
            case "87": return MLDSAParameters.ml_dsa_87;
            default: throw new IllegalArgumentException("--params must be 44|65|87, got " + v);
        }
    }

    private static MLDSASigner signerFor(long seed, MLDSAParameters params) {
        MLDSAKeyPairGenerator kpg = new MLDSAKeyPairGenerator();
        kpg.init(new MLDSAKeyGenerationParameters(new DeterministicSecureRandom(seed), params));
        AsymmetricCipherKeyPair kp = kpg.generateKeyPair();
        MLDSASigner s = new MLDSASigner();
        s.init(true, (MLDSAPrivateKeyParameters) kp.getPrivate()); // deterministic: rnd = 0
        return s;
    }

    /** Same generator and seed as the original six-key probe, so the corpus is comparable. */
    private static byte[][] corpus(int n, long seed) {
        RandomGenerator rng = RandomGeneratorFactory.of("L64X128MixRandom").create(seed);
        byte[][] msgs = new byte[n][32];
        for (byte[] m : msgs) rng.nextBytes(m);
        return msgs;
    }

    private static String corpusDigest(byte[][] msgs) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        for (byte[] m : msgs) md.update(m);
        return hex(md.digest());
    }

    private static String sha256(byte[] b) throws Exception {
        return hex(MessageDigest.getInstance("SHA-256").digest(b));
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format(Locale.ROOT, "%02x", x));
        return sb.toString();
    }

    private WorkProfile() {
    }
}
