# Pre-registration - ML-DSA-65 timing discrimination (algorithmic floor vs implementation residual)

**Frozen before any C-phase cross-key timing analysis.** This file fixes the strata, statistics,
thresholds, corrections, replication requirement, and the outcome→decision mapping *in advance*, so
the reply to David is determined by the data rather than chosen after seeing it. After data
collection begins, this file may only be appended to (with dated notes), never edited.

**Status at freeze:** Gates A, B1a, B1b, C1a, C1b all PASS. The ML-DSA signing path is byte-identical
1.84↔1.85 (`mldsa_1.84_vs_1.85_diff.md`). No cross-key timing distribution has been examined. The
descriptive facts used below (ML-DSA's expected iteration count; which loop operations early-exit)
come from the algorithm design and from reading byte-identical source - not from any key-comparison
result.

---

## 0. The question, made precise

ML-DSA signing runs a rejection loop. Each iteration performs a fixed structural sequence - ExpandMask,
NTT, matrix·vector, decompose, SampleInBall, three norm checks, a hint check - and either restarts (at
whichever check failed) or accepts. Two quantities determine "how much algorithmic work" a signature
did:

- **`iterations`** - total loop iterations (the coarse driver of signing time), and
- **the reject-stage multiset** - how many iterations exited at `‖z‖∞`, `‖r0‖∞`, `‖c·t0‖∞`, hint-weight,
  and the single accept. A "late reject" costs more than an "early reject", so the multiset, not just
  the count, pins the algorithmic cost.

Define the **work-profile key**:

```
profile_key = iterations : (rej_z, rej_r0, rej_ct0, rej_hint, accept)   # accept is always 1
```

Two signatures with the same `profile_key` executed the **same algorithmic branch structure**, and
therefore the same count of every expensive structural operation (SHAKE blocks for ExpandMask, NTTs,
pointwise products, decompose/makeHint passes). This is verified from source, not assumed - see §1.

**The discrimination question:** *after conditioning on `profile_key`, does mean signing time still
depend on the key?*
- **No** ⇒ the key→time channel is the algorithmic rejection floor (**Outcome A**).
- **Yes, and it replicates** ⇒ a residual that the algorithm does not require (**Outcome B**).

---

## 1. What can and cannot vary within a fixed `profile_key` (from source)

Within one `profile_key`, every loop operation runs a fixed number of times **except** two
value-dependent implementation quantities, both confirmed by reading the (byte-identical) 1.85 source
and both instrumented:

1. **`norm_scan_coeffs` - `Poly.checkNorm` early-exit.** `checkNorm` scans coefficients and
   `return true` on the first out-of-bound one. The number scanned depends on the *position* of the
   first violating coefficient, i.e. on coefficient values (`z = y + c·s1`, secret-dependent). This is
   an implementation choice: a constant-time norm check would scan all N (or accumulate branchlessly).
   **This is the leading a-priori candidate for an implementation residual, and it is pre-localised
   here.**
2. **`challenge_bytes` - SampleInBall (`Poly.challenge`).** Its `do … while (b > i)` loop consumes a
   variable number of SHAKE bytes. Its input is the commitment hash c̃ (message/commitment-driven), so
   a per-*key* dependence is not expected, but it is recorded and controlled.

Everything else in the loop body - `decompose`, `makeHint`, `power2round`, the NTTs, the pointwise
products, the packing - is **full-scan, fixed iteration count** (verified: `makeHint`/`decompose` loop
`0..N` with no early return; `Rounding` does fixed per-coefficient work). Per-coefficient
branch-on-value in `Rounding.decompose`/`makeHint` executes a fixed number of times within a fixed
profile; any timing it contributes is micro-architectural (branch prediction) and, if present in a
residual, is bounded and would itself be an implementation effect.

**Consequence for the design.** Stratifying by `profile_key` holds the algorithm fixed. A surviving
cross-key timing difference is then, by construction, attributable to (1), (2), or
micro-architectural value-dependence - all implementation-level. This is why the identical-work-profile
test is decisive, and why `norm_scan_coeffs` is pre-registered as the localization variable (Part D is
effectively pre-loaded).

---

## 2. Data (fixed in advance)

- **Keys:** 20 seed-pinned ML-DSA-65 keys, seeds `100..119` (the original six-key probe seeds `100..105`
  retained for continuity). Discovery set.
- **Replication keys:** 20 fresh disjoint seeds `200..219`. Used only for §6.
- **Messages:** fixed corpus from `L64X128MixRandom` (same generator as the 1.84 probe). Discovery
  corpus seed `999`; replication corpus seed `31337`. Corpus SHA-256 recorded per run. 32-byte messages,
  identical set signed by every key, interleaved (paired design).
- **Message count:** chosen so the modal `profile_key` cells clear the minimum in §3 (target ≈ 60k
  messages × 20 keys for discovery; adjust upward only if cells underfill, recorded as an append).
- **Timing:** stock 1.85 jar, per-invocation `System.nanoTime()`, scattered-slot engine, same
  confound hardening and percentile crop rule as the 1.84 harness. **JMH not used** (per-invocation,
  not aggregate).
- **Work profile:** `1.85-instrumented` build on the identical (key, message) grid. Joined to timing on
  `(key_id, message_id)`.
- **Join schema (one row per signature):**
  `key_id, key_seed, message_id, iterations, stage_seq, profile_key, rej_z, rej_r0, rej_ct0, rej_hint,
  norm_scan_coeffs, norm_scan_calls, challenge_bytes, time_ns, run_id`.

---

## 3. Strata and minimum cell size (fixed in advance)

- **Primary strata = exact `profile_key`.** Analyse every `profile_key` for which **at least 12 of the
  20 keys** each have **≥ 800 signatures** in that cell. (These are the "well-populated" strata.) Cells
  below this are reported as **underpowered** and are **not** used to support either outcome.
- **Coarse strata = exact `iterations`** (collapsing the stage multiset) for the Step-1 ANCOVA and as a
  fallback when few `profile_key` cells are well-populated.
- The modal iteration count and its immediate neighbours are expected to dominate (ML-DSA-65's expected
  repetition count is ≈ 4-5; the accept-on-iteration distribution is geometric-ish). Exact populations
  are reported after collection; strata are defined by the rule above, not by hand-picking.

---

## 4. Test statistics (fixed in advance)

Per key-pair × stratum, on per-invocation `time_ns` (after the same percentile crop as 1.84):

1. **Welch's t** (unequal variance). TVLA convention.
2. **Mann - Whitney U** → z (non-parametric, tail-robust).
3. **Cliff's delta** δ (non-parametric effect size).
4. **Bootstrap 95% CI** on the per-key mean-time gap (10,000 resamples), for magnitude.

Coarse control (Step 1), across the whole dataset:

5. **ANCOVA / OLS:** `time_ns ~ iterations + C(key)` (and a richer `time_ns ~ C(profile_key) + C(key)`).
   Report the partial effect and Type-II significance of the `key` term **after** controlling for work,
   plus the partial η²/variance-fraction the key term explains.

Localization (pre-registered secondary, Step 2b):

6. Within each well-populated `profile_key`, regress `time_ns ~ norm_scan_coeffs + C(key)` and report
   whether `norm_scan_coeffs` absorbs the key term. This tests the pre-localised candidate directly.

---

## 5. Decision thresholds and multiple-comparison correction (fixed in advance)

A cross-key difference **within a stratum** counts as **present** only if **both**:
- **|t| > 4.5** (TVLA), *and*
- **|Cliff's δ| ≥ 0.147** (the conventional small-effect floor),

**after Holm - Bonferroni correction across the full family of (key-pair × stratum) tests.** The Welch
t-threshold is applied to the Holm-adjusted significance; δ ≥ 0.147 must hold on the raw estimate.
Requiring a non-negligible effect size guards against large-N t-inflation flagging an operationally
meaningless difference.

The **ANCOVA `key` term** counts as **present** only if it is significant after correction **and**
explains a non-negligible variance fraction once `iterations`/`profile_key` are in the model (report
the fraction; ≥ 1% of residual variance is the pre-set "non-negligible" bar, flagged if borderline).

Statistical significance without δ ≥ 0.147 (or without ≥ 1% variance) is recorded as **"resolvable but
negligible"** and does **not** trigger Outcome B.

---

## 6. Replication requirement (mandatory before any Outcome B)

No implementation-residual claim is made without a confirmatory replication on the **fresh** keys
(`200..219`) and **fresh** corpus (seed `31337`). To confirm, the residual must, within the same
well-populated-stratum rule:
- have the **same sign** (same direction of the per-key gap where key identity is comparable via the
  `norm_scan_coeffs` mechanism, not via seed label), and
- **pass the §5 thresholds again**, and
- be of **comparable magnitude** (replication mean gap within the discovery bootstrap CI, order of
  magnitude at least).

A residual that does not replicate is **noise** → Outcome A.

---

## 7. Outcome → decision mapping (fixed in advance - this is the reply to David)

Follow the decision tree exactly; stop at the first terminal node reached.

**Step 1 - coarse control (iterations).**
- Confirm `time_ns` rises monotonically with `iterations` (validates iterations as the dominant
  driver). If it does not, halt and debug the join - do not interpret.
- If the ANCOVA `key` term is **absent** (§5) ⇒ **OUTCOME A (ALGORITHMIC)**. Terminal.
- If **present** ⇒ ambiguous (could be reject-stage mix, still algorithmic). Go to Step 2.

**Step 2 - fine control (identical `profile_key`).**
- If, in every well-populated stratum, no key-pair difference is **present** (§5) ⇒ **OUTCOME A
  (ALGORITHMIC).** The Step-1 signal was reject-stage mix. Terminal.
- If a difference **is present** in one or more well-populated strata ⇒ candidate residual. Run Step 2b
  and Step 3.

**Step 2b - localization (pre-registered).**
- If `norm_scan_coeffs` absorbs the surviving key term within strata ⇒ the residual is the
  **`checkNorm` early-exit**; carry this into Part D/`mitigation_candidate.md`.
- If it does not ⇒ residual is elsewhere; Part D micro-timing enumerates sub-operations without a
  pre-committed location.

**Step 3 - replication (§6).**
- Passes ⇒ **OUTCOME B (IMPLEMENTATION RESIDUAL).** Engage David; report magnitude + localization.
- Fails ⇒ **OUTCOME A**, reported as: signal seen in discovery did not replicate, treated as noise.

### The two replies, written in advance

- **Outcome A - do not propose an implementation fix.** "The key-dependent signal is fully accounted
  for by ML-DSA's rejection-sampling work: once the number of iterations and the reject-stage mix are
  held identical, no key-dependent timing difference survives (|t| and δ below threshold, or absent
  ANCOVA key term; statistics cited). This is the FIPS 204 rejection-count variance, key-dependent by
  construction, not a BC-specific defect. It persists on 1.85 (so it is not a stale-build artifact),
  which is expected since the ML-DSA path is byte-identical to 1.84. We characterised its magnitude and
  bound the residual at ≤ [[PENDING]] (upper bound, given host limits)."
- **Outcome B - engage.** "A key-dependent timing difference survives identical-work-profile control
  and replicates on fresh seeds (magnitude [[PENDING]], |t|=[[PENDING]], δ=[[PENDING]]). It localises to
  [[PENDING - checkNorm early-exit unless data says otherwise]]. Concrete constant-time direction in
  `mitigation_candidate.md`, with an explicit note on whether zero is reachable given the algorithmic
  floor beneath it."

---

## 8. Host caveat binding on the conclusion (see STATUS.md §A3)

Data is collected on an unpinned macOS/arm64 M2 desktop (host control materially weaker than the
brief's Linux/x86 or Graviton3 target). This is asymmetric and is fixed into the interpretation here so
it cannot be renegotiated post hoc:

- Under key-interleaving over a shared corpus, host noise **inflates within-cell variance** but does
  **not** manufacture key-correlated differences. So **Outcome B is conservative** (existence/sign
  credible; **magnitude not authoritative** - always stated as such).
- **Outcome A is the false-negative-prone direction.** A null is reported as an **upper bound** on the
  residual with the achieved power, never as "zero", and calls for a pinned-host confirmation before
  being treated as final.
- The **work-profile characterisation is host-independent** and fully valid here (deterministic in
  (key, message)).

---

*Frozen 2026-07-16, before RUN-C-TIME / RUN-C-WORK cross-key analysis. Appends only below this line.*
