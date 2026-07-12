package org.pqcsidechannel.targets.mldsa;

import org.bouncycastle.pqc.crypto.mldsa.MLDSASigner;
import org.pqcsidechannel.measure.MeasurementTarget;

import java.util.random.RandomGenerator;

/**
 * Primary ML-DSA-65 target (RQ-D1): <b>fixed vs. random message</b>, deterministic signing, fixed key.
 *
 * <p>Class 0 signs one fixed message; class 1 signs a fresh random message per slot. Because signing
 * is deterministic (randomness fixed), signing time is a function of (key, message), so different
 * messages take different rejection-sampling iteration counts and hence different times. The class-0
 * distribution is tight (one message → one iteration count); the class-1 distribution is spread over
 * many. The pipeline is expected to flag this — ML-DSA signing is input-dependent by construction.
 * The value is quantifying that dependence on the JVM and contrasting it with ML-KEM's null.
 */
public final class MlDsa65SignMessageTarget implements MeasurementTarget {

    private static final int MSG_LEN = 32;

    private final long seed;
    private MlDsa65Keys keys;
    private MLDSASigner signer;   // key expanded once; reused per signature
    private byte[] fixedMessage;  // class 0
    private byte[] message;       // reusable message buffer, fixed address for both classes

    public MlDsa65SignMessageTarget() {
        this(0x0D5A65L);
    }

    public MlDsa65SignMessageTarget(long seed) {
        this.seed = seed;
    }

    @Override
    public String name() {
        return "ml-dsa-65:sign-msg-fixed-vs-random-deterministic";
    }

    @Override
    public void setup(RandomGenerator rng) {
        keys = new MlDsa65Keys(seed);
        signer = keys.newDeterministicSigner();
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
        signer.reset();
        signer.update(message, 0, MSG_LEN);
        return MlDsa65Keys.generate(signer);
    }
}
