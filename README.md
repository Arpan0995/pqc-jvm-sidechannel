# pqc-jvm-sidechannel

**Constant-time / timing side-channel analysis of NIST post-quantum cryptography (ML-KEM, ML-DSA) on the Java Virtual Machine.**

Do pure-Java implementations of FIPS 203 (ML-KEM) and FIPS 204 (ML-DSA) leak secret-dependent timing
through JVM-specific mechanisms — JIT tiering, garbage collection, and non-constant-time library
primitives — that do not exist in the C/assembly implementations the constant-time literature has
studied? This repository is the experiment that answers it.

> This is a **security / leakage** study. It is intentionally separate from
> [`PQC-Java-Library-Comparison`](https://github.com/Arpan0995/PQC-Java-Library-Comparison) (a
> *performance* benchmark) and from
> [`pqc-migration-readiness`](https://github.com/Arpan0995/pqc-migration-readiness) (a static
> auditor/agility framework). Different question, different method, different paper.

## Status

Design phase. The full pre-registered experimental design — research questions, hypotheses,
threat model, statistical plan, and validity threats — is in
[`docs/EXPERIMENT-DESIGN.md`](docs/EXPERIMENT-DESIGN.md). Hypotheses and the leakage-detection
threshold (`|t| > 4.5`) are fixed **before** data collection so a negative result carries the same
weight as a positive one.

## Approach in one paragraph

Port the `dudect` fixed-vs-random leakage-detection method to the JVM, corroborate with TVLA
(Welch's t) and a non-parametric permutation test, and run every measurement through a mandatory
positive control (a deliberately variable-time function that *must* be flagged) and negative control
(a constant-time function that *must not* be). Sweep the experiment across JIT modes
(`-Xint`, C1-only, full tiered) and GC settings (G1 vs. Epsilon) to attribute any leakage to the
runtime versus the algorithm, and compare pure-Java BouncyCastle against a JNI-wrapped native
`liboqs` reference.

## Toolchain

- Java 21 (pinned OpenJDK 21; exact build recorded in every result file)
- BouncyCastle `bcprov-jdk18on` 1.84
- JMH 1.37 for measurement harnessing; JDK Flight Recorder for JIT/GC event correlation
- Maven multi-module

Apple-Silicon (arm64) runs are **exploratory only**; authoritative runs for publication are on a
pinned-frequency, core-isolated Linux/x86-64 host (see design doc §8–§9).

## Layout

```
docs/EXPERIMENT-DESIGN.md   Pre-registered design (read this first)
harness/                    Measurement + leakage-analysis code (not yet written)
results/                    Raw timing data, control outcomes, analysis (per-run, reproducible)
```

## License

Apache-2.0 (see `LICENSE`).
