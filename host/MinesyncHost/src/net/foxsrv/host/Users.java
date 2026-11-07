package net.foxsrv.host;

import com.google.gson.reflect.TypeToken;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** usuários do painel (o 1º é o dono/admin) — senha com PBKDF2 (210 mil voltas), sessões em memória com validade */
final class Users {
    static final class User {
        String name; String hash; String salt; boolean admin; long created; String lang = "auto"; Long backup_max_count, backup_max_mb; /* 1.2: null = padrão do config.yml */
        // 1.3: CONTA da host — permissões, limites (null = padrão de accounts.default no config.yml), tipos liberados, suspensa,
        // conta da rede vinculada (usuário da central, para entrar "pelo site") e contato
        List<String> perms, types; Long max_servers, max_ram_mb, max_cores, max_disk_gb; boolean suspended; String central, central_name, note; long last_login;
    }
    private final File file;
    private final Map<String, User> users = new ConcurrentHashMap<>();
    private final Map<String, long[]> sessions = new ConcurrentHashMap<>();       // token → [validade]
    private final Map<String, String> sessUser = new ConcurrentHashMap<>();
    private final Map<String, long[]> fails = new ConcurrentHashMap<>();
    private static final SecureRandom R = new SecureRandom();
    static final long TTL = 7L * 86400_000L;

