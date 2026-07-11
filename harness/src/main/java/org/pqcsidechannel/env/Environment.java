package org.pqcsidechannel.env;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Locale;

/**
 * Captures the execution environment that a timing result depends on. Every results file embeds this
 * so a run is interpretable and reproducible: the JDK build, the JVM flags (which encode the JIT and
 * GC configuration central to RQ2), the OS/CPU, and the heap size.
 */
public record Environment(String javaVersion,
                          String javaVendor,
                          String vmName,
                          String osName,
                          String osArch,
                          String osVersion,
                          int availableProcessors,
                          long maxHeapBytes,
                          List<String> jvmArgs) {

    public static Environment capture() {
        Runtime rt = Runtime.getRuntime();
        List<String> args = ManagementFactory.getRuntimeMXBean().getInputArguments();
        return new Environment(
                System.getProperty("java.version"),
                System.getProperty("java.vendor"),
                System.getProperty("java.vm.name"),
                System.getProperty("os.name"),
                System.getProperty("os.arch"),
                System.getProperty("os.version"),
                rt.availableProcessors(),
                rt.maxMemory(),
                args);
    }

    public String render() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "  jdk    : %s (%s) %s%n", javaVersion, javaVendor, vmName));
        sb.append(String.format(Locale.ROOT, "  os     : %s %s (%s)%n", osName, osVersion, osArch));
        sb.append(String.format(Locale.ROOT, "  cpu    : %d logical processors%n", availableProcessors));
        sb.append(String.format(Locale.ROOT, "  heap   : max %d MiB%n", maxHeapBytes / (1024 * 1024)));
        sb.append(String.format(Locale.ROOT, "  jvmArgs: %s%n",
                jvmArgs.isEmpty() ? "(none)" : String.join(" ", jvmArgs)));
        return sb.toString();
    }
}
