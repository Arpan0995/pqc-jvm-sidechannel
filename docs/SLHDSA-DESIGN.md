# Experimental Design - SLH-DSA signing timing

**Extends** the main design to the third NIST signature standard, FIPS 205 (SLH-DSA). Same pipeline,
same controls. This is the **expected-constant-time** case that completes the three-standard study and
frames ML-DSA's key-dependence as the interesting outlier.

## Why SLH-DSA should be constant-time

SLH-DSA is a **stateless hash-based** signature. Signing hashes the message to select indices into a
FORS instance and a hypertree, then computes a **fixed** number of hash evaluations - there is no
rejection sampling, no secret-dependent branch, and no data-dependent loop count. The control flow is
message- and key-independent, so signing time should not depend on the message or the secret key. This
is the opposite of ML-DSA (variable-time by rejection sampling) and the same expectation as ML-KEM
(constant-time by design).

## Research questions and hypotheses

- **RQ-S1 (message dependence).** With a fixed key, does signing time depend on the message?
  **H-S1: no** - CLEAN. Contrasts directly with ML-DSA's strong message-dependence.
- **RQ-S2 (key dependence, optional).** Does signing time depend on the secret key (two keys, random
  messages)? **H-S2: no** - CLEAN. Contrasts with ML-DSA's small-but-real key-dependence.

A CLEAN result on both, from a pipeline that detects the synthetic positive control (max|t| ≈ 23,000)
and ML-DSA's message-dependence (max|t| ≈ 300), is a meaningful confirmation, not a null for lack of
power.

## Design

Reuse the scattered-slot pool harness. Parameter set **`sha2_128f`** (the fast variant; the small
variants sign in hundreds of ms, which caps achievable sample sizes). Signing is ~milliseconds, so
sample sizes are smaller than for ML-KEM/ML-DSA (N on the order of 1e4); this is noted in results and
is why authoritative bounds want the pinned Linux host.

- **`slhdsa-sign-message`** (RQ-S1): fixed key, class 0 = fixed 32-byte message, class 1 = random
  messages. `compute` signs the slot's message. *Expect CLEAN.*

Signing determinism does not matter for the timing here as it does for ML-DSA: SLH-DSA performs a fixed
amount of work regardless of the signing randomizer, so timing is constant whether signing is
deterministic or hedged. Deterministic signing (plain private key) is used for reproducibility and is
verified by test.

## The three-standard contrast (the point)

| Standard | Mechanism | Expected timing |
|---|---|---|
| ML-KEM-768 | lattice KEM, constant-time by design | constant |
| ML-DSA-65 | lattice signature, rejection sampling | message-dependent; small key-dependence |
| SLH-DSA (sha2-128f) | hash-based, data-independent control flow | constant |

Measured by one validated detector across all three.
