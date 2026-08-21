#!/usr/bin/env python3
"""C-phase plots: per-key mean time, time-vs-iterations scatter+fit, within-pure-stratum
cross-key distributions. Reads the joined work+time data. Writes PNGs to --out."""
import argparse, csv, statistics
from collections import defaultdict
import numpy as np
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt

CROP_HI = 0.98


def load(work_path, time_path):
    work = {}
    with open(work_path) as f:
        for r in csv.DictReader(f):
            work[(int(r['key_id']), int(r['message_id']))] = (int(r['iterations']), r['profile_key'], int(r['norm_scan_coeffs']))
    rows = []
    with open(time_path) as f:
        for r in csv.DictReader(f):
            k = (int(r['key_id']), int(r['message_id']))
            w = work.get(k)
            if w:
                rows.append((k[0], k[1], w[0], w[1], w[2], int(r['time_ns'])))
    return rows


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--work', required=True)
    ap.add_argument('--time', required=True)
    ap.add_argument('--out', required=True)
    ap.add_argument('--label', default='discovery')
    a = ap.parse_args()
    rows = load(a.work, a.time)
    keys = sorted(set(r[0] for r in rows))

    # ---- Plot 1: per-key mean signing time (whole dataset) ----
    perkey = defaultdict(list)
    for r in rows:
        perkey[r[0]].append(r[5])
    means = []; ses = []
    for k in keys:
        v = np.array(perkey[k], dtype=float)
        v = v[v <= np.quantile(v, CROP_HI)]
        means.append(v.mean()/1000); ses.append(v.std(ddof=1)/np.sqrt(len(v))/1000)
    fig, ax = plt.subplots(figsize=(9,4))
    ax.errorbar(range(len(keys)), means, yerr=[s*1.96 for s in ses], fmt='o', capsize=3, color='#2b6cb0')
    ax.set_xticks(range(len(keys))); ax.set_xticklabels([f'k{k}' for k in keys], rotation=45, fontsize=7)
    ax.set_ylabel('mean signing time (µs)'); ax.set_title(f'Per-key mean ML-DSA-65 signing time (BC 1.85, {a.label})\n'
        'whole dataset — spread is the ALGORITHMIC rejection-count effect (mean iters differ by key)')
    ax.grid(True, alpha=0.3)
    fig.tight_layout(); fig.savefig(f'{a.out}/c_perkey_mean_time.png', dpi=130); plt.close(fig)

    # ---- Plot 2: time vs iterations scatter + robust median fit ----
    by_it = defaultdict(list)
    for r in rows:
        by_it[r[2]].append(r[5])
    its = sorted(k for k in by_it if len(by_it[k]) >= 50)
    med = [statistics.median(by_it[i])/1000 for i in its]
    # subsample scatter for legibility
    import random; random.seed(0)
    samp = random.sample(rows, min(40000, len(rows)))
    fig, ax = plt.subplots(figsize=(9,5))
    ax.scatter([r[2] for r in samp], [r[5]/1000 for r in samp], s=2, alpha=0.06, color='#718096')
    ax.plot(its, med, '-o', color='#c53030', ms=4, label='median per iteration count')
    # linear fit through medians
    A = np.column_stack([np.ones(len(its)), its])
    coef,*_ = np.linalg.lstsq(A, np.array(med), rcond=None)
    ax.plot(its, coef[0]+coef[1]*np.array(its), '--', color='#2f855a',
            label=f'fit: {coef[1]:.1f} µs/iter + {coef[0]:.0f} µs')
    ax.set_xlabel('rejection-loop iterations'); ax.set_ylabel('signing time (µs)')
    ax.set_title(f'ML-DSA-65 signing time vs iteration count (BC 1.85, {a.label})\n'
        'iteration count is the dominant, monotone driver — the algorithmic floor')
    ax.legend(); ax.grid(True, alpha=0.3); ax.set_ylim(0, med[min(len(med)-1, 20)]*1.3 if med else None)
    fig.tight_layout(); fig.savefig(f'{a.out}/c_time_vs_iterations.png', dpi=130); plt.close(fig)

    # ---- Plot 3: within pure stratum (accept iter1) cross-key time distributions ----
    pure = [r for r in rows if r[3] == '1:0-0-0-0-1']
    if pure:
        pk = defaultdict(list)
        for r in pure:
            pk[r[0]].append(r[5]/1000)
        show = keys[:10]
        data = []
        for k in show:
            v = np.array(pk[k]); v = v[v <= np.quantile(v, CROP_HI)]
            data.append(v)
        fig, ax = plt.subplots(figsize=(10,5))
        bp = ax.boxplot(data, showfliers=False, patch_artist=True)
        for b in bp['boxes']:
            b.set(facecolor='#bee3f8', alpha=0.7)
        ax.set_xticklabels([f'k{k}' for k in show])
        ax.set_ylabel('signing time (µs)')
        ax.set_title('Cross-key signing-time distributions WITHIN identical work profile\n'
            'stratum 1:0-0-0-0-1 (accept on iteration 1; norm_scan constant). '
            'Overlap => no implementation residual')
        ax.grid(True, alpha=0.3, axis='y')
        fig.tight_layout(); fig.savefig(f'{a.out}/c_within_profile_crosskey.png', dpi=130); plt.close(fig)

    print('wrote c_perkey_mean_time.png, c_time_vs_iterations.png, c_within_profile_crosskey.png')


if __name__ == '__main__':
    main()
