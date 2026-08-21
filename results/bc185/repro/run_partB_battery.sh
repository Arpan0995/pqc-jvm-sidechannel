#!/usr/bin/env bash
# Part B persistence battery: multi-key probes (6/20 keys, 1.84 & 1.85), dudect key-dependence
# (100k/250k, 1.84 & 1.85), and TVLA controls (1.85). Sequential — never concurrent with timing.
set -e
J21="/Users/arpansharma/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home"
REPO="/Users/arpansharma/Desktop/Claude-Projects/pqc-jvm-sidechannel"
W="$REPO/results/bc185/repro"; D="$W/data"
J84="$HOME/bcpin/bcprov-jdk18on-1.84.jar"; J85="$HOME/bcpin/bcprov-jdk18on-1.85.jar"
mkdir -p "$D"

run() {
  name="$1"; jar="$2"; shift 2
  echo "[$(date -u +%FT%TZ)] $name : $*"
  "$J21/bin/java" -Xms1g -Xmx1g -cp "$W/classes:$jar" "$@" > "$D/$name.out" 2>&1 || true
}

run b_probe6_184   "$J84" org.pqcsidechannel.targets.mldsa.MlDsa65MultiKeyProbe 25000 6
run b_probe6_185   "$J85" org.pqcsidechannel.targets.mldsa.MlDsa65MultiKeyProbe 25000 6
run b_probe20_185  "$J85" org.pqcsidechannel.targets.mldsa.MlDsa65MultiKeyProbe 25000 20
run b_keydep_184_100k "$J84" org.pqcsidechannel.Runner --target=mldsa-sign-keydep --n=100000
run b_keydep_185_100k "$J85" org.pqcsidechannel.Runner --target=mldsa-sign-keydep --n=100000
run b_keydep_184_250k "$J84" org.pqcsidechannel.Runner --target=mldsa-sign-keydep --n=250000
run b_keydep_185_250k "$J85" org.pqcsidechannel.Runner --target=mldsa-sign-keydep --n=250000
run b_ctl_pos_185     "$J85" org.pqcsidechannel.Runner --target=positive-control --n=200000
run b_ctl_neg_185     "$J85" org.pqcsidechannel.Runner --target=negative-control --n=200000
run b_ctl_realpos_185 "$J85" org.pqcsidechannel.Runner --target=mldsa-sign-message --n=100000
run b_ctl_realneg_185 "$J85" org.pqcsidechannel.Runner --target=mldsa-sign-fixed --n=100000
echo "[$(date -u +%FT%TZ)] Part B battery complete"