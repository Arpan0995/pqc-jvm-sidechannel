# Control validation — the leakage pipeline detects a known leak and clears a known non-leak

Before measuring any real cryptography, the pipeline is validated against two controls with known
ground truth. This is the precondition for trusting any result — especially a *negative* result — it
later produces about ML-KEM.

- **Positive control** (`early-exit-compare`): a `memcmp`-style byte comparison that returns on the
  first mismatch. Its timing depends on the input by construction, so the pipeline **must flag it**.
- **Negative control** (`constant-time-compare`): a fixed-length, branch-free XOR-accumulate compare.
  Its timing is input-independent, so the pipeline **must not flag it**.

## Result (validated)

| Control | dudect max\|t\| | TVLA Welch t | Cohen's d | Verdict |
|---|---|---|---|---|
| positive (N=1,000,000) | 22,886 | 861 | 1.75 | **LEAKY** ✓ |
| negative (N=1,000,000) | 1.9–4.6 (fluctuates) | ≈ 0.02 | ≈ 0.0000 | **CLEAN / MARGINAL** ✓ |

The positive control is flagged by four orders of magnitude of margin. The negative control's
first-order signal and effect size are essentially zero; its dudect cropped-max fluctuates near the
4.5 threshold from measurement noise (see "Marginal verdicts" below).

**Decisive N-scaling check.** Under the constant-time null the t-statistic does not grow with sample
size; under a real leak it grows ~√N. Measured on the negative control:

| N | negative dudect max\|t\| |
|---|---|
| 500,000 | 2.78 |
| 2,000,000 | 3.33 |

Flat as N quadruples — the signature of a true null. (Before the fixes below, this same figure grew
15 → 25 with N, which is what exposed the artifacts.)

## Environment

- JDK: OpenJDK 21.0.11 (Homebrew), 64-Bit Server VM
- OS/CPU: macOS 27.0, arm64 (Apple Silicon), 8 logical processors
- `System.nanoTime()` calibration: ~42 ns back-to-back overhead, 41 ns min non-zero delta
- **Unpinned**: no core affinity, no fixed CPU frequency. Per the design doc §8, this host is
  **exploratory**; authoritative runs belong on a pinned-frequency, core-isolated Linux/x86 host.

## Three measurement artifacts found and fixed

Building the controls surfaced three ways the *harness itself* can manufacture a false leakage signal.
Each was caught because the negative control lit up when it should not have. This is the core
methodological lesson of the increment.

1. **Allocation / cache-temperature asymmetry.** The first design reused one cached buffer for the
   fixed class but allocated a fresh buffer per measurement for the random class. The random class
   then read cold memory while the fixed class read warm memory — a ~22 max|t| false signal on a
   constant-time op. *Fix:* stop allocating per measurement.

2. **Fill-mechanism asymmetry.** Filling one shared buffer per iteration still differed by class
   (`arraycopy` for fixed vs. an RNG fill for random), leaving the buffer in different cache states
   before the timed read (~12 max|t|). *Fix:* fill both classes by the identical operation.

3. **Fixed-address layout confound (the subtle one).** Giving each class its own persistent buffer at
   a fixed address tied the class label to a fixed memory layout. First-order means stayed equal
   (TVLA t ≈ 0), but the dudect cropped-max **grew with N** (15 → 25) — a small, systematic,
   layout-driven distributional difference confounded with the class. *Fix:* the dudect scattered-slot
   pool — many input slots in one flat backing array, each assigned a class at random, visited in
   randomized order — so memory layout is decorrelated from the class label instead of aliased onto
   it. This is the measurement engine the real ML-KEM study uses.

## Marginal verdicts near threshold

Taking the maximum |t| over ~100 percentile crops inflates the false-positive rate near 4.5: a
genuinely constant-time operation can graze `|t| > 4.5` on a single run while its first-order TVLA t
and Cohen's d are ~0 (observed: max|t| = 4.626 with d = 0.0000, means differing by 0.3 ns). The
pipeline therefore reports a three-level verdict — **LEAKY** only when the dudect flag is corroborated
by the first-order test, **MARGINAL** when only the fragile cropped-max crosses — and the authoritative
determination is the reproducibility protocol (design doc §7): a real leak reproduces across
independent runs and grows with N; noise near threshold does neither.

## What this unblocks

The pipeline is trustworthy: it catches an obvious leak by a huge margin, clears a constant-time
operation, and distinguishes a true null (flat in N) from a real effect (grows with N). The next
increment points it at ML-KEM-768 decapsulation — starting with the FO implicit-rejection path, the
known-sensitive target.
