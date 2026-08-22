#!/usr/bin/env bash
# One-way ANOVA of rejection-loop iteration count by key, from a work_*.csv
# produced by WorkProfile (columns: key_id,key_seed,message_id,iterations,...).
# Tests the ML-DSA design property that the rejection probability, and hence
# the iteration distribution, does not depend on the key: under that null the
# per-key mean iteration counts differ only by finite-corpus sampling noise.
# With dfw ~ 1.2M, 19*F is ~ chi-square(19); the 5% critical F is 30.144/19 = 1.587.
# Usage: iteration_anova.sh data/work_discovery.csv
set -euo pipefail
awk -F, 'NR>1{k=$1; it=$4; n[k]++; s[k]+=it; ss[k]+=it*it; N++; S+=it}
END{gm=S/N; ssb=0; ssw=0;
  for(k in n){m=s[k]/n[k]; ssb+=n[k]*(m-gm)^2; ssw+=ss[k]-n[k]*m*m;
    if(min==""||m<min)min=m; if(m>max)max=m}
  K=0; for(k in n)K++; dfb=K-1; dfw=N-K; F=(ssb/dfb)/(ssw/dfw);
  sd=sqrt(ssw/dfw); se=sd/sqrt(N/K);
  printf "keys=%d N=%d grand_mean=%.4f within_sd=%.3f SE_keymean=%.4f\n",K,N,gm,sd,se;
  printf "per-key mean range %.4f..%.4f spread=%.4f (%.2f%%) = %.2f SE\n",min,max,max-min,(max-min)/gm*100,(max-min)/se;
  printf "ANOVA F(%d,%d)=%.3f  (5%% critical ~1.587)  eta2=%.5f%%\n",dfb,dfw,F,ssb/(ssb+ssw)*100}' "$1"
