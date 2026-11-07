package net.foxsrv.host;

import com.google.gson.reflect.TypeToken;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 1.2 — BACKUPS dos servidores + TAREFAS (backup, restauração, compactar, descompactar) com progresso.
 * <ul>
 *   <li>Guardados FORA da pasta do servidor ({@code ./backups/<servidor>/<id>.zip}): o servidor (e plugins) não mexem neles.</li>
 *   <li>Limites POR USUÁRIO (somando todos os servidores dele): quantidade e MB — padrão no config.yml (backups.max_count /
 *       backups.max_mb) e por usuário na aba Usuários. Também um limite total da host (backups.max_total_gb).</li>
 *   <li>No limite, o backup novo pode SOBRESCREVER um antigo escolhido (o antigo só é apagado depois que o novo deu certo).</li>
 *   <li>Automático por servidor (de X em X horas, mantendo N); no limite troca o automático mais antigo não travado.
 *       Backups travados (🔒) nunca são trocados nem apagados pela automação.</li>
 *   <li>Com o servidor ligado: save-off + save-all flush antes e save-on depois (o mundo não sai pela metade).</li>
 *   <li>Restaurar: desliga, extrai numa pasta ao lado, troca as pastas e liga de novo se estava ligado.</li>
 * </ul>
 */
final class Backups {
    static final class Bk { String id, server, owner, label; long size, created; boolean auto, locked; }

    private final MinesyncHost H;
    final File dir; private final File index;
    final Map<String, Bk> all = new ConcurrentHashMap<>();

    Backups(MinesyncHost h, File dir) {
        this.H = h; this.dir = dir; dir.mkdirs(); this.index = new File(dir, "backups.json");
        try { if (index.isFile()) { List<Bk> l = Json.G.fromJson(Files.readString(index.toPath(), StandardCharsets.UTF_8), new TypeToken<List<Bk>>() {}.getType()); if (l != null) for (Bk b : l) if (b != null && b.id != null && file(b).isFile()) all.put(b.id, b); } }
        catch (Exception e) { MinesyncHost.log("backups.json: " + e.getMessage()); }
        // sobras de um backup interrompido (.part) e de uma restauração interrompida
        File[] ds = dir.listFiles(File::isDirectory);
        if (ds != null) for (File d : ds) { File[] ps = d.listFiles((x, n) -> n.endsWith(".part")); if (ps != null) for (File p : ps) p.delete(); }
    }
    synchronized void save() {
        try { File t = new File(index.getPath() + ".tmp"); Files.writeString(t.toPath(), Json.PRETTY.toJson(new ArrayList<>(all.values())), StandardCharsets.UTF_8); Files.move(t.toPath(), index.toPath(), StandardCopyOption.REPLACE_EXISTING); Users.restrict(index); }
        catch (Exception e) { MinesyncHost.log("backups.json: " + e.getMessage()); }
    }
    File file(Bk b) { return new File(new File(dir, b.server), b.id + ".zip"); }

    // ------------------------------------------------------------------ limites
    boolean enabled() { return H.conf.bool("backups.enabled", true); }
    long maxCount(String owner) { Users.User u = H.users.get(owner); return u != null && u.backup_max_count != null ? u.backup_max_count : H.conf.num("backups.max_count", 5); }
    long maxBytes(String owner) { Users.User u = H.users.get(owner); return (u != null && u.backup_max_mb != null ? u.backup_max_mb : H.conf.num("backups.max_mb", 10240)) * 1048576L; }
    long[] usage(String owner) { long c = 0, b = 0; for (Bk x : all.values()) if (x.owner != null && x.owner.equalsIgnoreCase(owner)) { c++; b += x.size; } return new long[]{ c, b }; }
    long totalBytes() { long t = 0; for (Bk x : all.values()) t += x.size; return t; }
    Map<String, Object> quota(String owner) {
        long[] u = usage(owner); Map<String, Object> m = new LinkedHashMap<>();
        m.put("count", u[0]); m.put("max_count", maxCount(owner)); m.put("mb", Math.round(u[1] / 104857.6) / 10.0); m.put("max_mb", maxBytes(owner) / 1048576L);
        return m;
    }
    List<Bk> of(String server) { List<Bk> l = new ArrayList<>(); for (Bk b : all.values()) if (b.server.equals(server)) l.add(b); l.sort(Comparator.comparingLong((Bk b) -> b.created).reversed()); return l; }
    List<Bk> ofOwner(String owner) { List<Bk> l = new ArrayList<>(); for (Bk b : all.values()) if (b.owner != null && b.owner.equalsIgnoreCase(owner)) l.add(b); l.sort(Comparator.comparingLong((Bk b) -> b.created).reversed()); return l; }
    Map<String, Object> view(Bk b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", b.id); m.put("server", b.server); Instances.Inst i = H.inst.all.get(b.server); m.put("server_name", i == null ? b.server : i.name);
        m.put("label", b.label); m.put("size_mb", Math.round(b.size / 104857.6) / 10.0); m.put("created", b.created); m.put("auto", b.auto); m.put("locked", b.locked);
        return m;
    }
    Bk get(String id, String owner, boolean admin) { Bk b = id == null ? null : all.get(id); if (b == null || !(admin || owner.equalsIgnoreCase(b.owner))) throw new Api.Err("not_found", 404); return b; }

