import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.pqc.crypto.mldsa.*;
import org.pqcsidechannel.util.DeterministicSecureRandom;

import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import org.bouncycastle.crypto.params.ParametersWithRandom;
import java.util.List;
import java.util.Locale;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

/**
 * Per-invocation ML-DSA-65 signing timing on the STOCK 1.85 jar (Part C2, RUN-C-TIME).
 *
 * <p>Produces one row per signature — {@code key_id, message_id, time_ns} — on exactly the same
 * (key, message) grid as {@link WorkProfile}, so the two datasets join on {@code (key_id, message_id)}.
 * This is the timing half of the discrimination; the work-profile half comes from the instrumented
 * build. Timing is never taken from the instrumented build.
 *
 * <p>Design (mirrors the study's confound hardening for the multi-key setting):
 * <ul>
 *   <li><b>Paired / interleaved.</b> For each message, all keys sign it before moving on, so slow
 *       drift (thermal, DVFS, scheduler) is shared across keys rather than aliasing onto a key.</li>
 *   <li><b>Randomised key order per message.</b> The per-message signing order of the keys is shuffled
 *       (recorded via key_id), decorrelating key identity from loop position.</li>
 *   <li><b>Signers allocated once</b> (keys expanded once), matching the 1.84 probe and the real-world
 *       cost model; each key's working memory is warm and at a fixed address for every message.</li>
 *   <li><b>Warm-up</b> before timing so the JIT is at steady tier.</li>
 *   <li><b>GC accounting.</b> GC collection count/time recorded at start and end so GC contamination is
 *       visible, not hidden.</li>
 * </ul>
 */
public final class MicroTiming {

    public static void main(String[] args) throws Exception {
        int numKeys = 20;
        int numMessages = 60_000;
        long keySeed0 = 100L;
        long msgSeed = 999L;
        long shuffleSeed = 0xA11CE5EEDL;
        int warmupMessages = 3_000;
        String out = null;
        String runId = "RUN-C-TIME";
        long allocSeed = 0;   // 0 => allocate signers in natural key order; !=0 => shuffle alloc order
        MLDSAParameters params = MLDSAParameters.ml_dsa_65;
        boolean hedged = false;

        for (String a : args) {
            if (a.startsWith("--keys=")) numKeys = Integer.parseInt(a.substring(7));
            else if (a.startsWith("--messages=")) numMessages = Integer.parseInt(a.substring(11));
            else if (a.startsWith("--keyseed0=")) keySeed0 = Long.parseLong(a.substring(11));
            else if (a.startsWith("--msgseed=")) msgSeed = Long.parseLong(a.substring(10));
            else if (a.startsWith("--warmup=")) warmupMessages = Integer.parseInt(a.substring(9));
            else if (a.startsWith("--out=")) out = a.substring(6);
            else if (a.startsWith("--runid=")) runId = a.substring(8);
            else if (a.startsWith("--allocseed=")) allocSeed = Long.parseLong(a.substring(12));
            else if (a.startsWith("--params=")) params = pick(a.substring(9));
            else if (a.equals("--hedged")) hedged = true;
            else throw new IllegalArgumentException("unknown arg: " + a);
        }

        // Prove which jar we timed against (must be stock, must be 1.85).
        System.err.println("MLDSASigner from : "
                + MLDSASigner.class.getProtectionDomain().getCodeSource().getLocation());
        boolean instrumented = false;
        try {
            Class.forName("org.bouncycastle.pqc.crypto.mldsa.MLDSATrace");
            instrumented = true;
        } catch (ClassNotFoundException ok) {
            // expected: stock jar has no MLDSATrace
        }
        System.err.println("instrumented classes on path : " + instrumented
                + (instrumented ? "  <-- ABORT: timing must use the stock jar only" : "  (stock)"));
        if (instrumented) {
            throw new IllegalStateException("MLDSATrace present; refusing to record timing from an instrumented build");
        }

        byte[][] messages = corpus(numMessages, msgSeed);
        System.err.printf(Locale.ROOT, "corpus: %d x 32B  msgSeed=%d  sha256=%s%n",
                numMessages, msgSeed, corpusDigest(messages));

        // Allocation-order control: create the signer objects in a permuted sequence when allocSeed
        // != 0, so key_id is decorrelated from heap position / creation order. Signers are still
        // indexed by true key_id; only the order in which they are constructed changes. This is the
        // placement analog of the study's A/B swap — a per-key timing rank that survives an alloc-order
        // change and a fresh JVM follows the key's value, not its address.
        System.err.println("param set : " + params.getName() + "   signing mode : " + (hedged ? "hedged (fresh randomness per signature)" : "deterministic (rnd=0)"));
        MLDSASigner[] signers = new MLDSASigner[numKeys];
        int[] allocOrder = new int[numKeys];
        for (int k = 0; k < numKeys; k++) allocOrder[k] = k;
        if (allocSeed != 0) {
            RandomGenerator ar = RandomGeneratorFactory.of("L64X128MixRandom").create(allocSeed);
            shuffle(allocOrder, ar);
        }
        System.err.println("alloc order: " + (allocSeed == 0 ? "natural" : java.util.Arrays.toString(allocOrder)));
        for (int pos = 0; pos < numKeys; pos++) {
            int k = allocOrder[pos];
            signers[k] = signerFor(keySeed0 + k, params, hedged);
        }

        // Warm-up (untimed).
        long sink = 0;
        for (int w = 0; w < Math.min(warmupMessages, numMessages); w++) {
            for (int k = 0; k < numKeys; k++) {
                signers[k].reset();
                signers[k].update(messages[w], 0, 32);
                sink += sign(signers[k])[0];
            }
        }

        RandomGenerator ord = RandomGeneratorFactory.of("L64X128MixRandom").create(shuffleSeed);
        int[] order = new int[numKeys];
        for (int k = 0; k < numKeys; k++) order[k] = k;

        List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        long gcCount0 = gcCount(gcBeans), gcTime0 = gcTime(gcBeans);

        try (BufferedWriter w = Files.newBufferedWriter(Path.of(out))) {
            w.write("key_id,message_id,time_ns,run_id\n");
            for (int m = 0; m < numMessages; m++) {
                shuffle(order, ord);
                byte[] msg = messages[m];
                for (int idx = 0; idx < numKeys; idx++) {
                    int k = order[idx];
                    MLDSASigner s = signers[k];
                    s.reset();
                    s.update(msg, 0, 32);
                    long t0 = System.nanoTime();
                    byte[] sig = sign(s);
                    long dt = System.nanoTime() - t0;
                    sink += sig[0];
                    w.write(k + "," + m + "," + dt + "," + runId + "\n");
                }
                if ((m + 1) % 5000 == 0) System.err.printf("  %d/%d messages%n", m + 1, numMessages);
            }
        }

        long gcCount1 = gcCount(gcBeans), gcTime1 = gcTime(gcBeans);
        System.err.printf(Locale.ROOT, "GC during timing: %d collections, %d ms%n",
                gcCount1 - gcCount0, gcTime1 - gcTime0);
        System.err.println("wrote " + out);
        if (sink == 42) System.err.println(sink); // DCE guard
    }

