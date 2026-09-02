#!/usr/bin/env bash
# Frozen-protocol battery for extra ML-DSA parameter sets, matching the -65 chain exactly.
# ONE heavy signing process at a time. Deterministic signing. Writes _<params>-suffixed files.
set -e
J21="/Users/arpansharma/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home"
W="/Users/arpansharma/Desktop/Claude-Projects/pqc-jvm-sidechannel/results/bc185/repro"; D="$W/data"
J85="$HOME/bcpin/bcprov-jdk18on-1.85.jar"
CP_INST="$W/classes:$W/inst-classes:$J85"; CP_STOCK="$W/classes:$J85"
mkdir -p "$D/logs"
for P in "$@"; do
  echo "[$(date -u +%FT%TZ)] ===== ML-DSA-$P battery ====="
  echo "[$(date -u +%FT%TZ)] 1/5 work_discovery (instrumented, keys 100-119, corpus 999)"
  "$J21/bin/java" -Xms1g -Xmx1g -cp "$CP_INST" WorkProfile --params=$P \
    --keys=20 --messages=60000 --keyseed0=100 --msgseed=999 \
    --out="$D/work_discovery_$P.csv" > "$D/logs/work_discovery_$P.log" 2>&1
  echo "[$(date -u +%FT%TZ)] 2/5 time_discovery (stock, keys 100-119, corpus 999)"
  "$J21/bin/java" -Xms1g -Xmx1g -cp "$CP_STOCK" MicroTiming --params=$P \
    --keys=20 --messages=60000 --keyseed0=100 --msgseed=999 \
    --out="$D/time_discovery_$P.csv" --runid=RUN-C-TIME-$P > "$D/logs/time_discovery_$P.log" 2>&1
  echo "[$(date -u +%FT%TZ)] 3/5 time_rerun placement control (same keys, allocseed=12345, fresh JVM)"
  "$J21/bin/java" -Xms1g -Xmx1g -cp "$CP_STOCK" MicroTiming --params=$P \
    --keys=20 --messages=60000 --keyseed0=100 --msgseed=999 --allocseed=12345 \
    --out="$D/time_rerun_$P.csv" --runid=RUN-C-TIME-RERUN-$P > "$D/logs/time_rerun_$P.log" 2>&1
  echo "[$(date -u +%FT%TZ)] 4/5 work_replication (instrumented, keys 200-219, corpus 31337)"
  "$J21/bin/java" -Xms1g -Xmx1g -cp "$CP_INST" WorkProfile --params=$P \
    --keys=20 --messages=60000 --keyseed0=200 --msgseed=31337 \
    --out="$D/work_replication_$P.csv" > "$D/logs/work_replication_$P.log" 2>&1
  echo "[$(date -u +%FT%TZ)] 5/5 time_replication (stock, keys 200-219, corpus 31337)"
  "$J21/bin/java" -Xms1g -Xmx1g -cp "$CP_STOCK" MicroTiming --params=$P \
    --keys=20 --messages=60000 --keyseed0=200 --msgseed=31337 \
    --out="$D/time_replication_$P.csv" --runid=RUN-C-TIME-REP-$P > "$D/logs/time_replication_$P.log" 2>&1
  echo "[$(date -u +%FT%TZ)] ML-DSA-$P done; rows:"
  for f in work_discovery time_discovery time_rerun work_replication time_replication; do
    echo "    ${f}_$P.csv: $(wc -l < "$D/${f}_$P.csv")"
  done
done
echo "[$(date -u +%FT%TZ)] FAMILY BATTERY COMPLETE"
