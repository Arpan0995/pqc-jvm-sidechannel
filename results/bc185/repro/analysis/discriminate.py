#!/usr/bin/env python3
"""Discrimination analysis (Part C3) executing the frozen pre-registration decision tree.

Inputs:
  --work  work-profile CSV  (instrumented build: iterations, profile_key, norm_scan_coeffs, ...)
  --time  timing CSV        (stock jar: key_id, message_id, time_ns)
  --label a run label for output
  --out   output directory

Join on (key_id, message_id). Then:
  Step 1  time~iterations monotone check + ANCOVA key term after controlling for iterations
  Step 2  within each well-populated profile_key, cross-key Welch t / Mann-Whitney / Cliff's delta,
          Holm-Bonferroni over (key-pair x stratum)
  Step 2b norm_scan_coeffs localization regression within strata

Only stdlib + numpy. No SciPy dependency (t/normal tail approximations are implemented locally and
are adequate at the |t|>4.5 decision boundary).
"""
import argparse, csv, math, statistics, sys
from collections import defaultdict
import numpy as np

# ---- pre-registered thresholds (must match discrimination_preregistration.md) ----
T_THRESH = 4.5
DELTA_FLOOR = 0.147
MIN_CELL = 800          # min signatures per (key, profile_key) to call a cell well-populated
MIN_KEYS = 12           # min keys meeting MIN_CELL for a stratum to be "well-populated"
CROP_HI = 0.98          # percentile crop: drop the slow 2% tail (host preemption/GC), like the 1.84 rule


def load_join(work_path, time_path):
    work = {}
    with open(work_path) as f:
        for r in csv.DictReader(f):
            work[(int(r['key_id']), int(r['message_id']))] = (
                int(r['iterations']), r['profile_key'],
                int(r['norm_scan_coeffs']), int(r['challenge_bytes']))
    rows = []
    miss = 0
    with open(time_path) as f:
        for r in csv.DictReader(f):
            k = (int(r['key_id']), int(r['message_id']))
            w = work.get(k)
            if w is None:
                miss += 1
                continue
            rows.append((k[0], k[1], w[0], w[1], w[2], w[3], int(r['time_ns'])))
    return rows, miss, len(work)


def crop(times):
    if len(times) < 10:
        return times
    hi = np.quantile(times, CROP_HI)
    return times[times <= hi]


def welch(a, b):
    na, nb = len(a), len(b)
    if na < 2 or nb < 2:
        return float('nan'), float('nan')
    ma, mb = a.mean(), b.mean()
    va, vb = a.var(ddof=1), b.var(ddof=1)
    se = math.sqrt(va/na + vb/nb)
    if se == 0:
        return float('nan'), float('nan')
    t = (ma - mb) / se
    df = (va/na + vb/nb)**2 / ((va/na)**2/(na-1) + (vb/nb)**2/(nb-1))
    return t, df


def cliffs_delta(a, b):
    """Exact-ish Cliff's delta via sorted-rank counting; O((na+nb) log)."""
    a = np.sort(a); b = np.sort(b)
    # for each element of a, count elements of b less than / greater than it
    import bisect
    gt = 0; lt = 0
    bl = b.tolist()
    for x in a:
        lo = bisect.bisect_left(bl, x)
        hi = bisect.bisect_right(bl, x)
        lt += lo               # b < x
        gt += len(bl) - hi     # b > x
    n = len(a) * len(b)
    return (gt - lt) / n if n else float('nan')   # >0 means a tends larger


def mannwhitney_z(a, b):
    a = np.asarray(a); b = np.asarray(b)
    na, nb = len(a), len(b)
    allv = np.concatenate([a, b])
    order = allv.argsort(kind='mergesort')
    ranks = np.empty(len(allv))
    ranks[order] = np.arange(1, len(allv)+1)
    # tie correction (average ranks)
    sv = allv[order]
    i = 0
    while i < len(sv):
        j = i
        while j+1 < len(sv) and sv[j+1] == sv[i]:
            j += 1
        if j > i:
            avg = (ranks[order[i]] + ranks[order[j]]) / 2
            for k in range(i, j+1):
                ranks[order[k]] = avg
        i = j+1
    Ra = ranks[:na].sum()
    U = Ra - na*(na+1)/2
    mu = na*nb/2
    sd = math.sqrt(na*nb*(na+nb+1)/12)
    return (U - mu)/sd if sd else float('nan')


def holm(pvals):
    """Return Holm-Bonferroni adjusted p-values in original order."""
    idx = sorted(range(len(pvals)), key=lambda i: pvals[i])
    m = len(pvals)
    adj = [0.0]*m
    running = 0.0
    for rank, i in enumerate(idx):
        val = (m - rank) * pvals[i]
        running = max(running, val)
        adj[i] = min(1.0, running)
    return adj