    Users(File f) { this.file = f; load(); }
    private void load() {
        try { if (file.isFile()) { List<User> l = Json.G.fromJson(Files.readString(file.toPath(), StandardCharsets.UTF_8), new TypeToken<List<User>>() {}.getType()); if (l != null) for (User u : l) users.put(u.name.toLowerCase(), u); } }
        catch (Exception e) { MinesyncHost.log("users.json: " + e.getMessage()); }
    }
    private synchronized void save() {
        try { File t = new File(file.getPath() + ".tmp"); Files.writeString(t.toPath(), Json.PRETTY.toJson(new ArrayList<>(users.values())), StandardCharsets.UTF_8); Files.move(t.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING); Sys.class.getName(); restrict(file); }
        catch (Exception e) { MinesyncHost.log("users.json: " + e.getMessage()); }
    }
    static void restrict(File f) { try { f.setReadable(false, false); f.setReadable(true, true); f.setWritable(false, false); f.setWritable(true, true); } catch (Exception ignored) { } }
    boolean empty() { return users.isEmpty(); }
    static String rnd(int n) { byte[] b = new byte[n]; R.nextBytes(b); return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }
    private static String hash(String pw, String salt) {
        try { PBEKeySpec s = new PBEKeySpec(pw.toCharArray(), Base64.getDecoder().decode(salt), 210_000, 256); return Base64.getEncoder().encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(s).getEncoded()); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    static boolean validName(String n) { return n != null && n.matches("[A-Za-z0-9_.-]{3,24}"); }
    static boolean validPass(String p) { return p != null && p.length() >= 8 && p.length() <= 200; }
    synchronized User add(String name, String pw, boolean admin) {
        if (!validName(name)) throw new Api.Err("bad_username"); if (!validPass(pw)) throw new Api.Err("weak_password");
        if (users.containsKey(name.toLowerCase())) throw new Api.Err("user_exists");
        User u = new User(); u.name = name; byte[] s = new byte[16]; R.nextBytes(s); u.salt = Base64.getEncoder().encodeToString(s); u.hash = hash(pw, u.salt); u.admin = admin; u.created = System.currentTimeMillis();
        users.put(name.toLowerCase(), u); save(); return u;
    }
    synchronized void remove(String name) { users.remove(String.valueOf(name).toLowerCase()); sessUser.entrySet().removeIf(e -> e.getValue().equalsIgnoreCase(name)); save(); }
    synchronized void setPassword(String name, String pw) { User u = users.get(name.toLowerCase()); if (u == null) throw new Api.Err("not_found"); if (!validPass(pw)) throw new Api.Err("weak_password"); byte[] s = new byte[16]; R.nextBytes(s); u.salt = Base64.getEncoder().encodeToString(s); u.hash = hash(pw, u.salt); save(); }
    List<Map<String, Object>> list() { List<Map<String, Object>> l = new ArrayList<>(); for (User u : users.values()) { Map<String, Object> m = new java.util.LinkedHashMap<>(); m.put("name", u.name); m.put("admin", u.admin); m.put("created", u.created); m.put("backup_max_count", u.backup_max_count); m.put("backup_max_mb", u.backup_max_mb);
        m.put("perms", u.perms); m.put("types", u.types); m.put("max_servers", u.max_servers); m.put("max_ram_mb", u.max_ram_mb); m.put("max_cores", u.max_cores); m.put("max_disk_gb", u.max_disk_gb); m.put("suspended", u.suspended); m.put("central", u.central_name); m.put("note", u.note); m.put("last_login", u.last_login); l.add(m); } l.sort((a, b) -> String.valueOf(a.get("name")).compareToIgnoreCase(String.valueOf(b.get("name")))); return l; }
    List<User> all() { return new ArrayList<>(users.values()); }
    synchronized void persist() { save(); }
    /** 1.3: conta da host vinculada a este usuário da central (entrar pelo site) */
    User byCentral(String centralUser) { if (centralUser == null || centralUser.isBlank()) return null; for (User u : users.values()) if (centralUser.equalsIgnoreCase(u.central)) return u; return null; }
    /** 1.3: sessão para quem entrou pela conta da rede (sem senha da host) */
    String loginAs(User u, String ip) {
        if (u.suspended) throw new Api.Err("account_suspended", 403);
        String t = rnd(32); sessions.put(t, new long[]{ System.currentTimeMillis() + TTL }); sessUser.put(t, u.name); u.last_login = System.currentTimeMillis(); save(); return t;
    }
    /** 1.3: suspender derruba as sessões da conta */
    synchronized void dropSessions(String name) { sessUser.entrySet().removeIf(e -> { if (e.getValue().equalsIgnoreCase(name)) { sessions.remove(e.getKey()); return true; } return false; }); }
    /** freio por IP para as entradas pela rede (credenciais da central) */
    void throttle(String ip) { long now = System.currentTimeMillis(); long[] f = fails.get("net|" + ip); if (f != null && f[1] > now) throw new Api.Err("too_many_attempts", 429); }
    void failed(String ip) { long now = System.currentTimeMillis(); long[] g = fails.computeIfAbsent("net|" + ip, k -> new long[2]); g[0]++; if (g[0] >= 5) g[1] = now + Math.min(15 * 60_000L, 30_000L * (g[0] - 4)); }
    /** nome livre para uma conta nova (a partir do usuário da central) */
    String freeName(String base) {
        String b = String.valueOf(base).replaceAll("[^A-Za-z0-9_.-]", "_"); if (b.length() < 3) b = (b + "___").substring(0, 3); if (b.length() > 20) b = b.substring(0, 20);
        String n = b; int k = 2; while (users.containsKey(n.toLowerCase())) n = b + (k++); return n;
    }
    /** 1.2: limites de backup do usuário (null = o padrão do config.yml) */
    synchronized void setBackupLimits(String name, Long count, Long mb) {
        User u = users.get(String.valueOf(name).toLowerCase()); if (u == null) throw new Api.Err("not_found");
        if (count != null && (count < 0 || count > 1000)) throw new Api.Err("bad_limit"); if (mb != null && (mb < 0 || mb > 1L << 24)) throw new Api.Err("bad_limit");
        u.backup_max_count = count; u.backup_max_mb = mb; save();
    }
    User get(String name) { return name == null ? null : users.get(name.toLowerCase()); }

    /** 1.3: confere a senha atual (trocar a própria senha) */
    boolean check(User u, String pw) { return u != null && pw != null && MessageDigest.isEqual(hash(pw, u.salt).getBytes(StandardCharsets.UTF_8), u.hash.getBytes(StandardCharsets.UTF_8)); }
    /** login com freio: 5 erros → espera crescente por IP */
    String login(String name, String pw, String ip) {
        long now = System.currentTimeMillis(); long[] f = fails.get(ip);
        if (f != null && f[1] > now) throw new Api.Err("too_many_attempts", 429);
        User u = get(name);
        boolean ok = u != null && MessageDigest.isEqual(hash(pw == null ? "" : pw, u.salt).getBytes(StandardCharsets.UTF_8), u.hash.getBytes(StandardCharsets.UTF_8));
        if (!ok) { long[] g = fails.computeIfAbsent(ip, k -> new long[2]); g[0]++; if (g[0] >= 5) g[1] = now + Math.min(15 * 60_000L, 30_000L * (g[0] - 4)); throw new Api.Err("invalid_login", 401); }
        fails.remove(ip);
        if (u.suspended) throw new Api.Err("account_suspended", 403);
        u.last_login = now; save();
        String t = rnd(32); sessions.put(t, new long[]{ now + TTL }); sessUser.put(t, u.name);
        return t;
    }
    User session(String token) {
        if (token == null) return null; long[] v = sessions.get(token);
        if (v == null || v[0] < System.currentTimeMillis()) { if (v != null) { sessions.remove(token); sessUser.remove(token); } return null; }
        User u = get(sessUser.get(token));
        if (u != null && u.suspended) { sessions.remove(token); sessUser.remove(token); return null; }
        return u;
    }
    void logout(String token) { if (token != null) { sessions.remove(token); sessUser.remove(token); } }
}
