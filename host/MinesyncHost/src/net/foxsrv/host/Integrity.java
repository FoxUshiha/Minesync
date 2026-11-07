package net.foxsrv.host;

import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 2.0 — INTEGRIDADE dos servidores da central nesta host. Depois de cada operação da central (e quando o servidor
 * desliga) guarda uma "foto" da pasta: tamanho + data de cada arquivo e o sha256 dos .jar (servidor, plugins, mods).
 * A cada 10 min (e ao abrir a host) confere:
 *  · servidor DESLIGADO: qualquer arquivo mudado/apagado/criado fora da central → aviso de violação para a central;
 *  · servidor LIGADO: só os .jar (o próprio servidor mexe no mundo, nos logs…);
 *  · a pasta inteira sumiu → violação grave (a central suspende a host e migra o servidor do backup dela).
 * Quem mexe por fora (o dono do PC) perde reputação na central — tudo vai para o log da host que o admin vê.
 */
final class Integrity {
    private final MinesyncHost H; private final File dir;
    /** id → (caminho → "tamanho:data" ou "sha256") */
    private final Map<String, Map<String, String>> snaps = new ConcurrentHashMap<>();
    private final Map<String, Long> busyUntil = new ConcurrentHashMap<>();
    Integrity(MinesyncHost h, File dir) {
        this.H = h; this.dir = dir; dir.mkdirs();
        File[] fs = dir.listFiles((d, n) -> n.endsWith(".json"));
        if (fs != null) for (File f : fs) { try { Map<String, String> m = Json.G.fromJson(Files.readString(f.toPath(), StandardCharsets.UTF_8), new TypeToken<Map<String, String>>() {}.getType()); if (m != null) snaps.put(f.getName().replace(".json", ""), m); } catch (Exception ignored) { } }
    }
    /** a central mexeu (ou vai mexer) nesta instância: aceita o que mudar e tira a foto de novo daqui a pouco */
    /** janela de uma operação da central (pode ser reduzida só para testes: -Dmsh.busy.ms=…) */
    private static final long BUSY = Math.max(2000L, Long.getLong("msh.busy.ms", 120_000L));
    void touch(Instances.Inst i) { busyUntil.put(i.id, System.currentTimeMillis() + BUSY); H.timer.schedule(() -> snap(i), BUSY * 3 / 4, java.util.concurrent.TimeUnit.MILLISECONDS); }
    void forget(String id) { snaps.remove(id); busyUntil.remove(id); new File(dir, id + ".json").delete(); }
    boolean busy(String id) { Long b = busyUntil.get(id); return b != null && b > System.currentTimeMillis(); }

    synchronized void snap(Instances.Inst i) {
        if (!i.central || !H.inst.all.containsKey(i.id)) return;
        Map<String, String> m = scan(H.inst.dir(i).toPath(), true); if (m == null) return;
        snaps.put(i.id, m);
        try { File t = new File(dir, i.id + ".json.tmp"); Files.writeString(t.toPath(), Json.G.toJson(m), StandardCharsets.UTF_8); Files.move(t.toPath(), new File(dir, i.id + ".json").toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING); Users.restrict(new File(dir, i.id + ".json")); } catch (Exception ignored) { }
    }
    /** caminho → assinatura (null = a pasta não existe) */
    private static Map<String, String> scan(Path root, boolean all) {
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) return null;
        Map<String, String> m = new LinkedHashMap<>();
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes a) { String n = d.getFileName() == null ? "" : d.getFileName().toString(); return a.isSymbolicLink() || n.equals(".npm") || n.equals("node_modules") || n.equals(".pydeps") || n.equals("cache") ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE; }
                @Override public FileVisitResult visitFile(Path f, BasicFileAttributes a) {
                    if (!a.isRegularFile()) return FileVisitResult.CONTINUE;
                    String rel = root.relativize(f).toString().replace('\\', '/'); String n = f.getFileName().toString();
                    if (n.equals("session.lock") || n.endsWith(".msh-tmp") || n.contains(".msh-x") || rel.startsWith("logs/")) return FileVisitResult.CONTINUE;
                    boolean jar = n.endsWith(".jar") && (rel.indexOf('/') < 0 || rel.startsWith("plugins/") && rel.indexOf('/', 8) < 0 || rel.startsWith("mods/") && rel.indexOf('/', 5) < 0);
                    if (jar) m.put(rel, "sha:" + sha(f)); else if (all) m.put(rel, a.size() + ":" + a.lastModifiedTime().toMillis());
                    return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFileFailed(Path f, java.io.IOException e) { return FileVisitResult.CONTINUE; }
            });
        } catch (Exception e) { return m; }
        return m;
    }
    static String sha(Path f) {
        try (InputStream in = Files.newInputStream(f, LinkOption.NOFOLLOW_LINKS)) { MessageDigest md = MessageDigest.getInstance("SHA-256"); byte[] b = new byte[65536]; int n; while ((n = in.read(b)) > 0) md.update(b, 0, n); return HexFormat.of().formatHex(md.digest()); }
        catch (Exception e) { return "?"; }
    }
    /** confere uma instância; devolve o resultado (e avisa a central se for violação) */
    synchronized Map<String, Object> check(Instances.Inst i, boolean report) {
        Map<String, Object> r = new LinkedHashMap<>(); r.put("id", i.id);
        Map<String, String> old = snaps.get(i.id);
        if (old == null) { snap(i); r.put("state", "baseline"); return r; }
        boolean running = i.proc != null && i.proc.isAlive();
        if (!java.nio.file.Files.isDirectory(H.inst.dir(i).toPath(), LinkOption.NOFOLLOW_LINKS)) { r.put("state", "missing"); if (report) H.agent.event("missing", i.id, 500, "a pasta do servidor " + i.id + " sumiu desta host"); return r; }
        // LIGADO: o próprio servidor mexe nos arquivos (mundo, plugins que se atualizam…) — só confere se a pasta existe
        if (running) { r.put("state", "running"); return r; }
        Map<String, String> cur = scan(H.inst.dir(i).toPath(), true);
        if (cur == null) { r.put("state", "missing"); return r; }
        int changed = 0, deleted = 0, added = 0; StringBuilder ex = new StringBuilder();
        for (Map.Entry<String, String> e : old.entrySet()) {
            String now = cur.get(e.getKey());
            if (now == null) { deleted++; if (ex.length() < 200) ex.append(" −").append(e.getKey()); }
            else if (!now.equals(e.getValue())) { changed++; if (ex.length() < 200) ex.append(" ~").append(e.getKey()); }
        }
        if (!running) for (String k : cur.keySet()) if (!old.containsKey(k)) { added++; if (ex.length() < 200) ex.append(" +").append(k); }
        r.put("changed", changed); r.put("deleted", deleted); r.put("added", added); r.put("running", running);
        if (changed + deleted + added == 0) { r.put("state", "ok"); return r; }
        if (busy(i.id)) { r.put("state", "central_op"); snap(i); return r; }
        r.put("state", "tamper");
        int pts = deleted > 0 ? Math.min(500, 300 + deleted * 10) : 100;
        if (report) H.agent.event("tamper", i.id, pts, "arquivos mudaram fora da central (" + changed + " alterados, " + deleted + " apagados, " + added + " novos):" + ex);
        snap(i);   // não pune duas vezes a mesma mudança
        return r;
    }
    /** a cada 10 min */
    void tick() {
        for (Instances.Inst i : H.inst.all.values()) {
            if (!i.central || i.restoring || "installing".equals(i.state)) continue;
            try { check(i, true); } catch (Throwable t) { MinesyncHost.log("integridade " + i.id + ": " + t); }
        }
    }
}
