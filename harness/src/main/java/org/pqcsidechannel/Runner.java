package org.pqcsidechannel;

import org.pqcsidechannel.env.Environment;
import org.pqcsidechannel.measure.Measurement;
import org.pqcsidechannel.measure.MeasurementTarget;
import org.pqcsidechannel.measure.TimerCalibration;
import org.pqcsidechannel.stats.LeakageReport;
import org.pqcsidechannel.targets.controls.ConstantTimeCompareTarget;
import org.pqcsidechannel.targets.controls.EarlyExitCompareTarget;

import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * CLI entry point. Runs a chosen target through the measurement + leakage pipeline and prints the
 * environment, timer calibration, and verdict.
 *
 * <pre>
 *   --target=positive-control | negative-control   (default: positive-control)
 *   --n=&lt;measurements&gt;                          (default: 2_000_000)
 * </pre>
 *
 * The JIT mode and GC are controlled by JVM flags passed on the command line (e.g. {@code -Xint},
 * {@code -XX:TieredStopAtLevel=1}, {@code -XX:+UseEpsilonGC}); they are recorded via {@link Environment}.
 */
public final class Runner {

    private static final Map<String, Supplier<MeasurementTarget>> TARGETS = Map.of(
            "positive-control", EarlyExitCompareTarget::new,
            "negative-control", ConstantTimeCompareTarget::new);

    public static void main(String[] args) {
        String targetName = "positive-control";
        long n = 2_000_000L;

        for (String arg : args) {
            if (arg.startsWith("--target=")) {
                targetName = arg.substring("--target=".length());
            } else if (arg.startsWith("--n=")) {
                n = Long.parseLong(arg.substring("--n=".length()).replace("_", ""));
            } else {
                System.err.println("Unknown argument: " + arg);
                System.exit(2);
            }
        }

        Supplier<MeasurementTarget> factory = TARGETS.get(targetName);
        if (factory == null) {
            System.err.println("Unknown target: " + targetName + ". Known: " + TARGETS.keySet());
            System.exit(2);
        }

        System.out.println("=== pqc-jvm-sidechannel run ===");
        System.out.print(Environment.capture().render());
        System.out.println("  " + TimerCalibration.measure(2_000_000).render());
        System.out.printf(Locale.ROOT, "  target : %s  (n=%d)%n%n", targetName, n);

        Measurement measurement = new Measurement(factory.get(), Measurement.Config.of(n));
        Measurement.Samples samples = measurement.run();

        LeakageReport report = LeakageReport.of(samples.targetName(), samples.class0(), samples.class1());
        System.out.print(report.render());

        // Reference the sink value so the whole measured computation cannot be optimised away.
        if (samples.sinkValue() == 42) {
            System.out.println("(sink=" + samples.sinkValue() + ")");
        }
    }

    private Runner() {
    }
}
