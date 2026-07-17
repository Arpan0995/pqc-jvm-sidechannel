# ML-DSA signing path: BouncyCastle 1.84 → 1.85 source diff

**Purpose (Part A2).** David's reply raised two things worth settling from source before any timing is
re-measured: that he *"[doesn't] recall this touching ML-DSA much"*, and that *"it's always possible
what you are seeing is due to something else in the API."* This document answers both from the
released artifacts, and it is evidence for the thread regardless of what the timing runs show.

**Headline.** Both halves of David's message are correct, and they resolve in different directions:

1. **ML-DSA itself was not touched.** Every class on the ML-DSA-65 signing path is **byte-identical**
   between 1.84 and 1.85 — including `MLDSAEngine` (which contains the entire rejection loop),
   `MLDSASigner` (the API this study calls), and the whole polynomial/NTT/packing layer.
2. **But "something else in the API" did change, and it is on the hot path.** `KeccakDigest` — reached
   from ML-DSA by inheritance (`SHAKEDigest extends KeccakDigest`) — gained a **lazy squeeze-packing**
   optimisation. SHAKE dominates ML-DSA signing cost, so this is the one 1.85 change that can move
   ML-DSA signing *time*. It is output-neutral by construction, and confirmed so empirically below.

The practical consequence: **1.85 cannot change the key→timing mechanism** (the code implementing it is
the same bytes), but it **can** shift absolute signing times. Any magnitude change observed in Part B
should be read against the Keccak change, not against an ML-DSA change.

---

## 1. Artifacts compared

| Artifact | SHA-256 |
|---|---|
| `bcprov-jdk18on-1.84.jar` | `64d6c5a6121fcd927152dd182cbed39afe0fda641a970d9bcc0c9cb1858b2731` |
| `bcprov-jdk18on-1.85.jar` | `20af26bf6060bb8005cc2389916812c1e0e998dc48d2ced7131b89461b54cff7` |
| `bcprov-jdk18on-1.84-sources.jar` | `e5f04550f7740e588edcbd1654c59277cd7ee8725d8b674e44f7f8f4b9c5674a` |
| `bcprov-jdk18on-1.85-sources.jar` | `fa5a81c8b91f299712edcf917788f6293482240a46ec6d095ac6512ae257f007` |

All four fetched from Maven Central (`repo1.maven.org`); both binary jars verified against Central's
published `.sha1`. `1.85` is the current `<latest>`/`<release>` for `org.bouncycastle:bcprov-jdk18on`.

**Module check.** BC did **not** split PQC into a separate module in 1.85. `org.bouncycastle.pqc.crypto.mldsa`
still ships inside `bcprov-jdk18on`, with an **identical class inventory** (20 entries, same names) in
both versions. No additional artifact needs pinning.

**Method.** Two independent comparisons, which agree:
- *Bytecode*: SHA-256 of every extracted `.class`, plus `javap -c -p` disassembly for anything differing.
- *Source*: `diff` of the `-sources.jar` contents.

---

## 2. ML-DSA package: per-class result

`org/bouncycastle/pqc/crypto/mldsa/*` — bytecode SHA-256 comparison:

| Class | Status | On the signing path we call? |
|---|---|---|
| **`MLDSAEngine`** (rejection loop, ExpandMask, norm checks, hints) | **IDENTICAL** | yes — the core |
| **`MLDSASigner`** (the low-level API this study calls) | **IDENTICAL** | yes |
| **`MLDSAPrivateKeyParameters`** (secret-key decode) | **IDENTICAL** | yes |
| **`MLDSAKeyPairGenerator`** / `MLDSAKeyGenerationParameters` | **IDENTICAL** | yes (keygen) |
| `Poly`, `PolyVecK`, `PolyVecL`, `PolyVecMatrix` | **IDENTICAL** | yes |
| `Ntt`, `Reduce`, `Rounding`, `Packing` | **IDENTICAL** | yes |
| `Symmetric`, `Symmetric$ShakeSymmetric` | **IDENTICAL** | yes |
| `MLDSAKeyParameters`, `MLDSAParameters` | **IDENTICAL** | yes |
| `HashMLDSASigner` | **DIFFERS** (4286 B → 5271 B) | **no** — pre-hash/HashML-DSA variant |
| `MLDSAPublicKeyParameters` | **DIFFERS** (1265 B → 1481 B) | **no** — decode-side validation |

**17 of 19 classes are byte-identical, and the two that differ are both off the secret-dependent
signing path.** Nothing in the rejection loop, the norm/hint checks, the secret-key decode, or the
NTT/polynomial arithmetic changed by a single byte.

### 2.1 `MLDSAPublicKeyParameters` — added length validation (decode-side)

