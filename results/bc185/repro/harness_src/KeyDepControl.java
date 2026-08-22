import org.pqcsidechannel.env.Environment;
import org.pqcsidechannel.measure.Measurement;
import org.pqcsidechannel.measure.TimerCalibration;
import org.pqcsidechannel.stats.LeakageReport;
import org.pqcsidechannel.targets.mldsa.MlDsa65SignKeyDependenceTarget;
import java.util.Locale;

/**
 * Key-dependence controls: the same two-class pipeline as Runner's mldsa-sign-keydep, with the
 * two key seeds chosen on the command line. Used for A-vs-A (same key in both classes: any LEAKY
 * verdict is harness asymmetry), B-vs-A (swapped allocation order: does the slower class follow
 * the key or the slot?), and A-vs-B (repeat of the battery run).
 *   --seedA=<long> --seedB=<long> --n=<measurements>
 */
public final class KeyDepControl {
    public static void main(String[] args) {
        long seedA = 0x0D5A65L, seedB = 0x0D5A66L, n = 250_000L;
        for (String a : args) {
            if (a.startsWith("--seedA=")) seedA = Long.parseLong(a.substring(8));
            else if (a.startsWith("--seedB=")) seedB = Long.parseLong(a.substring(8));
            else if (a.startsWith("--n=")) n = Long.parseLong(a.substring(4).replace("_", ""));
            else throw new IllegalArgumentException("unknown arg " + a);
        }
        System.out.println("=== keydep control ===");
        System.out.print(Environment.capture().render());
        System.out.println("  " + TimerCalibration.measure(2_000_000).render());
        System.out.printf(Locale.ROOT, "  target : keydep-control seedA=%d seedB=%d (n=%d)%n%n", seedA, seedB, n);
        Measurement m = new Measurement(new MlDsa65SignKeyDependenceTarget(seedA, seedB), Measurement.Config.of(n));
        Measurement.Samples s = m.run();
        System.out.print(LeakageReport.of(s.targetName(), s.class0(), s.class1()).render());
        if (s.sinkValue() == 42) System.out.println("(sink=" + s.sinkValue() + ")");
    }
}
