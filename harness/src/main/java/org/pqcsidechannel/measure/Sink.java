package org.pqcsidechannel.measure;

/**
 * A minimal dead-code-elimination guard. The JIT is aggressive about proving that the result of a
 * timed operation is unused and deleting the call outright, which would make timing meaningless.
 * Every measured computation feeds its result here; {@link #value()} is read at the end of a run so
 * the accumulation cannot be proven dead.
 *
 * <p>This is a deliberately simple stand-in for JMH's {@code Blackhole}. The custom dudect-style
 * measurement loop needs per-call timings with class labels, which JMH's aggregate model does not
 * provide, so we cannot rely on {@code Blackhole} here.
 */
public final class Sink {

    private long state = 0xC0FFEE_15_600DL;

    public void consume(boolean b) {
        state += b ? 0x9E3779B97F4A7C15L : 0x1234567891234567L;
    }

    public void consume(long v) {
        state ^= v + 0x9E3779B97F4A7C15L + (state << 6) + (state >>> 2);
    }

    public void consume(byte[] bytes) {
        if (bytes == null) {
            state += 0xABCDEF;
            return;
        }
        long h = state;
        for (byte b : bytes) {
            h = (h ^ b) * 0x100000001B3L;
        }
        state = h;
    }

    public void consume(Object o) {
        state += (o == null) ? 0 : System.identityHashCode(o);
    }

    /** Read the accumulated state so the JIT cannot eliminate the consume calls as dead. */
    public long value() {
        return state;
    }
}