    // ------------------------------------------------------------------ tarefas (uma por servidor)
    interface Work { void run(Archive.Progress pr) throws Exception; }
    void job(Instances.Inst i, String kind, String what, Work w) {
        synchronized (i) {
            Map<String, Object> j = i.job;
            if (j != null && "running".equals(j.get("state"))) throw new Api.Err("busy");
            Archive.Progress pr = new Archive.Progress();
            Map<String, Object> nj = new ConcurrentHashMap<>(); nj.put("kind", kind); nj.put("what", what == null ? "" : what); nj.put("state", "running"); nj.put("started", System.currentTimeMillis());
            i.job = nj; i.jobProgress = pr;
            H.pool.execute(() -> {
                try { w.run(pr); nj.put("state", "done"); }
                catch (Api.Err e) { nj.put("state", "error"); nj.put("error", e.getMessage()); }
                catch (Throwable e) { String m = e.getMessage() == null ? e.toString() : e.getMessage(); nj.put("state", "error"); nj.put("error", m.startsWith("quota") ? "backup_quota" : m.equals("canceled") ? "canceled" : "io_error: " + m); MinesyncHost.log(i.name + " " + kind + ": " + m); }
                finally { nj.put("finished", System.currentTimeMillis()); nj.put("bytes", pr.bytes); nj.put("files", pr.files); H.inst.line(i, "[MinesyncHost] " + kind + ": " + nj.get("state") + (nj.containsKey("error") ? " (" + nj.get("error") + ")" : "")); }
            });
        }
    }
    Map<String, Object> jobView(Instances.Inst i) {
        Map<String, Object> j = i.job; if (j == null) return null;
        Map<String, Object> m = new LinkedHashMap<>(j); Archive.Progress pr = i.jobProgress;
        if (pr != null && "running".equals(j.get("state"))) {
            m.put("bytes", pr.bytes); m.put("files", pr.files); m.put("current", pr.current); m.put("total", pr.total);
            double pct = pr.inTotal > 0 ? pr.in * 100.0 / pr.inTotal : pr.total > 0 ? pr.bytes * 100.0 / pr.total : -1;   // 2.1.2: % real (-1 = sem como saber)
            m.put("pct", pct < 0 ? -1 : Math.min(99.0, Math.round(pct * 10) / 10.0));
            long el = System.currentTimeMillis() - ((Number) j.getOrDefault("started", System.currentTimeMillis())).longValue();
            if (pct > 1 && el > 2000) m.put("eta_s", Math.round(el / 1000.0 * (100 - pct) / pct));
        } else if ("done".equals(j.get("state"))) m.put("pct", 100);
        return m;
    }

