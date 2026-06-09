package org.huntercore.optimization;

import com.mojang.logging.LogUtils;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.huntercore.config.HunterPreferences;
import org.slf4j.Logger;

public final class HunterCoreOptimizer {
    private static final Logger LOGGER = LogUtils.getClassLogger();
    private static final int CPU = Math.max(1, Runtime.getRuntime().availableProcessors());
    private static volatile OptimizationSnapshot lastSnapshot = OptimizationSnapshot.empty();

    private HunterCoreOptimizer() {
    }

    public static synchronized OptimizationSnapshot applyStartupDefaults() {
        if (Boolean.getBoolean("huntercore.optimizations.disabled")) {
            lastSnapshot = new OptimizationSnapshot(false, CPU, Map.of(), "disabled by huntercore.optimizations.disabled");
            return lastSnapshot;
        }

        final Map<String, String> applied = new LinkedHashMap<>();
        setIfMissing("Paper.WorkerThreadCount", Integer.toString(workerThreads()), applied);
        setIfMissing("DivineMC.WorkerThreadCount", Integer.toString(workerThreads()), applied);
        setIfMissing("io.netty.eventLoopThreads", Integer.toString(nettyThreads()), applied);
        setIfMissing("io.netty.allocator.numDirectArenas", Integer.toString(nettyArenaCount()), applied);
        setIfMissing("io.netty.allocator.numHeapArenas", Integer.toString(nettyArenaCount()), applied);
        setIfMissing("java.util.concurrent.ForkJoinPool.common.parallelism", Integer.toString(commonPoolParallelism()), applied);

        lastSnapshot = new OptimizationSnapshot(true, CPU, applied, "startup defaults");
        logSnapshot(lastSnapshot);
        return lastSnapshot;
    }

    public static synchronized OptimizationSnapshot applyPreferences(final HunterPreferences preferences) {
        if (!preferences.booleanValue("optimizations.cpu.enabled", true)) {
            lastSnapshot = new OptimizationSnapshot(false, CPU, Map.of(), "disabled in preferences.yml");
            return lastSnapshot;
        }

        final boolean preferExisting = preferences.booleanValue("optimizations.cpu.prefer-existing-jvm-flags", true);
        final Map<String, String> applied = new LinkedHashMap<>();
        setProperty("Paper.WorkerThreadCount", resolveThreadSetting(preferences.stringValue("optimizations.cpu.paper-worker-threads", "auto"), workerThreads()), preferExisting, applied);
        setProperty("DivineMC.WorkerThreadCount", resolveThreadSetting(preferences.stringValue("optimizations.cpu.divine-worker-threads", "auto"), workerThreads()), preferExisting, applied);
        setProperty("io.netty.eventLoopThreads", resolveThreadSetting(preferences.stringValue("optimizations.cpu.netty-io-threads", "auto"), nettyThreads()), preferExisting, applied);
        setProperty("java.util.concurrent.ForkJoinPool.common.parallelism", resolveThreadSetting(preferences.stringValue("optimizations.cpu.common-pool-parallelism", "auto"), commonPoolParallelism()), preferExisting, applied);

        lastSnapshot = new OptimizationSnapshot(true, CPU, applied, preferExisting ? "preferences, existing JVM flags preserved" : "preferences, JVM flags overwritten");
        logSnapshot(lastSnapshot);
        return lastSnapshot;
    }

    public static OptimizationSnapshot lastSnapshot() {
        return lastSnapshot;
    }

    private static void setIfMissing(final String key, final String value, final Map<String, String> applied) {
        setProperty(key, value, true, applied);
    }

    private static void setProperty(final String key, final String value, final boolean preferExisting, final Map<String, String> applied) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (preferExisting && System.getProperty(key) != null) {
            return;
        }
        System.setProperty(key, value);
        applied.put(key, value);
    }

    private static String resolveThreadSetting(final String raw, final int autoValue) {
        if (raw == null || raw.isBlank() || raw.equalsIgnoreCase("auto")) {
            return Integer.toString(autoValue);
        }
        try {
            final int value = Integer.parseInt(raw.trim());
            return Integer.toString(Math.max(1, Math.min(value, Math.max(4, CPU * 2))));
        } catch (final NumberFormatException ex) {
            LOGGER.warn("Invalid HunterCore CPU optimization value '{}', using auto value {}", raw, autoValue);
            return Integer.toString(autoValue);
        }
    }

    private static int workerThreads() {
        if (CPU <= 2) {
            return 1;
        }
        return clamp((int) Math.round(CPU * 0.50D), 2, 16);
    }

    private static int nettyThreads() {
        if (CPU <= 2) {
            return 2;
        }
        return clamp((int) Math.round(CPU * 0.40D), 2, 16);
    }

    private static int nettyArenaCount() {
        return clamp(CPU, 2, 16);
    }

    private static int commonPoolParallelism() {
        return clamp(CPU - 1, 1, 32);
    }

    private static int clamp(final int value, final int min, final int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void logSnapshot(final OptimizationSnapshot snapshot) {
        if (!snapshot.enabled() || snapshot.appliedProperties().isEmpty()) {
            LOGGER.info("HunterCore CPU optimizer: {} on {} CPU threads", snapshot.reason(), snapshot.cpuThreads());
            return;
        }
        LOGGER.info("HunterCore CPU optimizer applied {} setting(s) on {} CPU threads: {}",
            snapshot.appliedProperties().size(),
            snapshot.cpuThreads(),
            snapshot.appliedProperties());
    }

    public record OptimizationSnapshot(boolean enabled, int cpuThreads, Map<String, String> appliedProperties, String reason) {
        public static OptimizationSnapshot empty() {
            return new OptimizationSnapshot(false, CPU, Map.of(), "not applied");
        }

        public String summary() {
            if (!this.enabled) {
                return "disabled (" + this.reason + ")";
            }
            if (this.appliedProperties.isEmpty()) {
                return "enabled, no new JVM properties applied (" + this.reason + ")";
            }
            return "enabled, " + this.appliedProperties.size() + " JVM properties applied (" + this.reason.toLowerCase(Locale.ROOT) + ")";
        }
    }
}
