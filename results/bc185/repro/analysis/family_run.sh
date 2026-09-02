#!/usr/bin/env bash
set -euo pipefail
P="$1"; W="/Users/arpansharma/Desktop/Claude-Projects/pqc-jvm-sidechannel/results/bc185/repro"; D="$W/data"
cd "$W"
for f in work_discovery time_discovery time_rerun work_replication time_replication; do
  [ -f "$D/${f}_$P.csv" ] || { [ -f "$D/${f}_$P.csv.gz" ] && gunzip -k "$D/${f}_$P.csv.gz"; }
done
echo "### ML-DSA-$P : iteration ANOVA by key (design property) ###"
echo "-- discovery --";    bash analysis/iteration_anova.sh "$D/work_discovery_$P.csv"
echo "-- replication --";  bash analysis/iteration_anova.sh "$D/work_replication_$P.csv"
echo; echo "### ML-DSA-$P : frozen decision tree (discriminate.py) ###"
python3 analysis/discriminate.py --work "$D/work_discovery_$P.csv"   --time "$D/time_discovery_$P.csv"   --label discovery_$P   | tee "$D/discriminate_discovery_$P.txt"   | grep -iE "ANCOVA|present|Cliff|PURE STRATUM|spread|Outcome|STEP" | head -20
echo "-- placement control (time_rerun) --"
python3 analysis/discriminate.py --work "$D/work_discovery_$P.csv"   --time "$D/time_rerun_$P.csv"       --label rerun_$P       | tee "$D/discriminate_rerun_$P.txt"       | grep -iE "ANCOVA|present|Cliff|spread" | head -8
echo "-- replication --"
python3 analysis/discriminate.py --work "$D/work_replication_$P.csv" --time "$D/time_replication_$P.csv" --label replication_$P | tee "$D/discriminate_replication_$P.txt" | grep -iE "ANCOVA|present|Cliff|spread" | head -8
echo; echo "### ML-DSA-$P : rank persistence (placement vs discovery) ###"
python3 analysis/rank_persistence.py --suffix _$P --dir "$D" | tee "$D/rank_persistence_$P.txt" | grep -iE "spread|rank persistence|noise floor|ANOVA|bytes"
