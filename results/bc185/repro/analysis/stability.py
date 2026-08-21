#!/usr/bin/env python3
"""Decisive checks (handoff §7 steps 3-5): is the pure-stratum per-key residual a stable value
channel (Outcome B) or a placement/drift artifact (Outcome A)?

Pure stratum = profile_key '1:0-0-0-0-1' (accept on iteration 1; norm_scan constant = 4352), so the
algorithmic work and the checkNorm channel are both held fixed. Any per-key mean-time difference here
is either a genuine secret-value implementation channel or a per-key memory-placement/drift artifact.

Deciders:
  (3) magnitude: per-key pure-stratum spread on the steadier host.
  (4) placement/stability: Spearman + Pearson of per-key means, discovery vs alloc-shuffled rerun
      (same keys, fresh JVM). Low correlation OR ranking moving with alloc order => artifact => A.
      Also within-run first-half vs second-half stability (the Air showed 0.116).
  (5) replication: same on fresh keys (200-219, corpus 31337).
"""
import argparse, csv
from collections import defaultdict
import numpy as np

PURE = "1:0-0-0-0-1"
CROP_HI = 0.98
MIN_CELL = 800


def load_join(work_path, time_path):
    work = {}
    with open(work_path) as f:
        for r in csv.DictReader(f):
            work[(int(r["key_id"]), int(r["message_id"]))] = r["profile_key"]
    rows = []  # (key, msg, profile_key, time_ns)
    with open(time_path) as f:
        for r in csv.DictReader(f):
            k = (int(r["key_id"]), int(r["message_id"]))
            pk = work.get(k)
            if pk is not None:
                rows.append((k[0], k[1], pk, int(r["time_ns"])))
    return rows


def crop(a):
    a = np.asarray(a, dtype=float)
    if len(a) < 10:
        return a
    return a[a <= np.quantile(a, CROP_HI)]


def pure_perkey_means(rows, half=None):
    """Per-key cropped mean time within the pure stratum. half in {None,'lo','hi'} splits by message_id."""
    if rows:
        msgs = [r[1] for r in rows if r[2] == PURE]
        mid = (min(msgs) + max(msgs)) / 2 if msgs else 0
    perkey = defaultdict(list)
    for r in rows:
        if r[2] != PURE:
            continue
        if half == "lo" and r[1] >= mid:
            continue
        if half == "hi" and r[1] < mid:
            continue
        perkey[r[0]].append(r[3])
    return {k: crop(v).mean() for k, v in perkey.items() if len(v) >= MIN_CELL}


def spearman(x, y):
    x = np.asarray(x, dtype=float); y = np.asarray(y, dtype=float)
    rx = np.argsort(np.argsort(x)); ry = np.argsort(np.argsort(y))
    return float(np.corrcoef(rx, ry)[0, 1])


def pearson(x, y):
    return float(np.corrcoef(np.asarray(x, float), np.asarray(y, float))[0, 1])


def report_pair(label, means_a, means_b, name_a, name_b):
    keys = sorted(set(means_a) & set(means_b))
    a = [means_a[k] for k in keys]; b = [means_b[k] for k in keys]
    sp = spearman(a, b); pe = pearson(a, b)
    print(f"\n--- {label}  ({name_a} vs {name_b}; {len(keys)} keys) ---")
    print(f"  Spearman rank corr of per-key pure-stratum means = {sp:+.3f}")
    print(f"  Pearson corr of per-key pure-stratum means        = {pe:+.3f}")
    # ranking table
    order_a = sorted(keys, key=lambda k: means_a[k])
    order_b = sorted(keys, key=lambda k: means_b[k])
    print(f"  slowest-3 {name_a}: {order_a[-3:][::-1]}   slowest-3 {name_b}: {order_b[-3:][::-1]}")
    print(f"  fastest-3 {name_a}: {order_a[:3]}   fastest-3 {name_b}: {order_b[:3]}")
    return sp, pe


def spread(means, label):
    v = np.array(list(means.values()))
    lo, hi = v.min(), v.max()
    print(f"  {label}: per-key pure-stratum mean {lo:.0f}..{hi:.0f} ns  spread={hi-lo:.0f} ns ({100*(hi-lo)/lo:.3f}%)")
    return hi - lo, 100 * (hi - lo) / lo


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--work-disc", required=True)
    ap.add_argument("--time-disc", required=True)
    ap.add_argument("--time-rerun", required=True)
    ap.add_argument("--work-rep")
    ap.add_argument("--time-rep")
    args = ap.parse_args()

    disc = load_join(args.work_disc, args.time_disc)
    rerun = load_join(args.work_disc, args.time_rerun)  # same keys/msgs, so same work profile join

    print("=== (3) PURE-STRATUM MAGNITUDE on the steadier host ===")
    md = pure_perkey_means(disc)
    spread(md, "discovery")
    mr = pure_perkey_means(rerun)
    spread(mr, "rerun (alloc-shuffled)")

    print("\n=== (4) PLACEMENT / STABILITY — THE DECIDER ===")
    print("within-run stability (first half vs second half of messages):")
    d_lo = pure_perkey_means(disc, "lo"); d_hi = pure_perkey_means(disc, "hi")
    report_pair("discovery half-split", d_lo, d_hi, "1stHalf", "2ndHalf")
    report_pair("discovery vs alloc-shuffled rerun", md, mr, "disc", "rerun")

    if args.work_rep and args.time_rep:
        print("\n=== (5) REPLICATION on fresh keys 200-219, corpus 31337 ===")
        rep = load_join(args.work_rep, args.time_rep)
        mrep = pure_perkey_means(rep)
        spread(mrep, "replication")
        rlo = pure_perkey_means(rep, "lo"); rhi = pure_perkey_means(rep, "hi")
        report_pair("replication half-split", rlo, rhi, "1stHalf", "2ndHalf")

    print("\n[interpretation] low/unstable correlation (~Air's 0.116) or ranking that moves with "
          "alloc order => placement artifact => OUTCOME A. high correlation that survives alloc-"
          "shuffle AND replicates => candidate value channel => OUTCOME B.")


if __name__ == "__main__":
    main()
