# ML-DSA-65 signing timing: discrimination result

**Decision: Outcome A.** The key-dependent timing signal in Bouncy Castle
ML-DSA-65 signing is **fully algorithmic** (the FIPS 204 rejection-sampling
iteration count, which is key- and message-dependent by construction). There is
**no residual implementation-level channel** once the work performed is held
identical. Bouncy Castle's ML-DSA-65 signing comes out clean; no fix is
proposed.

Reached at **Step 1** of the pre-registered decision tree (ANCOVA key term
absent), and confirmed independently by the placement control and the
replication campaign. This settles the open question left by the exploratory
MacBook Air pass: on the steadier Mac mini the residual **shrinks** rather than
resolving into a stable channel.

## Environment

- Host: Apple M4 (Mac16,10), macOS 27.0, actively cooled, on AC.
- JDK: OpenJDK 21.0.9 (Microsoft) — a minor substitution for the pinned
  21.0.11; recorded per handoff. Harness compiled once against Bouncy
  Castle 1.85 and run against the stock jar.
- Bouncy Castle 1.85 (sha256 20af26bf…), 1.84 (64d6c5a6…) for gates; both
  verified against Maven Central. Signing path byte-identical 1.84↔1.85.
- Each timing run was the only heavy process, under `caffeinate`.

## Gates (all pass)

- **B1a** ML-DSA-65 public keys byte-identical 1.84↔1.85 for all seeds;
  deterministic signing engaged (det=true, verify=true).
- **C1b** trace determinism: 4000 (key,message) pairs re-signed, 0 mismatches.
- **§4 work-profile validation gate reproduces exactly** (deterministic in
  (key,message)): 1{,}200{,}000 signatures, overall mean iterations
  **5.1340** (expected 5.134); per-key mean spread **5.095–5.184 = 1.75%**;
  reject stages z **47.3%** / r0 **52.6%** / c·t0 **0.00%** / hint **0.09%**;
  accept-iter-1 stratum `norm_scan_coeffs` constant **4352** (n=234{,}014).
  Discovery corpus sha256 `34ed1311…` matches.

## Statistics (three campaigns, 20 keys × 60{,}000 messages each)

| Campaign | ANCOVA key partial F | key extra variance | cross-key tests "present" | max \|Cliff δ\| | pure-stratum per-key spread |
|---|---|---|---|---|---|
| Discovery (keys 100–119, corpus 999) | F(19, 1.2M)=0.64 | **0.0002%** | **0 / 2986** | 0.099 | 448 ns (0.351%) |
| Placement control (same keys, alloc shuffled, fresh JVM) | F=2.04 | **0.0001%** | **0 / 2986** | 0.093 | 283 ns (0.247%) |
| Replication (fresh keys 200–219, corpus 31337) | F=0.63 | **0.0002%** | **0 / 2916** | 0.084 | 171 ns (0.150%) |

Pre-registered "present" bar: Holm-adjusted p<0.05 **and** |t|>4.5 **and**
|Cliff's δ|≥0.147. Pre-registered ANCOVA bar: key term counts only if it
explains ≥1% of residual variance after controlling for iterations.

- Time is explained by iteration count: Pearson r(iterations, time)=0.921,
  medians monotone, slope ≈ 52{,}235 ns/iter. The 1.75% per-key iteration
  spread mechanically produces the ~1–1.5% time spread with **zero** timing
  component.
- The key ANCOVA term is 4–5 orders of magnitude below the 1% bar in every
  campaign. **Step 1 terminal → Outcome A.**
- In the pure stratum `1:0-0-0-0-1` (accept on iteration 1, `norm_scan`
  constant so the `checkNorm` early-exit contributes nothing), the per-key
  spread is 0.15–0.35% and shrinks on the steadier host and with fresh seeds —
  a placement/drift artifact, not a value channel. No cross-key test survives
  the thresholds in any campaign.
- The one genuine value-dependent branch, `Poly.checkNorm`'s early exit,
  contributes a per-key timing spread of ≈ **±1–4 ns**, roughly 1000× below
  the per-iteration cost and not exploitable. A branchless norm check remains
  the only implementation-level "drive to zero" available, as defense in depth.

## Timing-rig controls (Part B)

- Positive control (early-exit compare): max|t|=**7745** → LEAKY (rig detects
  a real channel).
- Negative control (constant-time compare): max|t|=**0.78** → CLEAN (no
  false positive).
- ML-DSA fixed-vs-random message (deterministic): max|t|=**201** → LEAKY —
  the algorithmic message/iteration channel, expected.
- ML-DSA fixed message (deterministic): max|t|=**1.40** → CLEAN — same
  message, same iterations, constant time; confirms no residual channel.

## Bottom line

The key→time relationship in ML-DSA-65 signing is fully accounted for by the
rejection-sampling iteration count. Holding the work identical leaves no
implementation channel above the noise floor. This is the responsible-
disclosure conclusion communicated to Bouncy Castle: alert but not alarmed, and
correct.

## Addendum (2026-08-22): is the per-key iteration spread itself a key effect?

Added after the pre-registered tree resolved (exploratory). ML-DSA's rejection
sampling is designed so the acceptance probability of the z-check is
independent of the secret (y is uniform on a range exactly beta wider than the
acceptance window, and ||c s1||_inf <= beta always), and the r0-check is argued
independent heuristically. Under that design property, per-key mean iteration
counts over a finite corpus differ only by sampling noise. Tested directly with
`repro/analysis/iteration_anova.sh` (one-way ANOVA, iterations ~ key):

| Campaign | keys x msgs | grand mean | per-key spread | spread in SE | F(19, 1,199,980) | p | eta^2 |
|---|---|---|---|---|---|---|---|
| discovery   | 20 x 60,000 | 5.1340 | 5.0948..5.1840 (1.74%) | 4.73 | 1.203 | 0.244 | 0.0019% |
| replication | 20 x 60,000 | 5.1420 | 5.1090..5.1753 (1.29%) | 3.52 | 1.144 | 0.298 | 0.0018% |

5% critical F = 1.587; expected max-min of 20 noise means ~ 3.7 SE
(SE of a per-key mean = 4.61/sqrt(60000) = 0.019). **Not significant in either
campaign**: the per-key iteration spread is consistent with all keys sharing
one iteration distribution, i.e. the design property observed empirically at
1.2M-signature scale. The measured grand mean 5.134 matches the design's
expected repetition count (~5.1) for ML-DSA-65.

Consequence for interpretation: the ~1.35% per-key mean signing-time spread
(345,211..349,898 ns; corr(per-key mean iterations, per-key mean time) = 0.958)
is the per-key SAMPLE of a key-independent iteration count multiplied by the
per-iteration cost. Combined with Outcome A (no residual after conditioning),
there is no key-dependent timing channel of ANY kind: neither algorithmic nor
implementation. This sharpens, and does not change, the Outcome A decision.
