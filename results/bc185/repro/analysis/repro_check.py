#!/usr/bin/env python3
"""Validation gate (handoff §4): reproduce the host-independent work profile from a WorkProfile CSV.

These quantities are deterministic in (key, message); if they do not reproduce, the jar/JDK/seeds are
mis-set and no timing should be trusted. Prints a PASS/FAIL against the Air's frozen expectations.
"""
import argparse, csv
from collections import defaultdict


EXPECT = {
    "mean_iters": 5.134,
    "spread_lo": 5.095, "spread_hi": 5.184, "spread_pct": 1.75,
    "rej_z_pct": 47.3, "rej_r0_pct": 52.6, "rej_ct0_pct": 0.00, "rej_hint_pct": 0.09,
    "accept1_norm_scan": 4352,
    "wellpop_strata": 16, "wellpop_coverage_pct": 70.8,
}
MIN_CELL = 800
MIN_KEYS = 12


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--work", required=True)
    args = ap.parse_args()

    n = 0
    tot_iters = 0
    rej = {"z": 0, "r0": 0, "ct0": 0, "hint": 0}
    per_key_iters = defaultdict(lambda: [0, 0])  # key -> [sum_iters, count]
    accept1_norm = set()
    accept1_n = 0
    cell = defaultdict(int)  # (profile_key, key) -> count

    with open(args.work) as f:
        for r in csv.DictReader(f):
            n += 1
            it = int(r["iterations"])
            k = int(r["key_id"])
            tot_iters += it
            rej["z"] += int(r["rej_z"]); rej["r0"] += int(r["rej_r0"])
            rej["ct0"] += int(r["rej_ct0"]); rej["hint"] += int(r["rej_hint"])
            per_key_iters[k][0] += it; per_key_iters[k][1] += 1
            pk = r["profile_key"]
            cell[(pk, k)] += 1
            if pk == "1:0-0-0-0-1":
                accept1_norm.add(int(r["norm_scan_coeffs"]))
                accept1_n += 1

    mean_iters = tot_iters / n
    key_means = {k: s / c for k, (s, c) in per_key_iters.items()}
    lo = min(key_means.values()); hi = max(key_means.values())
    spread_pct = 100 * (hi - lo) / lo
    tot_rej = sum(rej.values())
    rp = {s: 100 * v / tot_rej for s, v in rej.items()}

    # well-populated strata
    strata = defaultdict(list)
    for (pk, k), c in cell.items():
        if c >= MIN_CELL:
            strata[pk].append(k)
    wellpop = {pk: ks for pk, ks in strata.items() if len(ks) >= MIN_KEYS}
    covered = sum(cell[(pk, k)] for pk in wellpop for k in wellpop[pk])
    coverage_pct = 100 * covered / n

    def chk(name, got, exp, tol, unit=""):
        ok = abs(got - exp) <= tol
        print(f"  {'PASS' if ok else 'FAIL'}  {name:34s} got={got:.4f}{unit}  expect={exp}{unit}  (tol {tol})")
        return ok

    print(f"signatures n={n}   total iterations={tot_iters}")
    allok = True
    allok &= chk("overall mean iterations", mean_iters, EXPECT["mean_iters"], 0.01)
    allok &= chk("per-key iter min", lo, EXPECT["spread_lo"], 0.01)
    allok &= chk("per-key iter max", hi, EXPECT["spread_hi"], 0.01)
    allok &= chk("per-key iter spread %", spread_pct, EXPECT["spread_pct"], 0.06, "%")
    allok &= chk("reject ||z||inf %", rp["z"], EXPECT["rej_z_pct"], 0.3, "%")
    allok &= chk("reject ||r0||inf %", rp["r0"], EXPECT["rej_r0_pct"], 0.3, "%")
    allok &= chk("reject ||c.t0||inf %", rp["ct0"], EXPECT["rej_ct0_pct"], 0.05, "%")
    allok &= chk("reject hint %", rp["hint"], EXPECT["rej_hint_pct"], 0.05, "%")
    if len(accept1_norm) == 1 and next(iter(accept1_norm)) == EXPECT["accept1_norm_scan"]:
        print(f"  PASS  accept-iter-1 norm_scan constant       got={next(iter(accept1_norm))}  expect={EXPECT['accept1_norm_scan']}  (n={accept1_n})")
    else:
        allok = False
        print(f"  FAIL  accept-iter-1 norm_scan                got={sorted(accept1_norm)[:5]}  expect single {EXPECT['accept1_norm_scan']}")
    allok &= chk("well-populated strata count", len(wellpop), EXPECT["wellpop_strata"], 1)
    allok &= chk("well-populated coverage %", coverage_pct, EXPECT["wellpop_coverage_pct"], 1.0, "%")

    print(f"\n  reject-stage breakdown: z={rp['z']:.2f}%  r0={rp['r0']:.2f}%  ct0={rp['ct0']:.4f}%  hint={rp['hint']:.4f}%")
    print(f"  well-populated strata ({len(wellpop)}): " + ", ".join(sorted(wellpop, key=lambda p:-sum(cell[(p,k)] for k in wellpop[p]))[:20]))
    print(f"\n=== §4 REPRODUCTION: {'PASS — work profile matches; timing is trustworthy' if allok else 'FAIL — investigate before trusting timing'} ===")


if __name__ == "__main__":
    main()
