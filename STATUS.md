# STATUS — Study B follow-up: BC 1.85 re-run + ML-DSA-65 timing discrimination

**Living state file.** Read this first at the start of every session; update it at the end of every
session. Records resolved versions + checksums, host profile, seeds, corpus hashes, run-ids, gate
pass/fail, and the next action.

**Last updated:** 2026-07-16
**Current phase:** Part A complete; Part C gates complete; C-phase pre-registration written; awaiting
data collection.

---

## The decision this work must answer

Is the key→timing channel in ML-DSA-65 signing fully explained by ML-DSA's key-dependent
rejection-sampling work (**Outcome A — algorithmic**, do not propose an implementation fix), or is
there a residual left after work is held identical (**Outcome B — implementation residual**, engage
David's "drive it to zero" invitation with a localized target)?

Not yet answered. No C-phase data collected at time of writing.

---

## A1 — Pinned toolchain (all checksums verified)

| Artifact | Version | SHA-256 |
|---|---|---|
| `org.bouncycastle:bcprov-jdk18on` (baseline) | 1.84 | `64d6c5a6121fcd927152dd182cbed39afe0fda641a970d9bcc0c9cb1858b2731` |
| `org.bouncycastle:bcprov-jdk18on` (re-run) | 1.85 | `20af26bf6060bb8005cc2389916812c1e0e998dc48d2ced7131b89461b54cff7` |
| `bcprov-jdk18on-1.84-sources.jar` | 1.84 | `e5f04550f7740e588edcbd1654c59277cd7ee8725d8b674e44f7f8f4b9c5674a` |
| `bcprov-jdk18on-1.85-sources.jar` | 1.85 | `fa5a81c8b91f299712edcf917788f6293482240a46ec6d095ac6512ae257f007` |

- Both binary jars verified against Maven Central's published `.sha1`. 1.85 is the current
  `<latest>`/`<release>`.
- **No PQC module split in 1.85** — `org.bouncycastle.pqc.crypto.mldsa` still ships in `bcprov-jdk18on`
  (identical 20-entry class inventory). No extra artifact to pin.
- **JDK — exact 1.84-baseline build available and used**, so the JDK is *not* a changed variable:
  ```
  openjdk version "21.0.11" 2026-04-21
  OpenJDK Runtime Environment Homebrew (build 21.0.11)
  OpenJDK 64-Bit Server VM Homebrew (build 21.0.11, mixed mode, sharing)
  ```
  Path: `/opt/homebrew/Cellar/openjdk@21/21.0.11/libexec/openjdk.jdk/Contents/Home`.
  (Note: the machine's *default* `java` is 17.0.12 and Maven's is 26.0.1 — both are **not** used. All
  study runs invoke the pinned 21.0.11 explicitly.)
- **JMH is not used** in this study. The discrimination needs per-invocation times, not aggregates
  (brief §C2), and Part B's detector is the dudect-style single-shot harness. No JMH version to pin.

### One-variable-at-a-time enforcement

The harness is **compiled once** with the pinned JDK and the resulting bytecode is run against each
jar by swapping a single classpath entry. The harness bytecode is therefore *provably* identical
across the 1.84 and 1.85 runs, not merely "rebuilt the same way".

- Harness main-classes fingerprint (SHA-256 of sorted per-class SHA-256 list):
  `e2c246825e2764d3a91640459e5d3be5985624ff975f33036f7266266f889913`
- **Jar-identity control.** Classpath order is not trusted; each run proves which jar loaded. 1.85's
  `KeccakDigest` declares a private `queuePacked` field, 1.84's does not:
  ```
  run with 1.84 -> MLDSASigner from .../bcprov-jdk18on-1.84.jar ; queuePacked present: false
  run with 1.85 -> MLDSASigner from .../bcprov-jdk18on-1.85.jar ; queuePacked present: true
  ```

---

## A2 — ML-DSA path diff 1.84 → 1.85  ✅ COMPLETE

Full writeup: [`results/bc185/mldsa_1.84_vs_1.85_diff.md`](results/bc185/mldsa_1.84_vs_1.85_diff.md).

- **The ML-DSA signing path is byte-identical.** `MLDSAEngine` (whole rejection loop), `MLDSASigner`
  (the API we call), `MLDSAPrivateKeyParameters`, `MLDSAKeyPairGenerator`, `Poly`, `PolyVec*`, `Ntt`,
  `Packing`, `Reduce`, `Rounding`, `Symmetric` — all identical. David's expectation is confirmed, and
  more strongly than stated: not "not touched much", but *not touched at all*.
- Only 2 of 19 ML-DSA classes differ, both **off** the signing path: `HashMLDSASigner` (added
  external-hash API; we don't call it) and `MLDSAPublicKeyParameters` (added strict length validation
  on public-key *decode*).
- **The one material hot-path change: `KeccakDigest` lazy squeeze packing.** Reached from ML-DSA by
  inheritance (`SHAKEDigest extends KeccakDigest`), so it does *not* appear in the constant-pool
  closure and must be compared explicitly. `KeccakExtract()` no longer eagerly packs the full rate
  block; `squeeze()` materialises only the lanes consumed. Output-neutral (confirmed empirically —
  signatures byte-identical across versions); work saved is a function of requested output *length*
  (public), not of secret values.
- `Pack` differs only by an **added** `shortToLittleEndian(short[])` overload; the `longToLittleEndian`
  Keccak calls is unchanged. `CryptoServicesRegistrar`, `Arrays`, `SHAKEDigest` identical.

**Implication:** a persisting key-dependent signal on 1.85 **cannot** be attributed to a 1.85 ML-DSA
change — the mechanism is implemented by identical bytecode. If the *magnitude* moves, `KeccakDigest`
is the sole identified candidate.

---

## A3 — Host profile and host control  ⚠️ MATERIALLY WEAKER THAN THE BRIEF REQUIRES

| Property | Value |
|---|---|
| Machine | Apple Mac14,2 — **Apple M2** |
| Cores | 8 logical: **4 Performance + 4 Efficiency** (heterogeneous) |
| RAM | 8 GiB |
| OS | macOS 27.0 (build 26A5378n), arm64 |
| Kernel | Darwin 27.0.0 |

**Gate A verdict: PROCEED WITH REDUCED CLAIM STRENGTH.** The brief asks for an otherwise-idle,
frequency-pinned, core-isolated Linux/x86 or Graviton3 host. This host is none of those. Recorded
honestly rather than worked around:

| Control the brief asks for | Status here |
|---|---|
| `performance` governor / disable turbo | **Not possible** — macOS exposes no governor or turbo control |
| Pin to isolated physical core (`taskset`) | **Not possible** — `taskset`/`chrt`/`cpupower` are Linux-only; macOS offers no hard user-space CPU affinity |
| Disable SMT sibling | **N/A** — M2 cores are single-threaded (no SMT) |
| Otherwise-idle machine | **Violated** — this is an interactive desktop; the Claude app + WindowServer consume tens of % CPU |
| Fixed frequency / no DVFS | **Violated** — DVFS not disableable |
| Thermal logging | **Unavailable** without sudo (`powermetrics`); `pmset -g therm` reports nothing |
| Fixed JVM heap (`-Xms == -Xmx`) | **Enforced** in all runs |
| GC event recording | **Enforced** in all runs |

Additional M2-specific hazard, worse than the generic "unpinned host" caveat: **P/E-core
heterogeneity**. The scheduler may migrate the signing thread between Performance and Efficiency
cores, which differ substantially in throughput. There is no supported way to pin to a P-core.

**Session-specific state changes (recorded because they are timing-relevant):**
- **Low Power Mode was ON at session start and has been turned off** by the operator. LPM caps CPU
  frequency and would have depressed and destabilised all clocks.
- **Power source: battery (discharging, ~64%).** AC power is preferable; macOS DVFS behaviour differs
  on battery. **Open item.**
- Load average was 3.3 at session start; spiked to ~19.4 immediately after LPM was disabled as the
  system drained deferred background work (`modelcatalogd`, `BackgroundShortcutRunner`). Timing runs
  must wait for this to settle and must record load at run time.

### What this does and does not permit (inferential consequence)

This is not symmetric, and the asymmetry decides how the results may be read:

- **Host noise inflates within-cell variance; it does not bias the key contrast**, because the design
  interleaves keys over a shared message corpus in randomised order, decorrelating key from drift.
- Therefore **Outcome B (detecting a residual) is conservative here** — noise does not manufacture
  key-correlated differences under interleaving, so a residual that survives *and replicates* is
  credible in *sign/existence*, though its **magnitude is not authoritative**.
- Conversely **Outcome A (a null) is the risky direction on this host** — it may be a false negative
  from low power. A null must therefore be reported as an *upper bound* on the residual accompanied by
  a power statement, never as "the residual is zero".
- **The work-profile half of Part C is fully valid on this host**, because iteration counts and
  reject-stage traces are deterministic functions of (key, message) with no timing component. The
  algorithmic floor can be characterised *exactly* here. Only the timing half is host-limited.

**Required before any authoritative magnitude claim or any final Outcome B:** re-run on a pinned,
core-isolated, idle Linux/x86-64 or Graviton3 host. **Open item.**

---

## Seeds, corpus, and artifacts

- **Key seeds (long → `DeterministicSecureRandom` → ML-DSA-65 keygen).** The original six-key probe
  seeds are `100–105`; the dudect key-dependence pair is `0x0D5A65` (=875109, "key A") and `0x0D5A66`
  (=875110, "key B"). C-phase expands to seeds `100–119` (20 keys), keeping the original six for
  continuity. Replication set uses fresh, disjoint seeds (see pre-registration).
  - The mechanism is kept exactly as in the 1.84 run (a `long` seed, not a raw 32-byte ξ) precisely
    *because* changing it would change the keys and break comparability with the baseline. The 32-byte
    ξ each seed actually produces is recorded in the gate output.
- **Message corpus:** `L64X128MixRandom`, seed `999`, 32-byte messages — the same generator and seed as
  the original six-key probe. Corpus SHA-256 is recorded per run (it depends on the message count);
  e.g. the 200-message corpus is `07a9b747ed0b24570b28ca1aa569666f31cfb432f9febdd6dc028f8956d7e541`.
- **Instrumented artifact:** `1.85-instrumented` — the exact 1.85 ML-DSA sources plus write-only trace
  bookkeeping, compiled against the stock 1.85 jar and shadowing it on the classpath. **Local only;
  never published; never used for timing.** Timing always comes from the stock jar.
  - Only the `org.bouncycastle.pqc.crypto.mldsa` package is rebuilt. (bcprov is a *signed* jar: mixing
    unsigned classes into a package that also loads signed classes throws
    `SecurityException: signer information does not match`. Instrumenting a whole package avoids this.)

---

## Gate status

| Gate | Requirement | Status |
|---|---|---|
| **A** | A1–A3 recorded before measurement data | ✅ **PASS** (this file) — with host control formally downgraded, see A3 |
| **B1a** | ML-DSA-65 public key byte-identical 1.84 vs 1.85, per seed | ✅ **PASS** — all 8 seeds; SHA-256 match on pk *and* sk. Keys are the same keys; downstream comparisons valid |
| **B1b** | Same (key, message) signs byte-identically twice | ✅ **PASS** — `det=true` 8/8, `verify=true` 8/8; deterministic (rnd = 0) mode confirmed engaged |
| **C1a** | Instrumented build output byte-identical to stock | ✅ **PASS** — keys + signatures identical for all 8 seeds |
| **C1b** | Instrumented trace deterministic | ✅ **PASS** — 4000 (key, message) pairs re-signed, 0 trace/signature mismatches |
| — | Cross-version signature identity (bonus) | ✅ 8/8 — empirically confirms the Keccak lazy-pack change is output-neutral |
| **Detector controls on 1.85** | synthetic ± and real-crypto controls pass | ⏳ `[[PENDING: RUN-B-CTL]]` |
| **C0** | Decision rule pre-registered before C-phase analysis | ✅ **PASS** — `discrimination_preregistration.md` committed before any C-phase data |

---

## Run log

| run-id | What | Status |
|---|---|---|
| `RUN-A-DIFF` | 1.84↔1.85 artifact + source diff | ✅ complete — see `mldsa_1.84_vs_1.85_diff.md` |
| `RUN-A-GATE` | B1a/B1b/C1a/C1b gate battery | ✅ complete — all pass |
| `RUN-B-CTL` | Detector controls (synthetic ±, real-crypto) on the 1.85 setup | `[[PENDING]]` |
| `RUN-B-REPRO` | Part B battery on 1.85 (per-key means, 6-key probe, A/B swap, t-vs-N at 100k/250k) | `[[PENDING]]` |
| `RUN-C-WORK` | Work profile, 20 keys × N messages (instrumented; host-independent) | `[[PENDING]]` |
| `RUN-C-TIME` | Per-invocation timing, stock 1.85, same (key, message) grid | `[[PENDING]]` |
| `RUN-C-REP` | Confirmatory replication, fresh seeds + fresh corpus | `[[PENDING]]` |

**No measurement numbers are recorded anywhere in this repo yet for the 1.85 study.** Every unfilled
value is `[[PENDING]]`. Nothing is interpolated from the 1.84 run.

---

## Next action

1. Collect `RUN-C-WORK` (host-independent, valid now) — characterises the algorithmic floor exactly.
2. Wait for host load to settle; ideally move to AC power. Then `RUN-B-CTL` → `RUN-B-REPRO` →
   `RUN-C-TIME`.
3. Analyse per the pre-registration; replicate before any Outcome B claim.
4. Do not draft the reply to David until `discrimination_result.md` lands on A or B.
