# Experimental Design — ML-DSA-65 signing timing

**Extends** the main design (`EXPERIMENT-DESIGN.md`) to the signature standard. Same pipeline, same
controls, but a genuinely different question — and the one most likely to yield a *positive* finding.

## Why ML-DSA is different from ML-KEM

ML-KEM decapsulation is designed to be constant-time, so its null result is expected. **ML-DSA
signing is variable-time by construction:** FIPS 204 signing uses rejection sampling — it samples a
candidate signature, checks norm bounds, and *retries* until one passes. The number of iterations,
and therefore the signing time, depends on the secret key and on the per-signature randomness. This
is the single most likely place in the NIST suite to exhibit measurable, secret-correlated timing.

The scientific care this demands is exactly what makes it publishable: a naive fixed-vs-random test
would "detect leakage" that is simply the expected rejection-sampling variance, not a novel finding.
The design must separate the sources of timing variance and isolate what is actually secret-dependent.

## Controlling randomness — the key lever

BouncyCastle's `MLDSASigner.init(true, privateKey)` with a plain private key performs **deterministic**
signing (FIPS 204's rnd = 0 variant): the signature, and its iteration count, are a deterministic
function of (key, message). Passing `ParametersWithRandom(privateKey, random)` performs **hedged**
(randomized) signing. Having both lets us attribute timing variance to its sources.

## Research questions

- **RQ-D1 (message dependence).** With a fixed key and deterministic signing, does signing time depend
  on the message? (Expected: yes — different messages hash to different μ and take different iteration
  counts. The finding is quantifying it and confirming it is observable through JVM noise.)
- **RQ-D2 (intrinsic constant-time baseline).** With a fixed key, deterministic signing, and a fixed
  message, is signing time constant? (Expected: yes — this is a real-cryptography negative control that
  proves the variance in RQ-D1 is input-driven, not an artifact of ML-DSA's machinery or the harness.)
- **RQ-D3 (randomness contribution, future).** Does hedged signing add timing variance beyond the
  deterministic baseline for the same message?
- **RQ-D4 (key dependence / exploitability).** Does signing time depend on the secret *key*? This is
  the actual exploitability question — message-dependence (RQ-D1) leaks a public value, whereas
  key-dependence would leak the secret. **Design:** two independently generated fixed keys A (class 0)
  and B (class 1), both signing *random* messages under deterministic signing. Both classes draw from
  the same random-message distribution, so the message contribution is a common nuisance that averages
  out and the only systematic difference between classes is the key. A LEAKY verdict means the two
  keys have distinguishable signing-time distributions (a key-dependent channel); a CLEAN verdict
  bounds key-dependence — the reassuring outcome, and the *expected* one, since ML-DSA's rejection rate
  is designed to be essentially key-independent (expected iteration count is set by the parameters, not
  the specific key). Either way it is a real result, and it sharpens the RQ-D1 finding: is the
  variable timing driven by the public message or by the secret key?

  *Caveat:* the two keys live at different addresses, so an incidental memory-placement effect could
  in principle contribute; if a signal appears, re-run with the A/B seeds swapped — a real key-effect's
  sign behavior should track the keys, not the class label.

## Hypotheses

- **H-D1.** The message-dependence target (fixed vs random messages, deterministic) is flagged LEAKY
  with a large effect size — ML-DSA signing time is input-dependent on the JVM.
- **H-D2.** The fixed-message deterministic target is CLEAN — ML-DSA signing is constant-time when the
  input is held fixed, so the pipeline is not merely reacting to ML-DSA being "complicated."
- Together these give the paper its contrast: **ML-KEM constant-time, ML-DSA input-dependent**, both
  measured by the same validated detector.

## Design

Reuse the scattered-slot pool harness. The slot holds the message bytes (fixed 32-byte messages).

- **`mldsa-sign-message`** (RQ-D1): fixed key, deterministic signing. Class 0 = one fixed message;
  class 1 = random messages. `compute` signs the slot's message and returns the signature. *Expect
  LEAKY.*
- **`mldsa-sign-fixed`** (RQ-D2): fixed key, deterministic signing, **both classes sign the same fixed
  message**. *Expect CLEAN* — a real-cryptography negative control.

The timed operation is one signing call (message update + `generateSignature`) on a signer whose key
is expanded once at setup, so the measured variance is the signing work, not key setup.

## What "leaky" means here (interpretation)

For ML-DSA, a LEAKY verdict on RQ-D1 is the *expected, correct* result, not a vulnerability report by
itself: the message is public. Its value is (a) validating the detector against a known variable-time
primitive, (b) quantifying the magnitude on the JVM, and (c) setting up RQ-D4, where key-dependence is
the actual exploitability question. This framing is stated plainly so a reviewer sees we are not
over-claiming a "break."

## Reproducibility / environment

Same as the main design: pinned OpenJDK 21, BouncyCastle 1.84, deterministic seeds recorded, Apple
Silicon exploratory only, authoritative runs on a pinned Linux/x86 host. ML-DSA-65 signature length is
3309 bytes; messages are 32 bytes.
