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

## Addendum 2 (2026-08-22): the two-key dudect "leak" is the harness, not the key

The original signal was the Part-B `mldsa-sign-keydep` test (key A = seed
0x0D5A65 vs key B = 0x0D5A66, random messages, deterministic). At n=250k it
reports LEAKY: TVLA Welch t=-4.884, mean0=338,183 ns vs mean1=342,888 ns
(1.4%), Cohen's d=-0.0196 (`b_keydep_185_250k.out`). But the two keys'
iteration distributions are identical: `work_keydep_pair.csv.gz` (WorkProfile,
2 keys x 60,000 msgs) gives mean iterations 5.1461 vs 5.1457 (SE 0.019 each).
So the 1.4% is NOT iteration sampling. Controls (`harness_src/KeyDepControl.java`,
same pipeline as Runner with chosen seeds, n=250k each):

| run | class0 / class1 | TVLA t | Mann-Whitney z | dudect max|t| | Cohen d | class diff | verdict |
|---|---|---|---|---|---|---|---|
| A vs A (same key, 2 instances) | 874085 / 874085 | +4.261 | +14.10 | 22.58 | 0.0170 | 279,960 vs 276,810 ns (1.1%) | LEAKY |
| B vs A (swapped order)          | 874086 / 874085 | +1.452 |  +4.45 | 11.60 | 0.0058 | 377,278 vs 375,640 ns (0.4%) | MARGINAL |
| A vs B (repeat)                 | 874085 / 874086 | +1.800 |  -4.96 | 14.36 | 0.0072 | 368,967 vs 367,101 ns (0.5%) | LEAKY (dudect) |

(`b_keydep_ctl_AvsA_185.out`, `b_keydep_ctl_BvsA_185.out`, `b_keydep_ctl_AvsB_185.out`.)

Reading: with the SAME key in both classes the harness reports leakage as
confidently as with two different keys; the class difference is 0.4-1.1% with
a sign that changes from run to run. It is a run-specific class asymmetry of
the two-instance harness (instance placement / JIT on the JVM), amplified into
a verdict by n=250k per class and dudect's max-over-crops statistic. All effect
sizes are far below the pre-registered 0.147 floor. Absolute levels vary widely
across runs (280-377 us): within-run class comparison is what each verdict
uses. CONCLUSION: the apparent two-key signal is not about the key; consistent
with, and explained by, the 20-key within-profile null (Outcome A) and the
placement-control behaviour. A same-key negative control is mandatory before
reading any two-key first-order verdict on a managed runtime.

## Addendum 3 (2026-08-24): rank persistence and noise floor of the pure-stratum residual

Added after the decision tree resolved; exploratory. Script: `repro/analysis/rank_persistence.py` (run from `repro/data/`; output `repro/data/rank_persistence.txt`, `rank_persistence.json`). Same statistic as Table 1's pure-stratum row (per-key mean of own 98th-percentile-cropped times).

```
pure stratum 1:0-0-0-0-1: 234014 signatures per campaign, 20 keys; per-key crop at own 98% percentile (numpy.quantile)
per-key spread: discovery 448 ns (0.351%), placement control 283 ns (0.247%)
per-key SE of the cropped mean: 77 ns; expected span of 20 pure-noise means (3.735 SE): 289 ns
rank persistence across campaigns (n=20): Spearman rho=+0.489, Kendall tau=+0.337  (two-sided 5% critical |rho|~0.447, |tau|~0.33)
rank reproducibility within discovery: even/odd messages rho=+0.159, first/second half rho=+0.215; within placement control even/odd rho=+0.598
one-way ANOVA of cropped pure-stratum time by key: discovery F(19,229305)=2.71, eta^2=0.0225%; placement F=4.32, eta^2=0.0358%
SampleInBall challenge bytes: per-key spread 0.09 bytes; slope +16.9 ns/byte -> 1.6 ns attributable; Spearman(bytes,time) discovery +0.155

per-key cropped mean (ns): key  discovery  placement  rankD rankR
   0     127763     114757   14    7
   1     127581     114661    4    1
   2     127562     114778    3    9
   3     127704     114697   13    3
   4     127922     114826   19   15
   5     127820     114865   16   17
   6     127890     114807   18   13
   7     127614     114796    6   12
   8     127696     114695   12    2
   9     127655     114850   10   16
  10     127652     114705    8    4
  11     127666     114789   11   10
  12     127545     114597    2    0
  13     127528     114817    1   14
  14     127643     114748    7    6
  15     127654     114793    9   11
  16     127801     114866   15   18
  17     127880     114881   17   19
  18     127475     114758    0    8
  19     127582     114732    5    5
```

Reading: the placement-control spread (283 ns) equals the expected span of twenty pure-noise means (3.735 x 77 ns = 289 ns); discovery (448 ns) sits 1.5x above it. Each run carries small key-locked offsets beyond sampling noise (ANOVA F = 2.7 / 4.3, eta^2 <= 0.04%), as a signer instance parked in one place for a run would produce. Across re-allocation the per-key ranking reproduces only marginally (rho = 0.49, tau = 0.34, at the 5% edge for n = 20) and within the discovery run it barely reproduces (split-half rho 0.16-0.22). SampleInBall bytes account for 1.6 ns. The residual behaves as run-specific placement/drift at the level of a few standard errors; any key-linked component is bounded at a small fraction of 0.35% and is not excluded at the ~0.1% scale. No pre-registered threshold is approached.
