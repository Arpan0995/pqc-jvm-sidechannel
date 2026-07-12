package org.pqcsidechannel.targets.mldsa;

import org.bouncycastle.pqc.crypto.mldsa.MLDSASigner;
import org.pqcsidechannel.measure.MeasurementTarget;

import java.util.random.RandomGenerator;

/**
 * ML-DSA-65 real-cryptography negative control (RQ-D2): both classes sign the <b>same fixed message</b>
 * with deterministic signing and a fixed key.
 *
 * <p>Because the message, key, and randomness are all fixed, every signature is identical and takes the
 * same rejection-sampling path, so signing time is constant. This target is expected to be CLEAN — it
 * demonstrates that ML-DSA signing is constant-time when the input is held fixed, and therefore that a
 * LEAKY verdict on {@link MlDsa65SignMessageTarget} reflects genuine message-dependence rather than the
 * harness reacting to ML-DSA's nondeterminism or complexity.
 */
public final class MlDsa65SignFixedMessageTarget implements MeasurementTarget {

    private static final int MSG_LEN = 32;

    private final long seed;
    private MlDsa65Keys keys;
    private MLDSASigner signer;
    private byte[] fixedMessage;
    private byte[] message;

    public MlDsa65SignFixedMessageTarget() {
        this(0x0D5A65L);
    }

    public MlDsa65SignFixedMessageTarget(long seed) {
        this.seed = seed;
    }

    @Override
    public String name() {
        return "ml-dsa-65:sign-fixed-message-deterministic";
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
        // Both classes get the identical fixed message: the class label carries no difference at all.
        System.arraycopy(fixedMessage, 0, backing, offset, MSG_LEN);
    }

    @Override
    public Object compute(byte[] backing, int offset, int slotBytes) {
        System.arraycopy(backing, offset, message, 0, MSG_LEN);
        signer.reset();
        signer.update(message, 0, MSG_LEN);
        return MlDsa65Keys.generate(signer);
    }
}
