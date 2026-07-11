# Experimental Design — Timing Side-Channel Leakage of ML-KEM and ML-DSA on the JVM

**Working title:** *Does the Managed Runtime Leak? A Constant-Time Analysis of NIST Post-Quantum Cryptography on the Java Virtual Machine*

**Author:** Arpan Sharma
**Status:** Design draft v0.1 — pre-registration of hypotheses and method. No results yet.
**Repository:** `pqc-jvm-sidechannel` (standalone; not part of `PQC-Java-Library-Comparison` or `pqc-migration-readiness`)

---

## 1. Motivation and gap

FIPS 203 (ML-KEM), FIPS 204 (ML-DSA), and FIPS 205 (SLH-DSA) are now the U.S. standards for
post-quantum key establishment and digital signatures. Constant-time behavior — the property that
execution time does not depend on secret data — is a hard requirement for any implementation that
handles long-lived secrets, because timing dependence is a remotely observable side channel that has
repeatedly broken deployed cryptography (Lucky 13, the various RSA/ECDSA timing attacks, and, for PQC
specifically, the KyberSlash division-timing family disclosed in 2024).

The published constant-time literature is almost entirely about C and assembly implementations,
analyzed with tools like `dudect`, TIMECOP/ctgrind, and hardware-counter TVLA. There is a **structural
gap**: no published work systematically characterizes whether *managed-runtime* implementations of the
NIST PQC standards — specifically pure-Java implementations running on the JVM — introduce or mask
timing leakage through runtime-specific mechanisms that simply do not exist in a C build:

- **Just-in-time (JIT) compilation.** HotSpot begins in the interpreter, then recompiles hot methods
  through C1 and C2 tiers. Tier transitions, on-stack replacement, deoptimization, and branch-profile-driven
  speculative optimizations can all make the *same bytecode* exhibit different, and potentially
  input-correlated, timing over the life of a process.
- **Garbage collection.** Allocation inside a decapsulation or signing routine can trigger GC pauses whose
  occurrence may correlate with input-dependent allocation volume.
- **Non-constant-time library primitives.** `BigInteger`, `Arrays.equals`, `MessageDigest` comparisons,
  and conditional copies in the library may branch on secret data regardless of the algorithm's design intent.

This matters in practice, not just in theory: Java dominates enterprise and government backend systems,
and NIST SP 800-208 together with the FIPS 140-3 validation regime require modules to resist side-channel
attack — yet there is no guidance or evidence base for the JVM. This is the exact compliance gap named in
the petitioner's filed research roadmap (2028 project).

## 2. Research questions

- **RQ1 (leakage existence).** Do pure-Java implementations of ML-KEM and ML-DSA (BouncyCastle
  `bcprov-jdk18on`) exhibit statistically significant input-dependent timing variation in their
  secret-handling operations (ML-KEM decapsulation; ML-DSA signing)?
- **RQ2 (runtime attribution).** To what extent is any observed leakage attributable to JVM mechanisms
  (JIT tiering, GC) versus to non-constant-time constructs in the library code itself? I.e., does leakage
  behavior change across execution modes (`-Xint` interpreter-only, C1-only, full tiered) and GC
  configurations (default G1 vs. Epsilon no-op collector)?
- **RQ3 (implementation comparison).** How does the leakage profile of the pure-Java implementation
  compare to a JNI-wrapped native implementation (`liboqs` via `liboqs-java`), which shares the JVM's GC
  and scheduling environment but executes constant-time-hardened native code?

RQ1 establishes whether there is a problem. RQ2 is the scientific core — it isolates *the JVM itself* as a
potential leakage source, which is the novel contribution. RQ3 provides a reference point that separates
"managed runtime effects" from "algorithm/implementation effects."

## 3. Hypotheses (pre-registered)

- **H1.** For at least one tested algorithm/operation, the pure-Java implementation shows a statistically
  significant timing difference between two secret-dependent input classes under the dudect fixed-vs-random
  methodology (rejection of the constant-time null at the pre-set threshold).
- **H2.** Leakage signal (effect size) is **larger** under full tiered JIT than under `-Xint`, and the
  *shape* of the timing distribution changes across execution modes — evidence that a component of any
  observed leakage is runtime-induced rather than purely algorithmic.
- **H3.** GC configuration (G1 vs. Epsilon) changes the tail behavior of the timing distribution;
  allocation-driven pauses contribute to measured variance and can confound naive leakage tests.
- **H0 (the honest null).** It is a fully publishable outcome for H1 to be *rejected* — i.e., to
  demonstrate with adequate statistical power that these implementations are constant-time on the JVM to
  within a stated detection bound. A rigorous negative result on a compliance-relevant question is a
  contribution, not a failure. The design must therefore be powered to support a credible negative claim,
  not only to detect a positive one.

