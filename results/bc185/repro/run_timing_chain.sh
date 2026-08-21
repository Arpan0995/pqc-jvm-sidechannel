#!/usr/bin/env bash
# Sequential timing + replication chain. ONE heavy signing process at a time (concurrent signing
# workloads contend for cores and corrupt timing). Run only after the §4 work-profile gate passes.
set -e
J21="/Users/arpansharma/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home"
REPO="/Users/arpansharma/Desktop/Claude-Projects/pqc-jvm-sidechannel"
W="$REPO/results/bc185/repro"; D="$W/data"
J85="$HOME/bcpin/bcprov-jdk18on-1.85.jar"
CP_INST="$W/classes:$W/inst-classes:$J85"; CP_STOCK="$W/classes:$J85"
mkdir -p "$D/logs"

echo "[$(date -u +%FT%TZ)] 2) RUN-C-TIME discovery (authoritative, stock)"
"$J21/bin/java" -Xms1g -Xmx1g -cp "$CP_STOCK" MicroTiming \
  --keys=20 --messages=60000 --keyseed0=100 --msgseed=999 \
  --out="$D/time_discovery.csv" --runid=RUN-C-TIME > "$D/logs/time_discovery.log" 2>&1

echo "[$(date -u +%FT%TZ)] 3) placement control (same keys, shuffled alloc, fresh JVM)"
"$J21/bin/java" -Xms1g -Xmx1g -cp "$CP_STOCK" MicroTiming \
  --keys=20 --messages=60000 --keyseed0=100 --msgseed=999 --allocseed=12345 \
  --out="$D/time_rerun.csv" --runid=RUN-C-TIME-RERUN > "$D/logs/time_rerun.log" 2>&1

echo "[$(date -u +%FT%TZ)] 4) replication work profile (fresh keys 200-219, corpus 31337, instrumented)"
"$J21/bin/java" -Xms1g -Xmx1g -cp "$CP_INST" WorkProfile \
  --keys=20 --messages=60000 --keyseed0=200 --msgseed=31337 \
  --out="$D/work_replication.csv" > "$D/logs/work_replication.log" 2>&1

echo "[$(date -u +%FT%TZ)] 5) replication timing (fresh keys 200-219, corpus 31337, stock)"
"$J21/bin/java" -Xms1g -Xmx1g -cp "$CP_STOCK" MicroTiming \
  --keys=20 --messages=60000 --keyseed0=200 --msgseed=31337 \
  --out="$D/time_replication.csv" --runid=RUN-C-TIME-REP > "$D/logs/time_replication.log" 2>&1

echo "[$(date -u +%FT%TZ)] timing chain complete"
for f in time_discovery time_rerun work_replication time_replication; do
  echo "  $f.csv rows: $(wc -l < "$D/$f.csv")"
done