The public API surface is unchanged (`javap -p` identical); only a constructor body changed:

```diff
 public MLDSAPublicKeyParameters(MLDSAParameters params, byte[] encoding)
 {
     super(false, params);
-    this.rho = Arrays.copyOfRange(encoding, 0, MLDSAEngine.SeedBytes);
-    this.t1 = Arrays.copyOfRange(encoding, MLDSAEngine.SeedBytes, encoding.length);
-    if (t1.length == 0)
+
+    MLDSAEngine engine = params.getEngine(null);
+    if (encoding.length != engine.getCryptoPublicKeyBytes())
     {
-        throw new IllegalArgumentException("encoding too short");
+        throw new IllegalArgumentException("'encoding' has invalid length");
     }
+
+    this.rho = Arrays.copyOfRange(encoding, 0, MLDSAEngine.SeedBytes);
+    this.t1 = Arrays.copyOfRange(encoding, MLDSAEngine.SeedBytes, encoding.length);
 }
```

A strict exact-length check replaces a weak non-empty check — recognisably review-driven input
hardening (consistent with the Mythos review David mentions). It runs only when **decoding a public key
from bytes**, never during signing, and it does not change any key *value*.

### 2.2 `HashMLDSASigner` — new external-hash API surface

1.85 **adds** methods (nothing removed): `generateSignature(byte[] hash)`, `verifySignature(byte[] hash,
byte[] signature)`, `checkHashLength`, `generateSignatureFromMsgDigest`, `buildExternalMsgDigest`; plus
an exception-wrapping tidy (`Exceptions.illegalStateException`). This extends the **pre-hash
(HashML-DSA)** variant to accept an externally computed hash.

**This study does not call `HashMLDSASigner`.** It calls `MLDSASigner` (pure ML-DSA), which is
byte-identical. So this change cannot affect our measurements.

---

## 3. Dependency closure — where "something else in the API" actually lives

Byte-identical ML-DSA classes are only conclusive if what they *call* is also unchanged. The 1-hop
reference closure of the `mldsa` package (extracted from its constant pools) is 14 BC classes:

| Dependency | Status |
|---|---|
| `crypto/digests/SHAKEDigest` | IDENTICAL |
| `crypto/digests/SHA512Digest` | IDENTICAL |
| `crypto/AsymmetricCipherKeyPair`, `CipherParameters`, `Digest`, `DataLengthException`, `KeyGenerationParameters` | IDENTICAL |
| `crypto/params/AsymmetricKeyParameter`, `ParametersWithContext`, `ParametersWithRandom` | IDENTICAL |
| `pqc/crypto/DigestUtils` | IDENTICAL |
| `util/Arrays` | IDENTICAL |
| `asn1/ASN1ObjectIdentifier` | DIFFERS — not on the signing hot path |
| `util/Exceptions` | DIFFERS — not on the signing hot path |

**The constant-pool closure is not sufficient**, because `SHAKEDigest` reaches its superclass by
*inheritance*, which does not appear as a constant-pool reference:

```
MLDSAEngine / Symmetric$ShakeSymmetric  →  SHAKEDigest (IDENTICAL)  →  extends KeccakDigest (DIFFERS)
```

`KeccakDigest` is therefore **on the ML-DSA signing hot path**, and it changed. This is the finding
that matters, and it is precisely the case David flagged.

### 3.1 `KeccakDigest` — lazy squeeze packing (the one hot-path change)

1.85 adds a private field `queuePacked` and a private method `ensureQueuePacked(int)`. The semantic
change:

- **1.84 (eager):** `KeccakExtract()` permutes, then immediately packs the **entire** rate block from
  `state` into `dataQueue` (`Pack.longToLittleEndian(state, 0, rate >>> 6, dataQueue, 0)`).
- **1.85 (lazy):** `KeccakExtract()` permutes and defers packing (`this.queuePacked = 0`). `squeeze()`
  then calls `ensureQueuePacked(srcOff + nBytes)` to materialise **only the lanes it actually consumes**,
  picking up where the previous call left off.

BC's own comment states the intent — consumers that squeeze less than a full rate block "avoid packing
the discarded tail of the block", naming SHA3-256, SLH-DSA tweakable hashes, and small cSHAKE/KMAC
outputs. `getEncodedState` force-materialises before serialising so saved digests round-trip.

**Why this matters here, precisely:**

- It is a **performance optimisation on ML-DSA's dominant cost**. Every ML-DSA `ExpandA`, `ExpandMask`,
  `SampleInBall`, and `H`/`mu` call goes through SHAKE squeezing. So **absolute** ML-DSA signing times
  may legitimately differ between 1.84 and 1.85.
