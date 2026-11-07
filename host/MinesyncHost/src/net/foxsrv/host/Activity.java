package net.foxsrv.host;

import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 1.3 — REGISTRO DE ATIVIDADE por servidor (quem fez o quê e quando): últimas 300 ações, gravado em .msh/activity.json */
final class Activity {
    private final File file; private final Map<String, List<Map<String, Object>>> all = new ConcurrentHashMap<>(); private volatile boolean dirty;
    Activity(File f) {
        this.file = f;
        try { if (f.isFile()) { Map<String, List<Map<String, Object>>> m = Json.G.fromJson(Files.readString(f.toPath(), StandardCharsets.UTF_8), new TypeToken<Map<String, List<Map<String, Object>>>>() {}.getType()); if (m != null) all.putAll(m); } } catch (Exception e) { MinesyncHost.log("activity.json: " + e.getMessage()); }
    }
    void log(Instances.Inst i, String user, String action, String detail) {
        Map<String, Object> e = new LinkedHashMap<>(); e.put("t", System.currentTimeMillis()); e.put("user", user); e.put("action", action); e.put("detail", detail == null ? "" : detail.length() > 300 ? detail.substring(0, 300) : detail);
        List<Map<String, Object>> l = all.computeIfAbsent(i.id, k -> new ArrayList<>());
        synchronized (l) { l.add(e); if (l.size() > 300) l.subList(0, l.size() - 300).clear(); }
        dirty = true;
    }
    void log(Instances.Inst i, Users.User u, String action, String detail) { log(i, u == null ? "?" : u.name, action, detail); }
    List<Map<String, Object>> of(String id) { List<Map<String, Object>> l = all.get(id); if (l == null) return List.of(); synchronized (l) { List<Map<String, Object>> c = new ArrayList<>(l); java.util.Collections.reverse(c); return c; } }
    void remove(String id) { if (all.remove(id) != null) dirty = true; }
    void flush() {
        if (!dirty) return; dirty = false;
        try { File t = new File(file.getPath() + ".tmp"); Files.writeString(t.toPath(), Json.G.toJson(all), StandardCharsets.UTF_8); Files.move(t.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING); Users.restrict(file); } catch (Exception e) { MinesyncHost.log("activity.json: " + e.getMessage()); }
    }
}
