package org.pqcsidechannel.targets.mldsa;

import org.bouncycastle.pqc.crypto.mldsa.MLDSASigner;
import org.pqcsidechannel.measure.MeasurementTarget;

import java.util.random.RandomGenerator;

/**
 * ML-DSA-65 key-dependence target (RQ-D4): <b>two fixed keys over random messages</b>, deterministic.
 *
 * <p>Class 0 signs with key A, class 1 with key B; both classes sign fresh random messages. Because
 * both draw from the same random-message distribution, the message contribution is a common nuisance
 * that averages out, and the only systematic difference between the classes is the secret key. A LEAKY
 * verdict means signing time depends on the key (an exploitability-relevant channel); a CLEAN verdict
 * bounds key-dependence — the expected outcome, since ML-DSA's rejection rate is designed to be
 * essentially key-independent, so the strong message-dependence of {@link MlDsa65SignMessageTarget}
 * would then be driven by the public message rather than the secret key.
 *
 * <p>Slot layout: byte 0 is the key selector (0 = A, 1 = B), bytes 1..1+MSG_LEN are the message.
 */
public final class MlDsa65SignKeyDependenceTarget implements MeasurementTarget {

    private static final int MSG_LEN = 32;
    private static final int SLOT_BYTES = 1 + MSG_LEN;

    private final long seedA;
    private final long seedB;
    private MLDSASigner signerA;
    private MLDSASigner signerB;

    public MlDsa65SignKeyDependenceTarget() {
        this(0x0D5A65L, 0x0D5A66L);
    }

    public MlDsa65SignKeyDependenceTarget(long seedA, long seedB) {
        this.seedA = seedA;
        this.seedB = seedB;
    }

    @Override
    public String name() {
        return "ml-dsa-65:sign-keyA-vs-keyB-random-message-deterministic";
    }

    @Override
    public void setup(RandomGenerator rng) {
        signerA = new MlDsa65Keys(seedA).newDeterministicSigner();
        signerB = new MlDsa65Keys(seedB).newDeterministicSigner();
    }

    @Override
    public int slotBytes() {
        return SLOT_BYTES;
    }

    @Override
    public void fillSlot(byte[] backing, int offset, int klass, RandomGenerator rng) {
        backing[offset] = (byte) klass; // key selector
        for (int i = 0; i < MSG_LEN; i++) {
            backing[offset + 1 + i] = (byte) rng.nextInt(); // random message for both classes
        }
    }

    @Override
    public Object compute(byte[] backing, int offset, int slotBytes) {
        MLDSASigner signer = (backing[offset] & 1) == 0 ? signerA : signerB;
        signer.reset();
        signer.update(backing, offset + 1, MSG_LEN);
        return MlDsa65Keys.generate(signer);
    }
}
