# pqc-jvm-sidechannel

**Constant-time / timing side-channel analysis of NIST post-quantum cryptography (ML-KEM, ML-DSA) on the Java Virtual Machine.**

Do pure-Java implementations of FIPS 203 (ML-KEM) and FIPS 204 (ML-DSA) leak secret-dependent timing
through JVM-specific mechanisms - JIT tiering, garbage collection, and non-constant-time library
primitives - that do not exist in the C/assembly implementations the constant-time literature has
studied? This repository is the experiment that answers it.

> This is a **security / leakage** study. It is intentionally separate from
> [`PQC-Java-Library-Comparison`](https://github.com/Arpan0995/PQC-Java-Library-Comparison) (a
> *performance* benchmark) and from
> [`pqc-migration-readiness`](https://github.com/Arpan0995/pqc-migration-readiness) (a static
> auditor/agility framework). Different question, different method, different paper.

## Status

Pipeline validated; first algorithm results in. The pre-registered design - research questions,
hypotheses, threat model, statistical plan, and validity threats - is in
[`docs/EXPERIMENT-DESIGN.md`](docs/EXPERIMENT-DESIGN.md) (ML-KEM),
[`docs/MLDSA-DESIGN.md`](docs/MLDSA-DESIGN.md) (ML-DSA), and
[`docs/SLHDSA-DESIGN.md`](docs/SLHDSA-DESIGN.md) (SLH-DSA). Thresholds are fixed **before** data
collection so a negative result carries the same weight as a positive one.

**Findings so far** (exploratory macOS/arm64 host; authoritative runs pending on pinned Linux/x86):

| Standard | Operation | Result |
|---|---|---|
| ML-KEM-768 | decapsulation (valid; valid-vs-rejected FO path) | constant-time - no leakage detected |
| ML-DSA-65 | signing (fixed vs random message, deterministic) | input-dependent (rejection sampling), ~1.9× |
| ML-DSA-65 | signing (fixed message) | constant-time |
| ML-DSA-65 | signing (key A vs key B / 6 keys, random messages) | small but real **key-dependence** (~1-1.5%) |
| SLH-DSA sha2-128f | signing (fixed vs random message) | constant-time - no leakage detected |

The detector is validated by a synthetic positive/negative control pair and by real-cryptography
controls. See [`results/`](results/): [control validation](results/CONTROL-VALIDATION.md),
[ML-KEM](results/MLKEM-768-RESULTS.md), [ML-DSA](results/MLDSA-65-RESULTS.md).

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
pinned-frequency, core-isolated Linux/x86-64 host (see design doc §8 - §9).

## Layout

```
docs/                       Pre-registered designs (EXPERIMENT-DESIGN.md, MLDSA-DESIGN.md)
harness/                    Measurement engine, leakage statistics, targets, CLI runner
results/                    Control validation + per-algorithm findings (reproducible)
```

## Running

```
mvn -q compile
java -cp "harness/target/classes:<bcprov-jdk18on-1.84.jar>" \
    org.pqcsidechannel.Runner --target=<name> --n=<measurements>
```

Targets: `positive-control`, `negative-control`, `mlkem-decap-valid`, `mlkem-decap-rejection`,
`mldsa-sign-message`, `mldsa-sign-fixed`, `mldsa-sign-keydep`, `slhdsa-sign-message`. JIT/GC
configuration is controlled by JVM flags (e.g. `-Xint`, `-XX:TieredStopAtLevel=1`,
`-XX:+UseEpsilonGC`) and recorded in every result.

## License

Apache-2.0 (see `LICENSE`).