## 4. Leakage / threat model

- **Attacker capability modeled:** an attacker who can measure the wall-clock latency of a targeted
  cryptographic operation many times, in the same process/host conditions, and who controls or observes the
  relevant input (ciphertext for KEM decapsulation; message for signing). This is the standard local/
  co-resident timing-attacker model used by dudect and TVLA. We do **not** model remote-network timing
  attacks in this study (that is Project 2's territory), nor power/EM channels.
- **Secret of interest:** the ML-KEM decapsulation secret key (leakage would enable a KEM key-recovery /
  Fujisaki–Okamoto-rejection-oracle style attack) and the ML-DSA signing key / per-signature nonce path.
- **Out of scope:** cache attacks, speculative-execution channels (Spectre-class), multi-tenant cloud
  co-residency specifics, and fault attacks. Named explicitly so reviewers know the boundary.

## 5. Variables

**Independent variables (the experiment matrix):**

| Factor | Levels |
|---|---|
| Algorithm / operation | ML-KEM-768 decapsulation; ML-DSA-65 signing (primary). ML-KEM-512/1024, ML-DSA-44/87, SLH-DSA verify as extension. |
| Implementation | BouncyCastle pure Java (primary); liboqs-java JNI (reference). |
| JIT mode | Full tiered (default); C1-only (`-XX:TieredStopAtLevel=1`); interpreter-only (`-Xint`). |
| GC | G1 (default); Epsilon (`-XX:+UseEpsilonGC`, no-op). |
| Input class | dudect "fixed" class vs. "random" class (definitions in §6). |

**Dependent variable:** per-operation execution time. Primary instrument: `System.nanoTime()` deltas
around the isolated operation. Secondary/corroborating: JMH-reported percentiles and, where available,
`perfnorm`/`perfasm` hardware-counter profilers and JFR for GC/JIT event correlation.

**Controlled / nuisance variables (held fixed or recorded):** JDK build (pinned OpenJDK 21, vendor and
exact version recorded), CPU governor/frequency, background load, warm-up policy, heap size, process
affinity where the OS permits. See §9.

## 6. Methodology

### 6.1 dudect-style fixed-vs-random (primary test for RQ1)

Port the dudect leakage-detection procedure (Reparaz, Balasch, Verbauwhede, 2017) to the JVM:

1. Define two input classes. **Fixed class:** a single constant secret-dependent input (e.g., one fixed
   ciphertext / one fixed message). **Random class:** inputs drawn uniformly at random per measurement.
   For ML-KEM we will additionally test a targeted pair that exercises the implicit-rejection branch of
   FO decapsulation (valid vs. deliberately malformed ciphertext), since that is the known-sensitive path.
2. Interleave measurements of the two classes randomly (never block A then block B) to avoid drift/thermal
   confounds being aliased into the class variable.
3. Measure the isolated operation with `System.nanoTime()`. Record raw timings, not just aggregates.
4. Apply the two-sided **Welch's t-test** on trimmed/cropped percentile buckets (the dudect approach:
   test at multiple crop thresholds to discard tail outliers from preemption/GC). Report the maximum |t|.
5. **Decision rule:** dudect's conventional threshold is |t| > 4.5 (≈ 5-sigma) to declare leakage. We
   pre-commit to this threshold. We additionally report effect size (difference of means normalized by
   pooled SD), because with very large N a tiny, operationally irrelevant difference can cross |t| = 4.5;
   effect size lets a reader judge exploitability, not just detectability.

### 6.2 TVLA (corroborating, for robustness)

Run Test Vector Leakage Assessment (Welch's t on fixed-vs-random, the same statistical core used in
hardware CT evaluation, ISO/IEC 17825 lineage) as an independent cross-check, and — as a stronger
non-parametric backstop that does not assume normal tails — a permutation test on the difference of
medians. Agreement between dudect, TVLA, and the permutation test guards against a single test's
assumptions driving the conclusion.

### 6.3 Runtime attribution (RQ2 / RQ3)

Run the full §5 matrix. For RQ2, compare leakage signal and distribution shape across JIT modes and GC
settings. For RQ3, run the identical harness against liboqs-java. Correlate leakage spikes with JIT
compilation and GC events captured via JDK Flight Recorder to attribute variance to concrete runtime
events rather than inferring it.

### 6.4 Controls (mandatory — these make the result trustworthy)

