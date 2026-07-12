# ML-DSA-65 signing — preliminary leakage results (exploratory host)

First measurements of ML-DSA-65 signing (BouncyCastle 1.84, pure Java, deterministic signing) through
the validated pipeline. **Headline: ML-DSA-65 signing time is strongly input-dependent** — the opposite
of ML-KEM — **and it is constant-time when the input is held fixed.** Both facts are measured by the
same detector, which gives the paper its central contrast.

## Result

| Target | N | dudect max\|t\| | TVLA t | mean class0 → class1 | Cohen's d | Verdict |
|---|---|---|---|---|---|---|
| sign fixed-vs-random message | 100,000 | 211.2 | −129.1 | 274,653 → 518,459 ns | −0.82 | **LEAKY** |
| sign fixed-vs-random message | 200,000 | 304.7 | −169.7 | 272,843 → 517,187 ns | −0.77 | **LEAKY** |
| sign fixed message (control) | 100,000 | 2.49 | 1.89 | 274,217 → 272,706 ns | 0.01 | **CLEAN** |

- **Message-dependence is large and real.** A fixed message signs in ~273 µs; random messages average
  ~517 µs — about **1.9×** — because signing time tracks the rejection-sampling iteration count, which
  varies with the message. All three statistics agree, and the TVLA t **grows with N** (129 → 170 as N
  doubles), the signature of a genuine effect rather than noise.
- **Fixed input ⇒ constant time.** With both classes signing the *same* message, the means are within
  0.6% and the verdict is CLEAN. This real-cryptography negative control proves the leakage above is
  genuine message-dependence, not the detector reacting to ML-DSA's nondeterminism or a harness
  artifact.

## The contrast that makes the study

| Standard | Operation | Result |
|---|---|---|
| ML-KEM-768 | decapsulation (valid, and valid-vs-rejected) | constant-time (no leakage detected) |
| ML-DSA-65 | signing (fixed vs random message) | input-dependent (rejection sampling), ~1.9× |
| ML-DSA-65 | signing (fixed message) | constant-time |

Measured by one validated detector, with both a synthetic control suite and real-cryptography controls.

## Interpretation (no over-claiming)

A LEAKY verdict here is the **expected, correct** result, not a vulnerability report. ML-DSA is
variable-time by design, and the message is public, so message-dependent timing is not by itself a
secret leak. The value of this measurement is threefold:

1. **Detector validation on a real variable-time primitive** — the pipeline flags a known-variable-time
   operation by a huge margin, complementing the synthetic positive control.
2. **The channel survives the JVM.** The rejection-count timing signal is clearly observable through
   JIT/GC noise (t ≈ 130–170), so a managed runtime does not mask it — relevant to real-world
   exploitability.
3. **Setup for the exploitability question (RQ-D4).** Whether signing time depends on the *secret key*
   (fixed-key vs random-key), and whether the iteration-count channel can be tied to key material, is
   the actual security question and is the next experiment.

## Environment

- JDK OpenJDK 21.0.11 (Homebrew); macOS 27.0, arm64 (Apple Silicon); **unpinned** (exploratory).
- BouncyCastle `bcprov-jdk18on` 1.84, lightweight `org.bouncycastle.pqc.crypto.mldsa` API.
- Deterministic signing (FIPS 204 rnd = 0), verified deterministic by test. Single key pair (seed
  0x0D5A65). ML-DSA-65 signature length 3309 bytes; 32-byte messages.

## RQ-D4 — key dependence (the exploitability question)

Message-dependence leaks a *public* value. The security-relevant question is whether signing time
depends on the *secret key*. **Finding: yes — a small but real key-dependent timing difference exists.**
This was the expected-to-be-null test, so it was checked three independent ways before concluding.

**(1) Two keys, random messages, deterministic (`mldsa-sign-keydep`).**

| Orientation | N | slower key | gap | TVLA t | verdict |
|---|---|---|---|---|---|
| class0=A, class1=B | 100,000 | **B** (547.5 vs 539.0 µs) | ~8.5 µs | −3.32 | MARGINAL |
| class0=B, class1=A (swapped) | 100,000 | **B** (533.7 vs 517.5 µs) | ~16 µs | +6.47 | LEAKY |
| class0=A, class1=B | 250,000 | **B** (542.8 vs 535.7 µs) | ~7.2 µs | −4.38 | MARGINAL |

The decisive check is the **A/B swap**: the slower party is **key B in both orientations**, so the
effect follows the *key*, not the class label and not the object-creation order (both of which flip on
swap). That rules out a memory-placement or harness artifact. The effect also **grows with N**
(|t| 3.32 → 4.38 as N goes 100k → 250k), ruling out pure noise. It sits near the threshold because it
is *small* (Cohen's d ≈ 0.02).

**(2) Paired multi-key probe (`MlDsa65MultiKeyProbe`).** Six independent keys sign the *same* 25,000
random messages, interleaved (message effect fully controlled):

| key seed | mean sign time | vs key0 |
|---|---|---|
| 100 | 537,134 ns | baseline |
| 101 | 533,104 ns | −0.75% |
| 102 | 530,513 ns | −1.23% |
| 103 | 532,116 ns | −0.93% |
| 104 | 538,573 ns | +0.27% |
| 105 | 535,614 ns | −0.28% |

A ~1.5% spread (≈ 8 µs), well beyond the standard error of a 25k-sample mean, non-monotonic in loop
position (so not a position artifact), and reproducible (the same ordering appears at 5k messages).

**Conclusion and interpretation.** ML-DSA-65 signing time is **key-dependent**, because per-key average
rejection rates differ slightly (the rejection rate is designed to be *approximately*, not exactly,
key-independent). The effect is **small (~1–1.5%)** — far below the message-dependence (~90% / 1.9×) —
but real and measurable through JVM noise, which is the exploitability-relevant point: the secret does
influence timing. This does **not** demonstrate key recovery; it establishes that the key→timing
channel is nonzero, motivating constant-time hardening and further study (many keys; can the channel be
tied to specific key bits?).

*Honest limits:* two keys in the dudect test (near threshold on a single run); six keys in the paired
probe; exploratory unpinned host. Magnitudes need a pinned Linux/x86 host to pin down.

## Caveats and next

- Exploratory host; authoritative runs belong on a pinned-frequency, core-isolated Linux/x86 host.
- Default full-tiered JIT + G1 GC. JIT/GC sweep (RQ2) not yet applied to ML-DSA.
- **Next experiments:** hedged vs deterministic signing (RQ-D3); many-key characterization of the
  key→timing channel; SLH-DSA (expected constant-time, hash-based) to complete the three-standard
  study; then the full matrix on a pinned Linux host.
