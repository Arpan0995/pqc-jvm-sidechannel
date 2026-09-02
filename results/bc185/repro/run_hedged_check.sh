#!/usr/bin/env bash
# Hedged-mode timing check for -65: discovery + placement control, stock jar, fresh randomness
# per signature. Time-only (work<->time join is impossible under hedging by design); analysed by
# a per-key ANOVA of signing time. ONE heavy process at a time.
set -e
J21="/Users/arpansharma/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home"
W="/Users/arpansharma/Desktop/Claude-Projects/pqc-jvm-sidechannel/results/bc185/repro"; D="$W/data"
J85="$HOME/bcpin/bcprov-jdk18on-1.85.jar"; CP_STOCK="$W/classes:$J85"
echo "[$(date -u +%FT%TZ)] hedged -65 discovery (keys 100-119, corpus 999)"
"$J21/bin/java" -Xms1g -Xmx1g -cp "$CP_STOCK" MicroTiming --params=65 --hedged \
  --keys=20 --messages=60000 --keyseed0=100 --msgseed=999 \
  --out="$D/time_discovery_65hedged.csv" --runid=RUN-HEDGED > "$D/logs/time_discovery_65hedged.log" 2>&1
echo "[$(date -u +%FT%TZ)] hedged -65 placement control (same keys, allocseed=12345)"
"$J21/bin/java" -Xms1g -Xmx1g -cp "$CP_STOCK" MicroTiming --params=65 --hedged \
  --keys=20 --messages=60000 --keyseed0=100 --msgseed=999 --allocseed=12345 \
  --out="$D/time_rerun_65hedged.csv" --runid=RUN-HEDGED-RERUN > "$D/logs/time_rerun_65hedged.log" 2>&1
echo "[$(date -u +%FT%TZ)] HEDGED CHECK COMPLETE"
for f in time_discovery_65hedged time_rerun_65hedged; do echo "  $f.csv: $(wc -l < "$D/$f.csv")"; done