    // ------------------------------------------------------------------ criar
    /** replaceId = backup antigo (do mesmo dono) que será sobrescrito quando o novo terminar */
    void create(Instances.Inst i, String label, String replaceId, boolean auto, Users.User by) {
        if (!enabled()) throw new Api.Err("backups_disabled");
        if ("external".equals(i.type)) throw new Api.Err("bad_type");
        if (i.restoring) throw new Api.Err("busy");
        String owner = i.owner;
        Bk old = null;
        if (replaceId != null && !replaceId.isEmpty()) {
            old = all.get(replaceId);
            if (old == null || !(owner.equalsIgnoreCase(old.owner) || by != null && by.admin && old.server.equals(i.id))) throw new Api.Err("not_found", 404);
            if (old.locked && !auto) throw new Api.Err("backup_locked");
        } else if (usage(owner)[0] >= maxCount(owner)) throw new Api.Err("backup_limit");
        long allow = maxBytes(owner) - usage(owner)[1] + (old != null ? old.size : 0);
        long hostMax = H.conf.num("backups.max_total_gb", 0) * 1073741824L;
        if (hostMax > 0) allow = Math.min(allow, hostMax - totalBytes() + (old != null ? old.size : 0));
        if (allow <= 0) throw new Api.Err("backup_quota");
        String lb = label == null ? "" : label.replaceAll("[<>\"'\\\\\\r\\n]", "").trim(); if (lb.length() > 60) lb = lb.substring(0, 60);
        final Bk replace = old; final long allowF = allow; final String lbF = lb.isEmpty() ? (auto ? "auto" : "") : lb;
        job(i, "backup", lbF, pr -> {
            Bk b = new Bk(); b.id = Long.toString(System.currentTimeMillis(), 36) + Users.rnd(3).replaceAll("[^A-Za-z0-9]", "x"); b.server = i.id; b.owner = owner; b.label = lbF; b.auto = auto; b.created = System.currentTimeMillis();
            File out = file(b); out.getParentFile().mkdirs(); File part = new File(out.getPath() + ".part");
            boolean running = i.proc != null && i.proc.isAlive() && "running".equals(i.state) && Instances.mc(i.type);   // 2.1.2: save-off/save-all só existe no Minecraft (antes travava 30 s nos outros)
            Map<String, Object> nj = i.job;
            if (running) { if (nj != null) nj.put("phase", "saving"); try { long s0 = i.seq.get(); H.inst.command(i, "save-off"); H.inst.command(i, "save-all flush"); waitLine(i, s0, 30_000); } catch (Exception ignored) { } }
            try {
                if (nj != null) nj.put("phase", "listing");
                Path root = H.inst.dir(i).toPath().toAbsolutePath().normalize();
                List<Archive.Item> items = Archive.collect(root, List.of(root), p -> { String n = p.getFileName() == null ? "" : p.getFileName().toString(); return n.equals("session.lock") || n.endsWith(".msh-tmp") || n.contains(".msh-x"); });
                int skipped;
                if (nj != null) nj.put("phase", "writing");
                try (OutputStream o = new Archive.Counting(new BufferedOutputStream(Files.newOutputStream(part.toPath()), 1 << 16), allowF)) { skipped = Archive.write("zip", items, o, pr, 5); }
                catch (IOException e) { part.delete(); if (e.getMessage() != null && e.getMessage().startsWith("quota")) throw new Api.Err("backup_quota"); throw e; }
                b.size = part.length();
                Files.move(part.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING); Users.restrict(out);
                if (replace != null) { all.remove(replace.id); file(replace).delete(); }
                all.put(b.id, b); save();
                if (auto) { i.backup_last = b.created; H.inst.save(); }
                H.inst.line(i, "[MinesyncHost] backup " + b.id + " · " + (b.size / 1048576) + " MB" + (skipped > 0 ? " · " + skipped + " arquivo(s) em uso pulados" : "") + (replace != null ? " · substituiu " + replace.id : ""));
            } finally {
                if (running && i.proc != null && i.proc.isAlive()) try { H.inst.command(i, "save-on"); } catch (Exception ignored) { }
            }
        });
    }
    /** espera o "Saved the game" do servidor (ou o tempo acabar) */
    private void waitLine(Instances.Inst i, long since, long ms) {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            List<String> l; synchronized (i.log) { long s = i.seq.get(); int n = i.log.size(); int from = (int) Math.max(0, n - (s - since)); l = new ArrayList<>(i.log.subList(Math.max(0, Math.min(n, from)), n)); }
            for (String x : l) if (x.contains("Saved the game") || x.contains("saved the game") || x.contains("Saved the world")) return;
            try { Thread.sleep(250); } catch (InterruptedException e) { return; }
        }
    }

    // ------------------------------------------------------------------ restaurar
    void restore(Instances.Inst i, Bk b) {
        if (!b.server.equals(i.id)) throw new Api.Err("not_found", 404);
        if (i.restoring) throw new Api.Err("busy");
        job(i, "restore", b.label == null || b.label.isEmpty() ? b.id : b.label, pr -> {
            i.restoring = true;
            boolean was = i.proc != null && i.proc.isAlive();
            try {
                if (was) { H.inst.stop(i); for (int k = 0; k < 130 && i.proc != null && i.proc.isAlive(); k++) Thread.sleep(500); if (i.proc != null && i.proc.isAlive()) { H.inst.kill(i); Thread.sleep(1500); } }
                Path d = H.inst.dir(i).toPath().toAbsolutePath().normalize();
                Path tmp = d.resolveSibling(i.id + ".restore-tmp"), old = d.resolveSibling(i.id + ".restore-old");
                Files2.deleteTree(tmp); Files2.deleteTree(old); Files.createDirectories(tmp);
                long lim = Math.max(i.disk_gb * 1073741824L, b.size * 4);
                try { Archive.extract(file(b).toPath(), "zip", tmp, tmp, new Archive.Limits(lim, H.conf.num("archive.max_entries", 200000) * 4), null, pr); }
                catch (Exception e) { Files2.deleteTree(tmp); throw e; }
                try { Files.move(d, old); }
                catch (IOException e) {   // Windows com arquivo preso: esvazia a pasta e move o conteúdo
                    try (var s = Files.list(d)) { for (Path p : (Iterable<Path>) s::iterator) Files2.deleteTree(p); }
                    try (var s = Files.list(tmp)) { for (Path p : (Iterable<Path>) s::iterator) Files.move(p, d.resolve(p.getFileName())); }
                    Files2.deleteTree(tmp); old = null;
                }
                if (old != null) { Files.move(tmp, d); final Path o = old; H.pool.execute(() -> Files2.deleteTree(o)); }
                i.diskBytes = -1;
                H.inst.line(i, "[MinesyncHost] restaurado do backup " + b.id);
            } finally { i.restoring = false; }
            if (was) H.inst.start(i);
        });
    }

    // ------------------------------------------------------------------ outros
    void delete(Bk b) { if (all.remove(b.id) != null) { file(b).delete(); save(); } }
    void removeServer(String server) { boolean ch = false; for (Bk b : new ArrayList<>(all.values())) if (b.server.equals(server)) { all.remove(b.id); file(b).delete(); ch = true; } new File(dir, server).delete(); if (ch) save(); }
    void lock(Bk b, boolean on) { b.locked = on; save(); }
    void rename(Bk b, String label) { String l = label == null ? "" : label.replaceAll("[<>\"'\\\\\\r\\n]", "").trim(); if (l.length() > 60) l = l.substring(0, 60); b.label = l; save(); }

    /** automação: a cada minuto confere os servidores com backup automático */
    void tick() {
        if (!enabled() || !H.conf.bool("enabled", false)) return;
        long now = System.currentTimeMillis(), minH = Math.max(1, H.conf.num("backups.min_every_h", 1));
        for (Instances.Inst i : H.inst.all.values()) {
            if (i.backup_every_h <= 0 || "external".equals(i.type) || i.restoring) continue;
            long every = Math.max(minH, i.backup_every_h) * 3600_000L;
            if (now - i.backup_last < every) continue;
            Map<String, Object> j = i.job; if (j != null && "running".equals(j.get("state"))) continue;
            if (!new File(H.inst.dir(i), "server.jar").isFile() && !new File(H.inst.dir(i), "run.sh").isFile() && Sys.dirSize(H.inst.dir(i).toPath()) == 0) continue;
            i.backup_last = now; H.inst.save();
            try { autoBackup(i, "auto"); }
            catch (Api.Err e) { H.inst.line(i, "[MinesyncHost] backup automático: " + e.getMessage()); }
        }
    }
    /** backup automático/agendado: no limite troca o automático mais antigo não travado (mantém no máximo "keep") */
    void autoBackup(Instances.Inst i, String label) {
        List<Bk> autos = new ArrayList<>(); for (Bk b : of(i.id)) if (b.auto && !b.locked) autos.add(b);
        autos.sort(Comparator.comparingLong(b -> b.created));
        long keep = Math.max(1, i.backup_keep <= 0 ? 3 : i.backup_keep);
        boolean atLimit = usage(i.owner)[0] >= maxCount(i.owner);
        String replace = null;
        if (autos.size() >= keep || atLimit) {
            if (autos.isEmpty()) throw new Api.Err("backup_limit");
            replace = autos.get(0).id;
            for (int k = 1; k < autos.size() - keep + 1; k++) delete(autos.get(k));
        }
        create(i, label, replace, true, null);
    }
}
