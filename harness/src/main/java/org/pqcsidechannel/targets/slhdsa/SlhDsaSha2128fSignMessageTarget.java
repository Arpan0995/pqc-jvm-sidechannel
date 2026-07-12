package org.pqcsidechannel.targets.slhdsa;

import org.bouncycastle.pqc.crypto.slhdsa.SLHDSAParameters;
import org.bouncycastle.pqc.crypto.slhdsa.SLHDSASigner;
import org.pqcsidechannel.measure.MeasurementTarget;

import java.util.random.RandomGenerator;

/**
 * SLH-DSA (sha2-128f) message-dependence target (RQ-S1): <b>fixed vs. random message</b>, fixed key.
 *
 * <p>Class 0 signs one fixed message; class 1 signs a fresh random message per slot. Because SLH-DSA is
 * hash-based with a fixed, data-independent number of operations, signing time is expected to be
 * <em>independent</em> of the message — so this target is expected to come back CLEAN, in direct
 * contrast to ML-DSA. Confirming that on a detector which flags the synthetic positive control and
 * ML-DSA's message-dependence makes the null meaningful.
 *
 * <p>The {@code sha2_128f} (fast) parameter set is used so signing (~milliseconds) permits usable
 * sample sizes; the small variants sign in hundreds of milliseconds.
 */
public final class SlhDsaSha2128fSignMessageTarget implements MeasurementTarget {

    private static final int MSG_LEN = 32;

    private final long seed;
    private SlhDsaKeys keys;
    private SLHDSASigner signer;  // key set once; reused per signature
    private byte[] fixedMessage;  // class 0
    private byte[] message;       // reusable message buffer, fixed address for both classes

    public SlhDsaSha2128fSignMessageTarget() {
        this(0x51D5AL);
    }

    public SlhDsaSha2128fSignMessageTarget(long seed) {
        this.seed = seed;
    }

    @Override
    public String name() {
        return "slh-dsa-sha2-128f:sign-msg-fixed-vs-random";
    }

    @Override
    public void setup(RandomGenerator rng) {
        keys = new SlhDsaKeys(seed, SLHDSAParameters.sha2_128f);
        signer = keys.newSigner();
        fixedMessage = new byte[MSG_LEN];
        for (int i = 0; i < MSG_LEN; i++) {
            fixedMessage[i] = (byte) (i * 7 + 1);
        }
        message = new byte[MSG_LEN];
    }

    @Override
    public int slotBytes() {
        return MSG_LEN;
    }

    @Override
    public void fillSlot(byte[] backing, int offset, int klass, RandomGenerator rng) {
        if (klass == 0) {
            System.arraycopy(fixedMessage, 0, backing, offset, MSG_LEN);
        } else {
            for (int i = 0; i < MSG_LEN; i++) {
                backing[offset + i] = (byte) rng.nextInt();
            }
        }
    }

    @Override
    public Object compute(byte[] backing, int offset, int slotBytes) {
        System.arraycopy(backing, offset, message, 0, MSG_LEN);
        return signer.generateSignature(message);
    }
}