    private static byte[] sign(MLDSASigner s) {
        try {
            return s.generateSignature();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static final SecureRandom HEDGE_RNG = new SecureRandom();

    private static MLDSAParameters pick(String v) {
        switch (v) {
            case "44": return MLDSAParameters.ml_dsa_44;
            case "65": return MLDSAParameters.ml_dsa_65;
            case "87": return MLDSAParameters.ml_dsa_87;
            default: throw new IllegalArgumentException("--params must be 44|65|87, got " + v);
        }
    }

    private static MLDSASigner signerFor(long seed, MLDSAParameters params, boolean hedged) {
        MLDSAKeyPairGenerator kpg = new MLDSAKeyPairGenerator();
        kpg.init(new MLDSAKeyGenerationParameters(new DeterministicSecureRandom(seed), params));
        AsymmetricCipherKeyPair kp = kpg.generateKeyPair();
        MLDSASigner s = new MLDSASigner();
        if (hedged) s.init(true, new ParametersWithRandom((MLDSAPrivateKeyParameters) kp.getPrivate(), HEDGE_RNG));
        else s.init(true, (MLDSAPrivateKeyParameters) kp.getPrivate());
        return s;
    }

    private static byte[][] corpus(int n, long seed) {
        RandomGenerator rng = RandomGeneratorFactory.of("L64X128MixRandom").create(seed);
        byte[][] msgs = new byte[n][32];
        for (byte[] m : msgs) rng.nextBytes(m);
        return msgs;
    }

    private static void shuffle(int[] a, RandomGenerator rng) {
        for (int i = a.length - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int t = a[i];
            a[i] = a[j];
            a[j] = t;
        }
    }

    private static long gcCount(List<GarbageCollectorMXBean> b) {
        long s = 0;
        for (GarbageCollectorMXBean g : b) s += Math.max(0, g.getCollectionCount());
        return s;
    }

    private static long gcTime(List<GarbageCollectorMXBean> b) {
        long s = 0;
        for (GarbageCollectorMXBean g : b) s += Math.max(0, g.getCollectionTime());
        return s;
    }

    private static String corpusDigest(byte[][] msgs) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        for (byte[] m : msgs) md.update(m);
        return hex(md.digest());
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format(Locale.ROOT, "%02x", x));
        return sb.toString();
    }

    private MicroTiming() {
    }
}