def t_to_p(t, df):
    """Two-sided p from t via a normal approx for large df (df here is huge)."""
    if math.isnan(t):
        return 1.0
    z = abs(t)
    # survival of standard normal * 2
    return 2 * 0.5 * math.erfc(z / math.sqrt(2))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--work', required=True)
    ap.add_argument('--time', required=True)
    ap.add_argument('--label', default='discovery')
    ap.add_argument('--out', default='.')
    args = ap.parse_args()

    rows, miss, nwork = load_join(args.work, args.time)
    print(f"[{args.label}] joined rows: {len(rows)}  (work rows {nwork}, timing rows unmatched: {miss})")
    if not rows:
        sys.exit("no joined rows")

    keys = sorted(set(r[0] for r in rows))
    times = np.array([r[6] for r in rows], dtype=float)
    iters = np.array([r[2] for r in rows], dtype=float)

    # ---- Step 1a: monotone time~iterations ----
    print("\n=== STEP 1a: time vs iterations (dominant driver check) ===")
    by_it = defaultdict(list)
    for r in rows:
        by_it[r[2]].append(r[6])
    prev = None; mono = True
    for it in sorted(by_it):
        if len(by_it[it]) < 50:
            continue
        med = statistics.median(by_it[it])
        tag = ''
        if prev is not None and med < prev:
            mono = False; tag = '  <-- non-monotone'
        print(f"  iters={it:2d}  n={len(by_it[it]):7d}  median={med:10.0f} ns{tag}")
        prev = med
    # Spearman-ish: correlation of iters vs time
    r_pearson = np.corrcoef(iters, times)[0,1]
    print(f"  Pearson r(iterations, time) = {r_pearson:.4f}   monotone medians: {mono}")

    # ---- Step 1b: ANCOVA key term after controlling for iterations ----
    # OLS: time ~ iterations + C(key)  (dummy-coded, drop first key)
    print("\n=== STEP 1b: ANCOVA  time ~ iterations + C(key) ===")
    kidx = {k:i for i,k in enumerate(keys)}
    n = len(rows)
    p = 1 + 1 + (len(keys)-1)   # intercept + iterations + (K-1) key dummies
    X = np.zeros((n, p)); y = times.copy()
    X[:,0] = 1.0
    X[:,1] = iters
    for i,r in enumerate(rows):
        ki = kidx[r[0]]
        if ki > 0:
            X[i, 1+ki] = 1.0
    beta, *_ = np.linalg.lstsq(X, y, rcond=None)
    resid = y - X@beta
    dof = n - p
    sigma2 = (resid@resid)/dof
    XtX_inv = np.linalg.inv(X.T@X)
    se = np.sqrt(np.diag(XtX_inv)*sigma2)
    # full model R^2
    sst = ((y-y.mean())**2).sum(); sse = (resid@resid); r2_full = 1-sse/sst
    # reduced model time ~ iterations only
    Xr = X[:,:2]
    br,*_ = np.linalg.lstsq(Xr, y, rcond=None)
    sser = ((y-Xr@br)**2).sum(); r2_red = 1-sser/sst
    # partial F for the key block
    q = len(keys)-1
    Fkey = ((sser-sse)/q) / (sse/dof)
    var_frac_key = (sser - sse)/sst
    print(f"  n={n}  full R^2={r2_full:.5f}  (iterations-only R^2={r2_red:.5f})")
    print(f"  iterations slope = {beta[1]:.1f} ns/iter  (se {se[1]:.2f})")
    print(f"  KEY block partial F({q},{dof}) = {Fkey:.2f}   extra variance explained by key = {var_frac_key*100:.4f}%")
    print(f"  -> key term {'PRESENT' if var_frac_key>=0.01 and Fkey>1 else 'negligible/absent'} "
          f"by pre-registered bar (>=1% residual variance)")

    # ---- Step 2: within-profile_key cross-key comparison ----
    print("\n=== STEP 2: within identical profile_key, cross-key timing ===")
    cell = defaultdict(list)   # (profile_key, key) -> [times]
    for r in rows:
        cell[(r[3], r[0])].append(r[6])
    strata = defaultdict(dict)
    for (pk,k),ts in cell.items():
        strata[pk][k] = np.array(ts, dtype=float)

    wellpop = []
    for pk, kd in strata.items():
        good = [k for k,ts in kd.items() if len(ts) >= MIN_CELL]
        if len(good) >= MIN_KEYS:
            wellpop.append((pk, good))
    # order by total support
    wellpop.sort(key=lambda x: -sum(len(strata[x[0]][k]) for k in x[1]))
    print(f"  well-populated strata (>= {MIN_KEYS} keys with >= {MIN_CELL} sigs): {len(wellpop)}")

    tests = []   # (pk, ka, kb, t, df, z, delta, mean_gap_ns)
    for pk, good in wellpop:
        cropped = {k: crop(strata[pk][k]) for k in good}
        for ai in range(len(good)):
            for bi in range(ai+1, len(good)):
                ka, kb = good[ai], good[bi]
                a, b = cropped[ka], cropped[kb]
                t, df = welch(a, b)
                delta = cliffs_delta(a, b)
                gap = a.mean() - b.mean()
                tests.append([pk, ka, kb, t, df, delta, gap])
    print(f"  cross-key tests (key-pair x stratum): {len(tests)}")
    if tests:
        pvals = [t_to_p(t[3], t[4]) for t in tests]
        adj = holm(pvals)
        flagged = []
        for t,pa in zip(tests, adj):
            sig = (pa < 0.05) and (abs(t[3])>T_THRESH) and (abs(t[5])>=DELTA_FLOOR)
            if sig:
                flagged.append((t, pa))
        # report the most extreme tests regardless
        tests_sorted = sorted(zip(tests,adj,pvals), key=lambda x:-abs(x[0][3]))
        print("  top 8 |t| cross-key tests (profile_key, keyA,keyB, t, delta, mean_gap_ns, holm_p):")
        for t,pa,pv in tests_sorted[:8]:
            print(f"    {t[0]:14s} k{t[1]:>2}-k{t[2]:>2}  t={t[3]:8.2f}  d={t[5]:+.4f}  gap={t[6]:+9.0f}ns  holm_p={pa:.2e}")
        print(f"\n  PRE-REGISTERED 'present' (holm_p<0.05 AND |t|>{T_THRESH} AND |delta|>={DELTA_FLOOR}): "
              f"{len(flagged)} / {len(tests)}")
        maxd = max(abs(t[5]) for t in tests)
        print(f"  max |Cliff's delta| across all cross-key tests = {maxd:.4f}  "
              f"({'exceeds' if maxd>=DELTA_FLOOR else 'below'} the {DELTA_FLOOR} floor)")

    # ---- Purest identical-work stratum: accept-on-first-iteration (norm_scan constant) ----
    pure = [r for r in rows if r[3] == '1:0-0-0-0-1']
    _nsc = sorted({r[4] for r in pure})
    print(f"\n=== PURE STRATUM 1:0-0-0-0-1 (accept on iter 1; norm_scan constant = {_nsc[0] if len(_nsc)==1 else _nsc}) ===")
    if pure:
        ns_pure = {r[4] for r in pure}
        print(f"  n={len(pure)}  norm_scan values present: {sorted(ns_pure)[:4]}"
              f"{'...' if len(ns_pure)>4 else ''}  (constant => checkNorm channel contributes 0 here)")
        perkey = defaultdict(list)
        for r in pure:
            perkey[r[0]].append(r[6])
        good = [k for k in keys if len(perkey.get(k, [])) >= MIN_CELL]
        cr = {k: crop(np.array(perkey[k], dtype=float)) for k in good}
        means = {k: cr[k].mean() for k in good}
        slow = max(means, key=means.get); fast = min(means, key=means.get)
        print(f"  per-key mean time: {means[fast]:.0f}..{means[slow]:.0f} ns  "
              f"spread={means[slow]-means[fast]:.0f} ns ({100*(means[slow]-means[fast])/means[fast]:.3f}%)")
        # worst-case cross-key pair in the pure stratum
        t, df = welch(cr[slow], cr[fast]); d = cliffs_delta(cr[slow], cr[fast])
        print(f"  extreme pair k{slow} vs k{fast}: t={t:.2f}  Cliff d={d:+.4f}  "
              f"-> {'PRESENT' if abs(t)>T_THRESH and abs(d)>=DELTA_FLOOR else 'below threshold'}")

    # ---- Step 2b: localization regression within reject-containing strata ----
    print("\n=== STEP 2b: within-stratum localization (norm_scan_coeffs, challenge_bytes) ===")
    # empirical ns per scanned coefficient, from a reject-containing stratum where norm_scan varies
    for pk0 in [w[0] for w in wellpop]:
        sub = [r for r in rows if r[3] == pk0]
        ns = np.array([r[4] for r in sub], dtype=float)
        if ns.std() < 1e-9:
            continue  # skip strata where norm_scan is constant (e.g. the pure accept stratum)
        tt = np.array([r[6] for r in sub], dtype=float)
        cb = np.array([r[5] for r in sub], dtype=float)
        rns = np.corrcoef(ns, tt)[0, 1]
        # slope ns_time per scanned coeff via OLS on (norm_scan, challenge_bytes)
        A = np.column_stack([np.ones(len(sub)), ns, cb])
        coef, *_ = np.linalg.lstsq(A, tt, rcond=None)
        # per-key mean norm_scan spread within this stratum
        pk_ns = defaultdict(list)
        for r in sub:
            pk_ns[r[0]].append(r[4])
        m = {k: statistics.mean(v) for k, v in pk_ns.items() if len(v) >= 50}
        spread = (max(m.values()) - min(m.values())) if m else float('nan')
        print(f"  {pk0:16s} n={len(sub):7d}  r(scan,time)={rns:+.4f}  "
              f"time/scan-coeff={coef[1]:+.3f} ns  per-key scan spread={spread:.0f} coeffs "
              f"=> checkNorm timing spread ~ {coef[1]*spread:+.1f} ns")
        break  # one representative reject stratum is enough to bound the channel

    print("\n[done]")


if __name__ == '__main__':
    main()
