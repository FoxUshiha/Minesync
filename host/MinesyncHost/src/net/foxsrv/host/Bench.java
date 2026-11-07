package net.foxsrv.host;

import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 2.1 — A MÁQUINA: descobre sozinho o que o PC tem e mede (em segundo plano, sem travar nada):
 * sistema operacional, processador (modelo, GHz máximo, núcleos/threads, nota de um núcleo), RAM (total e velocidade),
 * disco (tipo SSD/HDD/NVMe, espaço, velocidade de escrita e leitura) e a internet até a central (download, upload, ping).
 * O resultado fica em servers/.msh/bench.json e vai para a central (aba Máquinas / Admin).
 */
final class Bench {
    private final MinesyncHost H; private final File file;
    private volatile Map<String, Object> last = new LinkedHashMap<>();
    private volatile boolean running; volatile long at;
    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(10)).build();
    Bench(MinesyncHost h, File file) {
        this.H = h; this.file = file;
        try { if (file.isFile()) { JsonObject o = Json.obj(Files.readString(file.toPath(), StandardCharsets.UTF_8)); last = Json.toMap(o); at = Json.n(o, "at", 0); } } catch (Exception ignored) { }
        if (last.isEmpty()) last = quick();
    }
    Map<String, Object> view() { Map<String, Object> m = new LinkedHashMap<>(last); m.put("running", running); return m; }
    boolean stale() { return System.currentTimeMillis() - at > 12 * 3600_000L; }

    /** o que dá para saber na hora (sem medir) */
    Map<String, Object> quick() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("os", osName()); m.put("arch", Sys.arch()); m.put("java", System.getProperty("java.version"));
        m.put("cpu_model", Sys.cpuModel()); m.put("threads", Runtime.getRuntime().availableProcessors()); m.put("cores", physicalCores());
        m.put("cpu_ghz", maxGhz()); m.put("ram_mb", Sys.totalRamMb()); m.put("ram_speed_mhz", ramSpeed()); m.put("ram_type", ramType());
        File d = H.base; m.put("disk_total_gb", d.getTotalSpace() / 1073741824L); m.put("disk_free_gb", d.getUsableSpace() / 1073741824L); m.put("disk_type", diskType());
        m.put("hostname", hostName());
        return m;
    }
    /** mede tudo (uns 20–40 s, em segundo plano) */
    void runAsync(Runnable after) {
        if (running) return; running = true;
        H.pool.execute(() -> {
            try {
                Map<String, Object> m = quick();
                m.put("cpu_score", cpuScore());
                long[] dk = diskSpeed(); m.put("disk_write_mbs", dk[0]); m.put("disk_read_mbs", dk[1]);
                Map<String, Object> net = netSpeed(); m.putAll(net);
                m.put("at", System.currentTimeMillis());
                last = m; at = System.currentTimeMillis();
                try { Files.writeString(file.toPath(), Json.PRETTY.toJson(m), StandardCharsets.UTF_8); Users.restrict(file); } catch (Exception ignored) { }
                MinesyncHost.log("máquina medida: " + m.get("cpu_model") + " · " + m.get("cpu_ghz") + " GHz · " + m.get("ram_mb") + " MB · disco " + m.get("disk_type") + " " + m.get("disk_write_mbs") + "/" + m.get("disk_read_mbs") + " MB/s · internet ↓" + m.get("down_mbps") + " ↑" + m.get("up_mbps") + " Mbps");
            } catch (Throwable t) { MinesyncHost.log("medição: " + t); }
            finally { running = false; if (after != null) try { after.run(); } catch (Throwable ignored) { } }
        });
    }

    // ------------------------------------------------------------------ medições
    /** nota de um núcleo: quantas voltas de uma conta fixa em 300 ms (comparável entre as hosts da rede) */
    static long cpuScore() {
        long best = 0;
        for (int r = 0; r < 3; r++) {
            long end = System.nanoTime() + 300_000_000L, n = 0; double x = 1.0001;
            while (System.nanoTime() < end) { for (int k = 0; k < 2000; k++) { x = x * 1.0000001 + 0.0000001; if (x > 2) x = 1.0001; } n++; }
            if (x < 0) n++;   // não deixa o JIT jogar a conta fora
            best = Math.max(best, n);
        }
        return best;
    }
    /** escrita e leitura sequencial de 128 MB na pasta da host (com fsync), em MB/s */
    long[] diskSpeed() {
        File f = new File(H.state, "bench.tmp"); long w = 0, r = 0; int mb = 128;
        try {
            ByteBuffer b = ByteBuffer.allocateDirect(1 << 20); for (int i = 0; i < b.capacity(); i++) b.put((byte) (i * 31));
            long t0 = System.nanoTime();
            try (FileChannel c = FileChannel.open(f.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                for (int i = 0; i < mb; i++) { b.rewind(); while (b.hasRemaining()) c.write(b); }
                c.force(true);
            }
            double s = (System.nanoTime() - t0) / 1e9; w = Math.round(mb / Math.max(s, 0.001));
            t0 = System.nanoTime();
            try (RandomAccessFile raf = new RandomAccessFile(f, "r"); FileChannel c = raf.getChannel()) { ByteBuffer rb = ByteBuffer.allocateDirect(1 << 20); while (c.read(rb) > 0) rb.clear(); }
            s = (System.nanoTime() - t0) / 1e9; r = Math.round(mb / Math.max(s, 0.001));
        } catch (Exception ignored) { }
        finally { f.delete(); }
        return new long[]{ w, r };
    }
    /** internet até a central: baixa e envia alguns MB (o endereço /botcraft/hn/speed da central) */
    Map<String, Object> netSpeed() {
        Map<String, Object> m = new LinkedHashMap<>(); m.put("down_mbps", 0); m.put("up_mbps", 0); m.put("ping_ms", -1);
        String base = H.conf.central();
        try {
            long t0 = System.nanoTime();
            HttpResponse<Void> p = http.send(HttpRequest.newBuilder(URI.create(base + "/botcraft/hn/speed?mb=0")).timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.discarding());
            if (p.statusCode() == 200) m.put("ping_ms", Math.round((System.nanoTime() - t0) / 1e6));
            t0 = System.nanoTime(); long n = 0;
            HttpResponse<InputStream> d = http.send(HttpRequest.newBuilder(URI.create(base + "/botcraft/hn/speed?mb=8")).timeout(Duration.ofSeconds(40)).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream in = d.body()) { byte[] b = new byte[1 << 16]; int k; while ((k = in.read(b)) > 0) n += k; }
            double s = (System.nanoTime() - t0) / 1e9; if (n > 0 && d.statusCode() == 200) m.put("down_mbps", Math.round(n * 8 / 1e6 / Math.max(s, 0.001) * 10) / 10.0);
            byte[] up = new byte[4 << 20]; new java.util.Random(1).nextBytes(up);
            t0 = System.nanoTime();
            HttpResponse<String> u = http.send(HttpRequest.newBuilder(URI.create(base + "/botcraft/hn/speed")).timeout(Duration.ofSeconds(40)).POST(HttpRequest.BodyPublishers.ofByteArray(up)).build(), HttpResponse.BodyHandlers.ofString());
            s = (System.nanoTime() - t0) / 1e9; if (u.statusCode() == 200) m.put("up_mbps", Math.round(up.length * 8 / 1e6 / Math.max(s, 0.001) * 10) / 10.0);
        } catch (Exception ignored) { }
        return m;
    }

    // ------------------------------------------------------------------ o que o sistema informa
    static String osName() {
        String n = System.getProperty("os.name", "?"), v = System.getProperty("os.version", "");
        try {
            if (Sys.linux()) { for (String l : Files.readAllLines(Path.of("/etc/os-release"))) if (l.startsWith("PRETTY_NAME=")) return l.substring(12).replace("\"", "") + " (Linux " + v + ")"; }
            if (Sys.mac()) { String pv = run("sw_vers", "-productVersion"); if (pv != null) return "macOS " + pv.trim(); }
            if (Sys.win()) { String c = run("cmd", "/c", "ver"); if (c != null && !c.isBlank()) return n + " (" + c.trim().replaceAll(".*\\[(.*)\\].*", "$1") + ")"; }
        } catch (Exception ignored) { }
        return n + " " + v;
    }
    static String hostName() { try { return java.net.InetAddress.getLocalHost().getHostName(); } catch (Exception e) { String h = System.getenv("COMPUTERNAME"); return h == null ? "?" : h; } }
    static int physicalCores() {
        try {
            if (Sys.linux()) { java.util.Set<String> s = new java.util.HashSet<>(); String phys = "0"; for (String l : Files.readAllLines(Path.of("/proc/cpuinfo"))) { if (l.startsWith("physical id")) phys = l.split(":")[1].trim(); if (l.startsWith("core id")) s.add(phys + ":" + l.split(":")[1].trim()); } if (!s.isEmpty()) return s.size(); }
            if (Sys.win()) { String o = ps("(Get-CimInstance Win32_Processor | Measure-Object -Property NumberOfCores -Sum).Sum"); if (o != null && o.trim().matches("\\d+")) return Integer.parseInt(o.trim()); }
            if (Sys.mac()) { String o = run("sysctl", "-n", "hw.physicalcpu"); if (o != null && o.trim().matches("\\d+")) return Integer.parseInt(o.trim()); }
        } catch (Exception ignored) { }
        return Runtime.getRuntime().availableProcessors();
    }
    static double maxGhz() {
        try {
            if (Sys.linux()) {
                Path p = Path.of("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq");
                if (Files.isReadable(p)) return round(Long.parseLong(Files.readString(p).trim()) / 1e6);
                double best = 0; for (String l : Files.readAllLines(Path.of("/proc/cpuinfo"))) { if (l.startsWith("cpu MHz")) best = Math.max(best, Double.parseDouble(l.split(":")[1].trim()) / 1000); if (l.startsWith("model name") && l.matches(".*@\\s*([0-9.]+)GHz.*")) best = Math.max(best, Double.parseDouble(l.replaceAll(".*@\\s*([0-9.]+)GHz.*", "$1"))); }
                if (best > 0) return round(best);
            }
            if (Sys.win()) { String o = ps("(Get-CimInstance Win32_Processor | Select-Object -First 1).MaxClockSpeed"); if (o != null && o.trim().matches("\\d+")) return round(Integer.parseInt(o.trim()) / 1000.0); }
            if (Sys.mac()) { String o = run("sysctl", "-n", "hw.cpufrequency_max"); if (o != null && o.trim().matches("\\d+")) return round(Long.parseLong(o.trim()) / 1e9); }
        } catch (Exception ignored) { }
        String m = Sys.cpuModel(); if (m.matches(".*@\\s*([0-9.]+)\\s*GHz.*")) try { return Double.parseDouble(m.replaceAll(".*@\\s*([0-9.]+)\\s*GHz.*", "$1")); } catch (Exception ignored) { }
        return 0;
    }
    static long ramSpeed() {
        try {
            if (Sys.win()) { String o = ps("(Get-CimInstance Win32_PhysicalMemory | Select-Object -First 1).ConfiguredClockSpeed"); if (o != null && o.trim().matches("\\d+")) return Long.parseLong(o.trim()); }
            if (Sys.linux()) { String o = run("dmidecode", "-t", "memory"); if (o != null) for (String l : o.split("\n")) { String t = l.trim(); if ((t.startsWith("Configured Memory Speed:") || t.startsWith("Speed:")) && t.matches(".*\\d+.*")) return Long.parseLong(t.replaceAll("[^0-9]", "")); } }
            if (Sys.mac()) { String o = run("system_profiler", "SPMemoryDataType"); if (o != null) for (String l : o.split("\n")) if (l.trim().startsWith("Speed:") && l.matches(".*\\d+.*")) return Long.parseLong(l.replaceAll("[^0-9]", "")); }
        } catch (Exception ignored) { }
        return 0;
    }
    static String ramType() {
        try {
            if (Sys.win()) { String o = ps("(Get-CimInstance Win32_PhysicalMemory | Select-Object -First 1).SMBIOSMemoryType"); if (o != null) switch (o.trim()) { case "26": return "DDR4"; case "34": return "DDR5"; case "24": return "DDR3"; default: return ""; } }
            if (Sys.linux()) { String o = run("dmidecode", "-t", "memory"); if (o != null) for (String l : o.split("\n")) { String t = l.trim(); if (t.startsWith("Type: DDR")) return t.substring(6).trim(); } }
        } catch (Exception ignored) { }
        return "";
    }
    /** SSD / HDD / NVMe do disco onde a host está */
    String diskType() {
        try {
            if (Sys.linux()) {
                String dev = run("df", "--output=source", H.base.getAbsolutePath()); if (dev == null) return "";
                String[] ls = dev.trim().split("\n"); String d = ls[ls.length - 1].trim().replace("/dev/", "");
                if (d.startsWith("nvme")) return "NVMe";
                String blk = d.replaceAll("[0-9]+$", "").replaceAll("p$", "");
                Path rot = Path.of("/sys/block/" + blk + "/queue/rotational");
                if (Files.isReadable(rot)) return "0".equals(Files.readString(rot).trim()) ? "SSD" : "HDD";
                return d.startsWith("overlay") || d.startsWith("tmpfs") ? "virtual" : "";
            }
            if (Sys.win()) {
                String letter = H.base.getAbsolutePath().substring(0, 1);
                String o = ps("$p=(Get-Partition -DriveLetter " + letter + " | Get-Disk | Get-PhysicalDisk); \"$($p.MediaType) $($p.BusType)\"");
                if (o != null) { String t = o.trim(); if (t.contains("NVMe")) return "NVMe"; if (t.startsWith("SSD")) return "SSD"; if (t.startsWith("HDD")) return "HDD"; }
            }
            if (Sys.mac()) return "SSD";
        } catch (Exception ignored) { }
        return "";
    }
    private static double round(double v) { return Math.round(v * 100) / 100.0; }
    static String ps(String cmd) { return run("powershell", "-NoProfile", "-NonInteractive", "-Command", cmd); }
    static String run(String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) { String l; while ((l = r.readLine()) != null && sb.length() < 200_000) sb.append(l).append('\n'); }
            if (!p.waitFor(15, TimeUnit.SECONDS)) { p.destroyForcibly(); return null; }
            return p.exitValue() == 0 ? sb.toString() : null;
        } catch (Exception e) { return null; }
    }
    @SuppressWarnings("unused") private static List<String> lines(String s) { List<String> l = new ArrayList<>(); if (s != null) for (String x : s.split("\n")) if (!x.isBlank()) l.add(x.trim()); return l; }
    @SuppressWarnings("unused") private static String lc(String s) { return s == null ? "" : s.toLowerCase(Locale.ROOT); }
}