- It is **output-neutral** — the packed bytes are identical to eager packing (the state is stable during
  squeeze). BC asserts this; §4 confirms it empirically.
- It is **length-dependent, not value-dependent**. The work saved is a function of *how many bytes the
  caller requests*, a public/structural quantity — not of any secret coefficient value. On its face it
  is therefore not a new secret-dependent channel.
- **One interaction worth flagging, not yet claimed.** `Poly.challenge` (SampleInBall) consumes SHAKE
  bytes in a `do { ... } while (b > i)` rejection loop, so the *number of squeezed bytes* is
  value-dependent. Lazy packing makes per-squeeze cost depend on how much is drawn. This is a
  mechanism by which 1.85 could in principle change the *shape* of value-dependent timing rather than
  just its offset. SampleInBall's input is the commitment hash c̃ (a hash output, and message-driven),
  so there is no evident route from the signing key to this loop — but it is recorded here as a
  hypothesis to keep in view, **not** as a finding.

`Pack` also differs, but only by an **added** `shortToLittleEndian(short[])` overload; the
`longToLittleEndian` overload Keccak calls is unchanged, and `CryptoServicesRegistrar` and `Arrays` are
identical. So `KeccakDigest` is the sole material hot-path change in the closure.

---

## 4. Empirical confirmation (gates B1a / B1b)

The source analysis predicts: identical keygen, identical signatures, possibly different timing. Run
against both jars with **identical harness bytecode and the identical JDK** (only the jar path changes):

- **Gate B1a — PASS.** For all 8 pinned seeds (the six-key probe seeds `100–105`, plus the dudect
  key-dependence seeds `0x0D5A65`/`0x0D5A66`), the ML-DSA-65 **public key is byte-identical** across
  1.84 and 1.85 (SHA-256 match). Private keys match too. Consistent with `MLDSAKeyPairGenerator` being
  byte-identical. **The two versions' keys are the same keys**, so all downstream comparisons are valid.
- **Gate B1b — PASS.** Deterministic signing is engaged (`det=true` 8/8: same `(key, message)` signs
  byte-identically twice), and every signature verifies (`verify=true` 8/8).
- **Cross-version signature identity.** Signature SHA-256 matches across 1.84 and 1.85 for all 8 seeds
  — a direct empirical confirmation that the Keccak lazy-packing change is output-neutral on the ML-DSA
  path, exactly as its comment claims.

A runtime discriminator was used to prove the intended jar actually loaded in each run (rather than
assuming it from the classpath): 1.85's `KeccakDigest` declares `queuePacked`, 1.84's does not.

```
--- run with 1.84 ---  MLDSASigner from : .../bcprov-jdk18on-1.84.jar
                       KeccakDigest.queuePacked present : false  => 1.84 eager-pack build
--- run with 1.85 ---  MLDSASigner from : .../bcprov-jdk18on-1.85.jar
                       KeccakDigest.queuePacked present : true   => 1.85 lazy-pack build
```

---

## 5. Answers to the questions this document had to settle

**Did the signing algorithm change?** No. `MLDSAEngine` is byte-identical; the rejection loop, the
`‖z‖∞` / `‖r0‖∞` / `‖c·t0‖∞` / hint-weight checks, `ExpandMask`, `SampleInBall`, and secret-key decode
are the same bytes.

**Did the API we call change?** No. `MLDSASigner` is byte-identical. The API additions are on
`HashMLDSASigner`, which we do not call.

**Did anything touch secret-dependent operations?** Not in ML-DSA. The only hot-path change is
`KeccakDigest`'s lazy squeeze packing, whose saved work is a function of requested output length
(public), not of secret values.

**Is David's expectation confirmed?** Yes for ML-DSA proper — stronger than "not touched much": *not
touched at all, byte-for-byte*. And his second point is also vindicated: 1.85 *did* change something
else in the API that reaches ML-DSA (`KeccakDigest`), which is a real candidate for shifting absolute
timings even though ML-DSA's own code is unchanged.

**What does this imply for Part B?** A key-dependent signal, if it persists, **cannot** be attributed to
a 1.85 ML-DSA change — the mechanism is implemented by identical bytecode. If the *magnitude* moves,
`KeccakDigest`'s lazy packing is the sole identified candidate on the hot path. This diff does **not**
by itself establish whether the channel is algorithmic or implementation-level; that is Part C.

---

*Reproduce:* fetch the four artifacts above, verify SHA-256, extract
`org/bouncycastle/pqc/crypto/mldsa/*` from both binary jars, compare per-class SHA-256, and `diff` the
corresponding `-sources.jar` trees. `KeccakDigest` must be compared explicitly — it is reached by
inheritance and does not appear in the ML-DSA constant-pool closure.
