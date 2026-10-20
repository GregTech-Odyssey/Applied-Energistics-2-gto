package dev.emi.emi.runtime;

import com.google.common.collect.Lists;
import dev.emi.emi.runtime.EmiLog;

import java.util.List;

public class EmiReloadLog {
    private static List<String> pendingWarnings = Lists.newArrayList();
    public static List<String> warnings = List.of();
    private static int pendingWarningCount;
    public static int warningCount;

    public static void bake() {
        warnings = pendingWarnings;
        warningCount = pendingWarningCount;
        pendingWarnings = Lists.newArrayList();
        pendingWarningCount = 0;
    }

    public static void warn(String warning) {
        pendingWarnings.add(warning);
        EmiLog.error(warning);
        pendingWarningCount++;
    }

    public static void warn(String warning, Throwable t) {
        pendingWarnings.add(warning);
        EmiLog.error(warning, t);
        pendingWarningCount++;
    }

    public static void info(String info) {
        pendingWarnings.add(info);
        EmiLog.info(info);
    }
}