- **Positive control:** a deliberately non-constant-time function (e.g., an early-exit `memcmp`-style
  byte-array comparison, or a secret-dependent branch with a `Thread.sleep`/busy-loop delta). The pipeline
  **must** flag this as leaky. If it does not, the harness is not sensitive enough and no negative result
  can be trusted.
- **Negative control:** a known constant-time operation (e.g., a fixed-iteration XOR over equal-length
  arrays, or a hardened `MessageDigest.isEqual` constant-time compare). The pipeline **must not** flag this.
- These two controls are run in every session and reported alongside every result. They calibrate the
  detection floor and define what "constant-time to within our detection bound" quantitatively means.

## 7. Statistical plan and power

- **Sample size:** target ≥ 1e6 measurements per (class, cell), tuned upward until the negative-control
  t-statistic is stable across repeated sessions. Report N per cell.
- **Multiple comparisons:** the matrix produces many t-tests; apply a Bonferroni/Holm correction to the
  family-wise threshold and report both raw and corrected decisions.
- **Repeatability:** every cell is run in ≥ 3 independent JVM processes (fresh process = fresh JIT/GC
  history) on separate occasions. A leakage claim must replicate across processes, not appear once.
- **Reporting:** publish raw timing distributions (histograms + percentile tables), max |t| per crop
  threshold, effect sizes, and the control outcomes. Commit the analysis scripts and the raw data (or a
  DOI'd archive if too large for git).

## 8. Threats to validity (and mitigations)

- **Measurement quantization / clock resolution.** `System.nanoTime()` resolution and call overhead can
  swamp a small signal. *Mitigation:* characterize timer overhead first; if the per-op time is too close to
  timer granularity, batch a fixed count of identical ops per measurement (documented) and/or use a
  higher-resolution source.
- **JIT warm-up aliasing.** If the fixed class runs mostly cold and the random class mostly warm, JIT state
  aliases into the class variable. *Mitigation:* randomized interleaving (§6.1 step 2) + explicit warm-up +
  reporting steady-state separately from warm-up transient.
- **OS scheduling / thermal / frequency scaling on Apple Silicon.** macOS gives no core pinning and limited
  frequency control; measurements pick up P/E-core migration and DVFS. *Mitigation:* treat the Apple-Silicon
  (arm64) runs as **exploratory**; the authoritative runs for publication are on a pinned-frequency Linux/
  x86-64 host with `taskset` core isolation, `cpupower` fixed governor, and hyperthreading disabled. Both
  environments' full specs are recorded; the paper's headline claims rest on the controlled host.
- **"Absence of evidence" trap.** A null result only means "no leakage detectable *by this method at this N
  on this platform*." *Mitigation:* the positive control quantifies the smallest effect the pipeline can
  catch, so the negative claim is stated *with* an explicit detection bound rather than as an absolute.
- **Library version specificity.** Results are tied to exact BouncyCastle / liboqs versions. *Mitigation:*
  pin and record versions; frame findings as version-specific and re-runnable.

## 9. Reproducibility requirements

- Pinned JDK: OpenJDK 21 (exact build string recorded in every results file).
- Pinned BouncyCastle 1.84 (`bcprov-jdk18on`), matching the petitioner's other repos; liboqs/liboqs-java
  versions recorded when RQ3 is run.
- Every result artifact embeds: full `java -version`, OS/kernel, CPU model, governor/frequency policy,
  JVM flags, heap size, N, and git commit hash of the harness.
- One-command reproduction script per experiment cell; raw data + analysis notebook committed under
  `results/`.

## 10. Deliverables and target venues

- **Artifact:** an open-source, reusable JVM constant-time / leakage-testing harness (dudect + TVLA +
  permutation test, with the control suite) — useful beyond this paper, which strengthens the contribution.
- **Paper 1 (primary):** the leakage characterization of ML-KEM / ML-DSA on the JVM with runtime attribution.
  - Positive-result venues (if leakage found): CHES/TCHES, PQCrypto, ACNS.
  - Compliance/engineering framing (either outcome): IEEE SecDev, ACSAC, ARES.
  - Immediate: IACR ePrint + arXiv preprint on submission.

## 11. Explicit non-goals for this study

- Not a network/remote timing attack (that is the separate hybrid-TLS project).
- Not a full key-recovery exploit; we measure *leakage*, and discuss exploitability, but a working
  end-to-end attack is future work if strong leakage is found.
- Not power/EM/cache/Spectre channels.
- Not a performance/throughput benchmark — that is what `PQC-Java-Library-Comparison` already covers; this
  repo is deliberately kept separate.

---

*This document is a pre-registration: hypotheses, thresholds (|t| > 4.5), and the control protocol are
fixed before data collection so that a negative result carries the same weight as a positive one.*
