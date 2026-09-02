#!/usr/bin/env python3
"""Rank persistence and noise floor of the pure-stratum per-key residual.

Discovery and the placement control sign the same 20 keys over the same corpus
with a shuffled signer-allocation order in a fresh JVM. A value-dependent
channel would preserve each key's timing rank across the two campaigns; a
placement or drift artifact would not. Uses discriminate.py's statistic for
the pure stratum 1:0-0-0-0-1 (per-key mean of that key's own
98th-percentile-cropped times, numpy.quantile) and adds: the per-key standard
error and the expected span of twenty pure-noise means; split-half rank
reproducibility within each campaign; a one-way ANOVA of cropped time by key;
and the SampleInBall challenge-byte accounting (the one quantity that varies
within the pure stratum; deterministic in (key, message), so identical across
the two campaigns).

Run from results/bc185/repro/data:  python3 ../analysis/rank_persistence.py
"""
import gzip, csv, math, statistics, json, sys, os, argparse
from collections import defaultdict
import numpy as np

def _open(base):
    for cand in (base + '.csv.gz', base + '.csv'):
        if os.path.exists(cand):
            return gzip.open(cand, 'rt') if cand.endswith('.gz') else open(cand)
    raise FileNotFoundError(base + '.csv[.gz]')

CROP_HI = 0.98
PURE = '1:0-0-0-0-1'

def load_work(base):
    pure = {}
    with _open(base) as f:
        for r in csv.DictReader(f):
            if r['profile_key'] == PURE:
                pure[(int(r['key_id']), int(r['message_id']))] = int(r['challenge_bytes'])
    return pure

def load_time(base, pure):
    per = defaultdict(list)
    with _open(base) as f:
        for r in csv.DictReader(f):
            k = (int(r['key_id']), int(r['message_id']))
            if k in pure:
                per[k[0]].append((k[1], float(r['time_ns']), pure[k]))
    return per

def crop(v):
    v = np.asarray(v, dtype=float); hi = np.quantile(v, CROP_HI); return v[v <= hi]

def ranks(d):
    ks = sorted(d, key=lambda k: d[k]); return {k: i for i, k in enumerate(ks)}

def spearman(x, y):
    rx, ry = ranks(x), ranks(y); n = len(x)
    return 1 - 6 * sum((rx[k] - ry[k]) ** 2 for k in x) / (n * (n * n - 1))

def kendall(x, y):
    ks = list(x); c = d = 0
    for i in range(len(ks)):
        for j in range(i + 1, len(ks)):
            a = (x[ks[i]] - x[ks[j]]) * (y[ks[i]] - y[ks[j]]); c += a > 0; d += a < 0
    return (c - d) / (c + d)

def means(per, sel=lambda m: True):
    return {k: float(crop([t for m, t, b in per[k] if sel(m)]).mean()) for k in per}

def anova(per):
    groups = [crop([t for m, t, b in per[k]]) for k in per]
    N = sum(len(g) for g in groups); K = len(groups); gm = sum(g.sum() for g in groups) / N
    ssb = sum(len(g) * (g.mean() - gm) ** 2 for g in groups)
    ssw = sum(((g - g.mean()) ** 2).sum() for g in groups)
    return (ssb / (K - 1)) / (ssw / (N - K)), 100 * ssb / (ssb + ssw), K - 1, N - K

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--suffix', default='', help='param suffix, e.g. _44 (default: -65 files, no suffix)')
    ap.add_argument('--dir', default='.')
    a = ap.parse_args()
    sfx = a.suffix
    base = lambda name: os.path.join(a.dir, name + sfx)
    pure = load_work(base('work_discovery'))
    D = load_time(base('time_discovery'), pure); R = load_time(base('time_rerun'), pure)
    mD, mR = means(D), means(R)
    out = {}
    out['n_pure'] = sum(len(v) for v in D.values()); out['keys'] = len(mD)
    out['spread_D'] = max(mD.values()) - min(mD.values()); out['spread_R'] = max(mR.values()) - min(mR.values())
    out['pct_D'] = 100 * out['spread_D'] / min(mD.values()); out['pct_R'] = 100 * out['spread_R'] / min(mR.values())
    out['rho'] = spearman(mD, mR); out['tau'] = kendall(mD, mR)
    ses = [float(crop([t for m, t, b in D[k]]).std(ddof=1) / math.sqrt(len(crop([t for m, t, b in D[k]])))) for k in D]
    out['se'] = statistics.mean(ses); out['noise_span'] = 3.735 * out['se']   # E[range] of 20 N(0,1) = 3.735
    out['rho_D_split'] = spearman(means(D, lambda m: m % 2 == 0), means(D, lambda m: m % 2 == 1))
    out['rho_D_halves'] = spearman(means(D, lambda m: m < 30000), means(D, lambda m: m >= 30000))
    out['rho_R_split'] = spearman(means(R, lambda m: m % 2 == 0), means(R, lambda m: m % 2 == 1))
    FD, etaD, df1, df2 = anova(D); FR, etaR, _, _ = anova(R)
    out.update(F_D=FD, eta_D=etaD, F_R=FR, eta_R=etaR, df1=df1, df2=df2)
    bD = {k: statistics.mean(b for m, t, b in D[k]) for k in D}
    bs = max(bD.values()) - min(bD.values())
    xs = np.array([b for k in D for m, t, b in D[k]]); ys = np.array([t for k in D for m, t, b in D[k]])
    slope = float(np.cov(xs, ys)[0, 1] / xs.var(ddof=1))
    out['bytes_spread'] = bs; out['bytes_ns'] = abs(slope) * bs; out['rho_bytes_D'] = spearman(bD, mD)
    print(f"pure stratum {PURE}: {out['n_pure']} signatures per campaign, {out['keys']} keys; per-key crop at own {CROP_HI:.0%} percentile (numpy.quantile)")
    print(f"per-key spread: discovery {out['spread_D']:.0f} ns ({out['pct_D']:.3f}%), placement control {out['spread_R']:.0f} ns ({out['pct_R']:.3f}%)")
    print(f"per-key SE of the cropped mean: {out['se']:.0f} ns; expected span of 20 pure-noise means (3.735 SE): {out['noise_span']:.0f} ns")
    print(f"rank persistence across campaigns (n=20): Spearman rho={out['rho']:+.3f}, Kendall tau={out['tau']:+.3f}  (two-sided 5% critical |rho|~0.447, |tau|~0.33)")
    print(f"rank reproducibility within discovery: even/odd messages rho={out['rho_D_split']:+.3f}, first/second half rho={out['rho_D_halves']:+.3f}; within placement control even/odd rho={out['rho_R_split']:+.3f}")
    print(f"one-way ANOVA of cropped pure-stratum time by key: discovery F({df1},{df2})={FD:.2f}, eta^2={etaD:.4f}%; placement F={FR:.2f}, eta^2={etaR:.4f}%")
    print(f"SampleInBall challenge bytes: per-key spread {bs:.2f} bytes; slope {slope:+.1f} ns/byte -> {out['bytes_ns']:.1f} ns attributable; Spearman(bytes,time) discovery {out['rho_bytes_D']:+.3f}")
    rd, rr = ranks(mD), ranks(mR)
    print("\nper-key cropped mean (ns): key  discovery  placement  rankD rankR")
    for k in sorted(mD):
        print(f"  {k:2d}  {mD[k]:9.0f}  {mR[k]:9.0f}   {rd[k]:2d}   {rr[k]:2d}")
    json.dump(out, open(os.path.join(a.dir, 'rank_persistence' + sfx + '.json'), 'w'), indent=1)

if __name__ == '__main__':
    main()
