package org.pqcsidechannel.measure;

import java.util.random.RandomGenerator;

/**
 * A thing whose timing is being tested for secret dependence, following the dudect two-class model.
 *
 * <p>Class 0 and class 1 differ only in their <em>input contents</em> — class 0 is a fixed input,
 * class 1 varies. The harness ({@link Measurement}) holds a pool of many input slots in one flat
 * backing array and assigns each slot a class at random, so that neither class is systematically
 * associated with a particular memory address or cache state. This is what makes a difference in
 * timing attributable to the input <em>contents</em> rather than to memory layout — a distinction
 * that matters, because concentrating each class at a fixed address was observed during control
 * validation to manufacture a false, N-growing leakage signal even for a constant-time operation.
 *
 * <p>Contract:
 * <ul>
 *   <li>{@link #fillSlot} runs once per slot during setup (outside timing). It writes the input for
 *       the given class into {@code backing[offset .. offset+slotBytes)}.</li>
 *   <li>{@link #compute} is the timed operation over one slot. It returns a value derived from the
 *       real work so the {@link Sink} can stop the JIT from deleting it.</li>
 * </ul>
 */
public interface MeasurementTarget {

    /** Human-readable name recorded in results. */
    String name();

    /** One-time setup: generate the secret/key. Called once before the input pool is built. */
    void setup(RandomGenerator rng);

    /** Size in bytes of one input slot. */
    int slotBytes();

    /** Fill one input slot for the given class ({@code 0} = fixed, {@code 1} = varied). */
    void fillSlot(byte[] backing, int offset, int klass, RandomGenerator rng);

    /** The timed operation over the slot at {@code backing[offset .. offset+slotBytes)}. */
    Object compute(byte[] backing, int offset, int slotBytes);
}
