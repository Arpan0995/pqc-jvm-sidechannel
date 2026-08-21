package org.bouncycastle.pqc.crypto.mldsa;

/**
 * INSTRUMENTATION ONLY — not part of stock BouncyCastle, never published.
 *
 * <p>Records the per-signature <em>work profile</em> of ML-DSA signing: how many rejection-loop
 * iterations ran, which check ended each one, and how much work the (early-exiting) norm checks
 * actually did. This is the ground truth against which measured signing time is decomposed into
 * the algorithmic rejection-sampling floor versus any implementation-level residual.
 *
 * <p>Everything here is write-only bookkeeping: no value recorded is ever read back by the signing
 * algorithm, so instrumented and stock builds must produce byte-identical signatures (gate C1a).
 * The counters are plain statics because the collector is single-threaded by construction.
 */
public final class MLDSATrace {

    /** The accepting iteration. */
    public static final int ACCEPT = 0;
    /** Rejected at the ‖z‖∞ ≥ γ1 − β check. */
    public static final int REJ_Z = 1;
    /** Rejected at the ‖r0‖∞ ≥ γ2 − β check. */
    public static final int REJ_R0 = 2;
    /** Rejected at the ‖c·t0‖∞ ≥ γ2 check. */
    public static final int REJ_CT0 = 3;
    /** Rejected at the hint-weight (n > ω) check. */
    public static final int REJ_HINT = 4;

    public static final String[] STAGE_NAME = {"accept", "rej_z", "rej_r0", "rej_ct0", "rej_hint"};

    /** One stage code per rejection-loop iteration, in order; the last is normally ACCEPT. */
    public static int[] stages = new int[4096];
    public static int len = 0;

    /**
     * Total polynomial coefficients actually examined across every {@code Poly.checkNorm} call in
     * this signature. BC's norm check returns as soon as it finds an out-of-bound coefficient, so
     * this is strictly implementation work: it varies with the <em>position</em> of the first
     * violating coefficient, which the algorithm does not specify.
     */
    public static long normScanCoeffs = 0;
    /** Number of {@code Poly.checkNorm} invocations (per-polynomial, not per-vector). */
    public static long normScanCalls = 0;
    /** SHAKE bytes consumed by SampleInBall's {@code do..while (b > i)} rejection loop. */
    public static long challengeBytes = 0;

    public static void reset() {
        len = 0;
        normScanCoeffs = 0;
        normScanCalls = 0;
        challengeBytes = 0;
    }

    public static void stage(int s) {
        if (len < stages.length) {
            stages[len++] = s;
        }
    }

    /** Total rejection-loop iterations (each iteration contributes exactly one stage code). */
    public static int iterations() {
        return len;
    }

    /** Counts indexed by stage code — the reject-stage multiset that defines a work profile. */
    public static int[] stageCounts() {
        int[] c = new int[5];
        for (int i = 0; i < len; i++) {
            c[stages[i]]++;
        }
        return c;
    }

    /** The ordered stage sequence, e.g. {@code 1|1|3|0}. */
    public static String stageSeq() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            if (i > 0) {
                sb.append('|');
            }
            sb.append(stages[i]);
        }
        return sb.toString();
    }

    /**
     * The canonical work-profile key: total iterations plus the reject-stage multiset. Two
     * signatures sharing this key performed the same sequence of algorithmic steps.
     */
    public static String profileKey() {
        int[] c = stageCounts();
        return iterations() + ":" + c[1] + "," + c[2] + "," + c[3] + "," + c[4] + "," + c[0];
    }

    private MLDSATrace() {
    }
}
