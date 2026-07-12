# SLH-DSA (sha2-128f) signing — preliminary leakage results (exploratory host)

Measurements of SLH-DSA signing (BouncyCastle 1.84, pure Java, `sha2_128f`, deterministic) through the
validated pipeline. **Headline: SLH-DSA signing is constant-time — no message-dependence detected**, as
expected for a stateless hash-based signature with data-independent control flow. This completes the
three-standard study.

## Result

| Target | N | dudect max\|t\| | TVLA t | mean class0 → class1 | Cohen's d | Verdict |
|---|---|---|---|---|---|---|
| sign fixed-vs-random message | 5,000 | 1.53 | 0.74 | 38.195 → 38.140 ms | 0.02 | **CLEAN** |
| sign fixed-vs-random message | 4,000 | 2.46 | −1.02 | 52.55 → 53.87 ms | −0.03 | **CLEAN** |

Both runs are CLEAN: the fixed-message and random-message classes have statistically indistinguishable
signing times (within-run difference ≲ 2.5%, not significant). Signing time does not depend on the
message — the opposite of ML-DSA, and the expected result for a hash-based scheme whose signing
performs a fixed number of hash evaluations regardless of the input.

**Detection bound.** At N = 5,000, the smallest message-dependent difference the test would have flagged
at |t| > 4.5 is ≈ 0.34 ms (~0.9% of the ~38 ms signing time); none was observed.

## A note on absolute-time drift (and why the design holds)

The absolute mean signing time differed markedly between runs (~38 ms vs ~52 ms) — the Apple-Silicon
host thermally throttles under sustained multi-minute load, and SLH-DSA signing is slow (~tens of ms).
Yet both runs are CLEAN, because the two classes are measured **interleaved** under the same conditions
within each run, so the drift is shared and cancels in the class comparison. This is exactly the
robustness the randomized-interleaving design buys — and also why authoritative magnitude claims want a
thermally stable, pinned Linux/x86 host.

## Environment

- JDK OpenJDK 21.0.11 (Homebrew); macOS 27.0, arm64 (Apple Silicon); **unpinned** (exploratory).
- BouncyCastle `bcprov-jdk18on` 1.84, lightweight `org.bouncycastle.pqc.crypto.slhdsa` API.
- Parameter set `sha2_128f` (fast variant; small variants sign in hundreds of ms). Deterministic
  signing, verified by test. Single key pair (seed 0x51D5A); 32-byte messages.

## The completed three-standard contrast

| Standard | Mechanism | Result |
|---|---|---|
| ML-KEM-768 | lattice KEM, constant-time by design | **constant-time** (incl. FO rejection path) |
| ML-DSA-65 | lattice signature, rejection sampling | **message-dependent** (~1.9×); **small key-dependence** (~1–1.5%) |
| SLH-DSA sha2-128f | hash-based, data-independent control flow | **constant-time** (message-independent) |

One validated detector, synthetic + real-cryptography controls, across all three NIST PQC standards on
the JVM. ML-DSA is the outlier — variable-time by construction, and the only one with a (small) secret-
dependent channel.

## Caveats and next

- Exploratory host; slow signing caps N (thermal drift over long runs). Authoritative runs on a pinned
  Linux/x86 host, and a faster environment, would allow larger N and tighter bounds.
- `sha2_128f` only; other SLH-DSA parameter sets (and a key-dependence check) are natural extensions,
  though all are expected constant-time on structural grounds.
- Remaining program: RQ-D3 (hedged vs deterministic ML-DSA), many-key ML-DSA key-channel
  characterization, JIT/GC (RQ2) and the full matrix on a pinned host.
