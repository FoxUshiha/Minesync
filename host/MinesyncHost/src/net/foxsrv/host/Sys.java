package net.foxsrv.host;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** sistema: SO, memória, programas no PATH, tamanho de pasta, RAM de um processo */
final class Sys {
    private Sys() {}
    static final String OS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    static boolean win() { return OS.contains("win"); }
    static boolean mac() { return OS.contains("mac"); }
    static boolean linux() { return OS.contains("linux"); }
    static String arch() { String a = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT); return a.contains("aarch64") || a.contains("arm64") ? "arm64" : a.contains("arm") ? "arm" : a.contains("64") ? "amd64" : "386"; }
    static long totalRamMb() {
        try { com.sun.management.OperatingSystemMXBean b = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean(); return b.getTotalMemorySize() / 1048576L; } catch (Throwable t) { return 4096; }
    }
    static long freeRamMb() {
        try { com.sun.management.OperatingSystemMXBean b = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean(); return b.getFreeMemorySize() / 1048576L; } catch (Throwable t) { return 0; }
    }
    static double cpuLoad() {
        try { com.sun.management.OperatingSystemMXBean b = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean(); double v = b.getCpuLoad(); return v < 0 ? 0 : v; } catch (Throwable t) { return 0; }
    }
    static String which(String exe) {
        String p = System.getenv("PATH"); if (p == null) return null;
        for (String d : p.split(File.pathSeparator)) {
            for (String n : win() ? new String[]{ exe + ".exe", exe } : new String[]{ exe }) { File f = new File(d, n); if (f.isFile() && f.canExecute()) return f.getAbsolutePath(); }
        }
        return null;
    }
    static String java() {
        String c = ProcessHandle.current().info().command().orElse(null);
        if (c != null && new File(c).isFile()) return c;
        String h = System.getProperty("java.home"); File f = new File(h, "bin" + File.separator + (win() ? "java.exe" : "java"));
        return f.isFile() ? f.getAbsolutePath() : "java";
    }
    /** 2.0: o modelo do processador (para a lista de hosts da central) */
    static String cpuModel() {
        try {
            if (linux()) for (String l : Files.readAllLines(Path.of("/proc/cpuinfo"))) if (l.startsWith("model name")) return l.substring(l.indexOf(':') + 1).trim();
            if (win()) { String v = System.getenv("PROCESSOR_IDENTIFIER"); if (v != null) return v; }
        } catch (Exception ignored) { }
        return Runtime.getRuntime().availableProcessors() + " cores";
    }
    static String javaHome() { return new File(System.getProperty("java.home")).getAbsolutePath(); }
    /** tamanho de uma pasta (sem seguir atalhos) */
    static long dirSize(Path p) {
        final long[] t = { 0 };
        try (var s = Files.walk(p)) { s.filter(Files::isRegularFile).forEach(f -> { try { t[0] += Files.size(f); } catch (Exception ignored) { } }); } catch (Exception ignored) { }
        return t[0];
    }
    /** RAM física de um processo (Linux /proc; outros: aproximação pelo heap configurado) */
    static long rssMb(long pid) {
        if (!linux()) { Long v = rssOthers().get(pid); return v == null ? -1 : v; }
        try { for (String l : Files.readAllLines(Path.of("/proc/" + pid + "/status"))) if (l.startsWith("VmRSS:")) return Long.parseLong(l.replaceAll("[^0-9]", "")) / 1024L; } catch (Exception ignored) { }
        return -1;
    }

    /** 2.1: Windows/macOS — uma leitura de TODOS os processos (tasklist / ps) a cada 2 s, reaproveitada por todos os servidores */
    private static volatile java.util.Map<Long, Long> rssCache = java.util.Map.of(); private static volatile long rssAt;
    private static synchronized java.util.Map<Long, Long> rssOthers() {
        if (System.currentTimeMillis() - rssAt < 2000) return rssCache;
        java.util.Map<Long, Long> m = new java.util.HashMap<>();
        try {
            Process p = win() ? new ProcessBuilder("tasklist", "/FO", "CSV", "/NH").redirectErrorStream(true).start() : new ProcessBuilder("ps", "-A", "-o", "pid=,rss=").redirectErrorStream(true).start();
            try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                String l;
                while ((l = r.readLine()) != null) {
                    try {
                        if (win()) { String[] c = l.split("\",\""); if (c.length >= 5) { long pid = Long.parseLong(c[1].replaceAll("[^0-9]", "")); long kb = Long.parseLong(c[4].replaceAll("[^0-9]", "")); m.put(pid, kb / 1024); } }
                        else { String[] c = l.trim().split("\\s+"); if (c.length >= 2) m.put(Long.parseLong(c[0]), Long.parseLong(c[1]) / 1024); }
                    } catch (Exception ignored) { }
                }
            }
            p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception ignored) { }
        rssCache = m; rssAt = System.currentTimeMillis(); return m;
    }
}
