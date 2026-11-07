package net.foxsrv.host;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 2.1 — QUEM ESTÁ ESCUTANDO NUMA PORTA: o túnel de um servidor só pode chegar numa porta aberta por um processo DAQUELE
 * servidor (o processo principal ou um filho). Linux: /proc/net/tcp* + /proc/&lt;pid&gt;/fd · Windows: netstat -ano ·
 * macOS: lsof. Assim ninguém usa o túnel para alcançar o painel da host ou outro programa do computador.
 */
final class Ports {
    private Ports() { }

    static boolean ownedBy(int port, ProcessHandle root) {
        Set<Long> tree = new HashSet<>(); tree.add(root.pid());
        try { root.descendants().forEach(h -> tree.add(h.pid())); } catch (Exception ignored) { }
        Set<Long> owners = listeners(port);
        for (Long p : owners) if (tree.contains(p)) return true;
        return false;
    }
    /** os PIDs que escutam (TCP) nesta porta */
    static Set<Long> listeners(int port) {
        Set<Long> out = new HashSet<>();
        try {
            if (Sys.linux()) {
                Set<String> inodes = new HashSet<>();
                for (String f : List.of("/proc/net/tcp", "/proc/net/tcp6")) {
                    Path p = Path.of(f); if (!Files.isReadable(p)) continue;
                    for (String l : Files.readAllLines(p)) {
                        String[] c = l.trim().split("\\s+"); if (c.length < 10 || !"0A".equals(c[3])) continue;   // 0A = LISTEN
                        String loc = c[1]; int i = loc.lastIndexOf(':'); if (i < 0) continue;
                        try { if (Integer.parseInt(loc.substring(i + 1), 16) == port) inodes.add(c[9]); } catch (Exception ignored) { }
                    }
                }
                if (inodes.isEmpty()) return out;
                File[] procs = new File("/proc").listFiles((d, n) -> n.matches("\\d+"));
                if (procs != null) for (File pd : procs) {
                    File[] fds = new File(pd, "fd").listFiles(); if (fds == null) continue;
                    for (File fd : fds) {
                        try { String t = Files.readSymbolicLink(fd.toPath()).toString(); if (t.startsWith("socket:[") && inodes.contains(t.substring(8, t.length() - 1))) { out.add(Long.parseLong(pd.getName())); break; } } catch (Exception ignored) { }
                    }
                }
                return out;
            }
            if (Sys.win()) {
                for (String l : run("netstat", "-ano", "-p", "TCP")) {
                    String[] c = l.trim().split("\\s+"); if (c.length < 5 || !c[3].toUpperCase().startsWith("LISTEN")) continue;
                    String loc = c[1]; int i = loc.lastIndexOf(':');
                    try { if (Integer.parseInt(loc.substring(i + 1)) == port) out.add(Long.parseLong(c[4])); } catch (Exception ignored) { }
                }
                for (String l : run("netstat", "-ano", "-p", "TCPv6")) {
                    String[] c = l.trim().split("\\s+"); if (c.length < 5 || !c[3].toUpperCase().startsWith("LISTEN")) continue;
                    String loc = c[1]; int i = loc.lastIndexOf(':');
                    try { if (Integer.parseInt(loc.substring(i + 1)) == port) out.add(Long.parseLong(c[4])); } catch (Exception ignored) { }
                }
                return out;
            }
            for (String l : run("lsof", "-nP", "-iTCP:" + port, "-sTCP:LISTEN", "-t")) try { out.add(Long.parseLong(l.trim())); } catch (Exception ignored) { }
        } catch (Exception ignored) { }
        return out;
    }
    private static List<String> run(String... cmd) {
        List<String> l = new java.util.ArrayList<>();
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) { String x; while ((x = r.readLine()) != null && l.size() < 20000) l.add(x); }
            p.waitFor(10, TimeUnit.SECONDS);
        } catch (Exception ignored) { }
        return l;
    }
}
