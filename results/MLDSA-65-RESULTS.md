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

## Caveats and next

- Exploratory host; authoritative runs belong on a pinned-frequency, core-isolated Linux/x86 host.
- Default full-tiered JIT + G1 GC. JIT/GC sweep (RQ2) not yet applied to ML-DSA.
- **Next experiments:** key-dependence (RQ-D4, the exploitability question), hedged vs deterministic
  signing (RQ-D3), then SLH-DSA (expected constant-time, hash-based) to complete the three-standard
  study, and finally the full matrix on a pinned Linux host.
