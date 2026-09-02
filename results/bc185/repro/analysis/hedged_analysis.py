#!/usr/bin/env python3
"""Hedged-mode key-dependence check for ML-DSA signing time.

In the hedged signing mode BC draws fresh randomness per signature, so the
rejection-loop iteration count is an i.i.d. draw from a distribution that is
independent of both key and message; there is no deterministic (key, message)
work profile to join against. The key-dependence question is then answered
directly on timing: a one-way ANOVA of per-signature signing time by key over
a large multi-key campaign. Under the null (no key effect, key-independent
iteration distribution) the per-key mean times differ only by sampling noise
and placement/drift; the placement control (same keys, shuffled allocation,
fresh JVM) separates a value-dependent effect from a memory-placement one, as
in the deterministic study. Timing is cropped at the global 98th percentile,
matching discriminate.py.

Run from results/bc185/repro/data:  python3 ../analysis/hedged_analysis.py
"""
import csv, math, statistics, os
from collections import defaultdict
import numpy as np

CROP_HI = 0.98

def load(fn):
    per = defaultdict(list)
    with open(fn) as f:
        for r in csv.DictReader(f):
            per[int(r['key_id'])].append(float(r['time_ns']))
    return per

def crop_all(per):
    allt = np.array([t for v in per.values() for t in v])
    hi = np.quantile(allt, CROP_HI)
    return {k: np.array([t for t in v if t <= hi]) for k, v in per.items()}

def anova(groups):
    N = sum(len(g) for g in groups.values()); K = len(groups)
    gm = sum(g.sum() for g in groups.values()) / N
    ssb = sum(len(g) * (g.mean() - gm) ** 2 for g in groups.values())
    ssw = sum(((g - g.mean()) ** 2).sum() for g in groups.values())
    F = (ssb / (K - 1)) / (ssw / (N - K))
    return F, 100 * ssb / (ssb + ssw), K - 1, N - K

def ranks(d):
    ks = sorted(d, key=lambda k: d[k]); return {k: i for i, k in enumerate(ks)}

def spearman(x, y):
    rx, ry = ranks(x), ranks(y); n = len(x)
    return 1 - 6 * sum((rx[k] - ry[k]) ** 2 for k in x) / (n * (n * n - 1))

def main():
    D = crop_all(load('time_discovery_65hedged.csv'))
    R = crop_all(load('time_rerun_65hedged.csv'))
    mD = {k: float(D[k].mean()) for k in D}; mR = {k: float(R[k].mean()) for k in R}
    FD, etaD, df1, df2 = anova(D); FR, etaR, _, _ = anova(R)
    se = statistics.mean(float(D[k].std(ddof=1)) / math.sqrt(len(D[k])) for k in D)
    sD = max(mD.values()) - min(mD.values()); sR = max(mR.values()) - min(mR.values())
    print("HEDGED ML-DSA-65 (fresh randomness per signature; time-only, no work join)")
    print(f"  discovery : {sum(len(v) for v in D.values())} sigs, {len(D)} keys, mean {statistics.mean(mD.values())/1000:.1f} us")
    print(f"  per-key mean-time spread: discovery {sD:.0f} ns ({100*sD/min(mD.values()):.3f}%), placement {sR:.0f} ns ({100*sR/min(mR.values()):.3f}%)")
    print(f"  per-key SE of mean {se:.0f} ns -> 20 pure-noise means span ~{3.735*se:.0f} ns")
    print(f"  one-way ANOVA of signing time by key: discovery F({df1},{df2})={FD:.2f}, eta^2={etaD:.4f}%; placement F={FR:.2f}, eta^2={etaR:.4f}%")
    print(f"  5% critical F(19, large) ~ 1.59")
    print(f"  rank persistence discovery vs placement (n=20): Spearman rho={spearman(mD, mR):+.3f}")
    print(f"  detectable per-key effect at this n ~ a few x SE = order {3*se:.0f} ns ({100*3*se/statistics.mean(mD.values()):.3f}% of mean)")

if __name__ == '__main__':
    main()
