# ML-KEM-768 decapsulation - preliminary leakage results (exploratory host)

First measurements of ML-KEM-768 decapsulation (BouncyCastle 1.84, pure Java) through the validated
pipeline. **Headline: no timing leakage was detected in any configuration tested**, on a pipeline that
demonstrably detects a planted leak (positive control max|t| ≈ 23,000). This is a preliminary
*negative* result on an exploratory host; authoritative claims await the pinned Linux/x86 runs.

## What was tested

Two input designs, each through the full dudect + TVLA + Mann - Whitney pipeline, at multiple N with
repeated independent runs:

- **decap-valid** - fixed valid ciphertext vs. random valid ciphertexts (first-order: does decap time
  depend on which valid ciphertext is processed?).
- **decap-rejection** - valid vs. rejected ciphertexts (one flipped byte forces the Fujisaki - Okamoto
  implicit-rejection branch; the known-sensitive path). Both classes run the full decapsulation, so
  the only systematic difference is the branch outcome.

## Results - default JVM (full tiered JIT, G1 GC)

| Design | N | dudect max\|t\| | TVLA t | Mann - Whitney z | Cohen's d | Verdict |
|---|---|---|---|---|---|---|
| decap-rejection | 200,000 (A) | 2.417 | −0.418 | 1.043 | −0.0019 | CLEAN |
| decap-rejection | 200,000 (B) | 1.673 | 0.327 | −0.556 | 0.0015 | CLEAN |
| decap-rejection | 500,000 | 2.115 | −0.115 | −0.719 | −0.0003 | CLEAN |
| decap-valid | 200,000 (A) | 1.429 | 0.283 | −0.764 | 0.0013 | CLEAN |
| decap-valid | 200,000 (B) | 1.867 | −0.332 | −1.266 | −0.0015 | CLEAN |
| decap-valid | 500,000 | 1.588 | 1.588 | 0.215 | 0.0045 | CLEAN |

The dudect statistic stays flat (1.4-2.4) as N rises from 200k to 500k - no growth with N, the
signature of a true null - and the first-order t and effect size are ≈ 0 throughout. All three tests
agree.

## RQ2 - across JIT tiers (decap-rejection)

| JIT mode | mean decap time | dudect max\|t\| | Verdict |
|---|---|---|---|
| full tiered (default) | ~40 µs | 1.7-2.4 | CLEAN |
| C1-only (`-XX:TieredStopAtLevel=1`) | ~88 µs | 1.559 | CLEAN |
| interpreter (`-Xint`) | ~2.86 ms | 1.886 | CLEAN |

Absolute latency varies ~70× across tiers - the harness clearly captures the JIT dimension - yet the
constant-time property holds in every tier. (GC dimension, incl. Epsilon, still to run; see below.)

## Detection bound (why the negative result has teeth)

At N = 500,000 on this host, the standard error of the class mean difference implies the smallest
per-decapsulation timing difference the test would have flagged at |t| > 4.5 is roughly:

- decap-valid: ≈ 0.45 µs (~1.1% of the ~40 µs decapsulation)
- decap-rejection: ≈ 0.86 µs (~2% of decapsulation)

No difference near this magnitude was observed (observed mean differences were tens to ~160 ns, with
d ≈ 0.001-0.005). The planted positive-control leak (~1.15 µs mean difference, d ≈ 1.7) is detected
trivially, so the pipeline's sensitivity comfortably brackets the region where a real leak would lie.

## Environment

- JDK OpenJDK 21.0.11 (Homebrew); macOS 27.0, arm64 (Apple Silicon), 8 logical processors; **unpinned**.
- BouncyCastle `bcprov-jdk18on` 1.84, lightweight `org.bouncycastle.pqc.crypto.mlkem` API.
- Single deterministic key pair (seed 0xC0DECAFE); ML-KEM-768 ciphertext length 1088 bytes.

## Caveats and honest scope

- **Exploratory host.** macOS/arm64, no core pinning or frequency lock. Authoritative runs belong on
  a pinned-frequency, core-isolated Linux/x86 host (design doc §8 - §9). Treat these numbers as a
  well-controlled first look, not the final claim.
- **Default GC only.** G1 throughout. The Epsilon (no-op GC) comparison for the RQ3/GC dimension is
  not yet run (Epsilon risks OOM under sustained decap allocation; needs a bounded-N, large-heap run).
- **One rejection construction.** The rejection class flips the final ciphertext byte. Corrupting
  other positions (the `u` part, various coefficients) and using fully random garbage ciphertexts are
  worthwhile variants to exercise more of the decode/re-encrypt path.
- **One key, one library version.** Findings are specific to this key pair and BouncyCastle 1.84.

## Next

1. Re-run the full matrix on a pinned Linux/x86 host for authoritative claims.
2. Add the GC dimension (G1 vs. Epsilon, bounded N).
3. Larger N (≥ 1e6) and multiple key pairs to tighten the detection bound.
4. Extend to ML-DSA-65 signing (rejection-sampling loop) as the next algorithm.
