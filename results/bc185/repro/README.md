# ML-DSA-65 signing timing: discrimination repro

Reproduces the decision in [`../discrimination_result.md`](../discrimination_result.md):
the key-dependent timing signal in Bouncy Castle ML-DSA-65 signing is fully
algorithmic (FIPS 204 rejection-sampling iteration count), with no residual
implementation channel. **Outcome A.**

## Layout

- `instrumentation/` — `MLDSATrace.java` (write-only per-signature work
  profiler) and `apply_patch.py` (adds the six trace anchors to a pristine
  BouncyCastle 1.85 `mldsa` source tree; asserts each anchor matches exactly
  once). The instrumentation records only counts; instrumented and stock
  builds produce byte-identical signatures.
- `harness_src/` — `GateCheck` (B1a/B1b), `WhichJar` (which jar loaded),
  `WorkProfile` (deterministic per-signature work profile, instrumented),
  `MicroTiming` (per-signature timing, STOCK jar only; aborts if the trace
  class is on the classpath).
- `analysis/` — `discriminate.py` (the pre-registered decision procedure) and
  `plots_c.py`.
- `data/*.csv.gz` — raw per-signature data (gunzip before use). `work_*` are
  the deterministic work profiles; `time_*` the timings. `discovery` = keys
  100-119 / corpus 999; `rerun` = same keys, shuffled allocation, fresh JVM
  (placement control); `replication` = fresh keys 200-219 / corpus 31337.
  `gate-1.8x.txt`, `b_*.out`, `discriminate_*.txt` are captured outputs.

Not committed (see `.gitignore`): `inst-src/` (extracted upstream BouncyCastle
source, under the Bouncy Castle licence — regenerate it locally), and the
`classes/` / `inst-classes/` build outputs.

## Reproduce

Needs JDK 21 and the pinned jars (`bcprov-jdk18on-1.84.jar`,
`-1.85.jar`, and `-1.85-sources.jar`) from Maven Central; verify their SHA-256
against `../discrimination_preregistration.md` / `../mldsa_1.84_vs_1.85_diff.md`.
Then: compile the harness against 1.85, extract and patch the `mldsa` sources
with `apply_patch.py`, compile the instrumented package, run the gates, and run
the campaigns (work profile instrumented; timing on the stock jar, one heavy
process at a time under `caffeinate`). Finally:

```
gunzip -k data/*.csv.gz
python3 analysis/discriminate.py --work data/work_discovery.csv --time data/time_discovery.csv --label discovery
```

The `§4` work-profile gate is deterministic in `(key, message)` and must
reproduce exactly (overall mean iterations 5.1340; per-key spread 1.75%) before
any timing is trusted.
