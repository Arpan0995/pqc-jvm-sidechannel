#!/usr/bin/env python3
"""Apply C1 instrumentation to a pristine copy of the BC 1.85 ML-DSA sources.

Every edit is an *addition* of write-only bookkeeping. No recorded value is read back by the
algorithm, so signatures must stay byte-identical to the stock jar (gate C1a). The script asserts
on every substitution so a silent no-op patch cannot slip through.
"""
import pathlib
import sys

SRC = pathlib.Path(sys.argv[1])  # instrumented source tree root (…/org/bouncycastle/pqc/crypto/mldsa)


def patch(path, subs):
    p = SRC / path
    text = p.read_text()
    for old, new, label in subs:
        n = text.count(old)
        if n != 1:
            raise SystemExit(f"FAIL {path}: anchor for '{label}' matched {n} times, expected 1")
        text = text.replace(old, new)
    p.write_text(text)
    print(f"patched {path}: {len(subs)} site(s)")


# ---- MLDSAEngine: tag every rejection-loop exit with the stage that caused it ----------------
patch("MLDSAEngine.java", [
    (
        """            if (z.checkNorm(DilithiumGamma1 - DilithiumBeta))
            {
                continue;
            }""",
        """            if (z.checkNorm(DilithiumGamma1 - DilithiumBeta))
            {
                MLDSATrace.stage(MLDSATrace.REJ_Z);
                continue;
            }""",
        "reject at ||z||inf",
    ),
    (
        """            if (w0.checkNorm(DilithiumGamma2 - DilithiumBeta))
            {
                continue;
            }""",
        """            if (w0.checkNorm(DilithiumGamma2 - DilithiumBeta))
            {
                MLDSATrace.stage(MLDSATrace.REJ_R0);
                continue;
            }""",
        "reject at ||r0||inf",
    ),
    (
        """            if (h.checkNorm(DilithiumGamma2))
            {
                continue;
            }""",
        """            if (h.checkNorm(DilithiumGamma2))
            {
                MLDSATrace.stage(MLDSATrace.REJ_CT0);
                continue;
            }""",
        "reject at ||c.t0||inf",
    ),
    (
        """            if (n > DilithiumOmega)
            {
                continue;
            }

            Packing.packSignature(outSig, z, h, this);
            return outSig;""",
        """            if (n > DilithiumOmega)
            {
                MLDSATrace.stage(MLDSATrace.REJ_HINT);
                continue;
            }

            MLDSATrace.stage(MLDSATrace.ACCEPT);
            Packing.packSignature(outSig, z, h, this);
            return outSig;""",
        "reject at hint weight + accept",
    ),
])

# ---- Poly.checkNorm: count coefficients actually examined (captures the early exit) ----------
patch("Poly.java", [
    (
        """        for (i = 0; i < DilithiumN; ++i)
        {
            t = this.getCoeffIndex(i) >> 31;
            t = this.getCoeffIndex(i) - (t & 2 * this.getCoeffIndex(i));

            if (t >= B)
            {
                return true;
            }
        }
        return false;""",
        """        MLDSATrace.normScanCalls++;
        for (i = 0; i < DilithiumN; ++i)
        {
            t = this.getCoeffIndex(i) >> 31;
            t = this.getCoeffIndex(i) - (t & 2 * this.getCoeffIndex(i));

            if (t >= B)
            {
                MLDSATrace.normScanCoeffs += (i + 1);
                return true;
            }
        }
        MLDSATrace.normScanCoeffs += DilithiumN;
        return false;""",
        "checkNorm early-exit scan count",
    ),
    (
        """                b = (buf[pos++] & 0xFF);
            }
            while (b > i);""",
        """                b = (buf[pos++] & 0xFF);
                MLDSATrace.challengeBytes++;
            }
            while (b > i);""",
        "SampleInBall byte consumption",
    ),
])

print("instrumentation applied")
