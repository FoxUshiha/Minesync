package net.foxsrv.host;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 1.3 — CONTAS da host (como uma hospedagem de verdade):
 * <ul>
 *   <li><b>Permissões da conta</b>: criar servidores, apagar os próprios, tipos próprios (Java com .jar próprio, Node.js,
 *       Python, trocar o arquivo principal/ambiente), editar inicialização, agendamentos, convidar usuários (subusuários).</li>
 *   <li><b>Limites da conta</b> (somando os servidores dela): quantidade de servidores, RAM, núcleos, disco e tipos liberados.
 *       Vazio = o padrão de {@code accounts.default} no config.yml.</li>
 *   <li><b>Permissões por servidor</b> para subusuários: console, ligar/desligar, ver arquivos, mexer nos arquivos, backups,
 *       inicialização, agendamentos, ajustes.</li>
 *   <li>O dono (admin) da host pode tudo e cria servidores PARA uma conta (o servidor fica no limite daquela conta).</li>
 * </ul>
 */
final class Accounts {
    static final List<String> ACCOUNT_PERMS = List.of("create", "delete", "custom", "startup", "schedules", "subusers");
    static final List<String> SERVER_PERMS = List.of("console", "power", "files", "files.write", "backups", "startup", "schedules", "settings");
    static final List<String> CUSTOM_TYPES = List.of("java", "nodejs", "python");
    private final MinesyncHost H;
    Accounts(MinesyncHost h) { this.H = h; }

    @SuppressWarnings("unchecked")
    List<String> perms(Users.User u) {
        if (u == null) return List.of(); if (u.admin) return ACCOUNT_PERMS;
        if (u.perms != null) return u.perms;
        List<String> l = new ArrayList<>(); for (Object o : H.conf.list("accounts.default.perms")) if (ACCOUNT_PERMS.contains(String.valueOf(o))) l.add(String.valueOf(o));
        return l;
    }
    boolean has(Users.User u, String p) { return u != null && (u.admin || !u.suspended && perms(u).contains(p)); }
    long limit(Users.User u, String k) {
        Long v = switch (k) { case "max_servers" -> u.max_servers; case "max_ram_mb" -> u.max_ram_mb; case "max_cores" -> u.max_cores; case "max_disk_gb" -> u.max_disk_gb; default -> null; };
        return v != null ? v : H.conf.num("accounts.default." + k, switch (k) { case "max_servers" -> 1L; case "max_ram_mb" -> 2048L; case "max_cores" -> 1L; default -> 10L; });
    }
    List<String> types(Users.User u) {
        List<String> all = new ArrayList<>(List.of(Instances.TYPES));
        if (u.admin) return all;
        List<String> l = new ArrayList<>();
        if (u.types != null) l.addAll(u.types); else for (Object o : H.conf.list("accounts.default.types")) l.add(String.valueOf(o));
        if (l.isEmpty()) l.addAll(all);
        l.removeIf(t -> !all.contains(t) || CUSTOM_TYPES.contains(t) && !has(u, "custom"));
        return l;
    }
    Map<String, Object> usage(String owner) {
        long n = 0, ram = 0, cores = 0, disk = 0;
        for (Instances.Inst i : H.inst.all.values()) if (owner.equalsIgnoreCase(i.owner)) { n++; if (!"external".equals(i.type)) { ram += i.ram_mb; cores += i.cores; disk += i.disk_gb; } }
        Map<String, Object> m = new LinkedHashMap<>(); m.put("servers", n); m.put("ram_mb", ram); m.put("cores", cores); m.put("disk_gb", disk); return m;
    }
    Map<String, Object> view(Users.User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", u.name); m.put("admin", u.admin); m.put("perms", perms(u)); m.put("types", types(u)); m.put("suspended", u.suspended); m.put("central", u.central_name);
        Map<String, Object> lim = new LinkedHashMap<>(); for (String k : List.of("max_servers", "max_ram_mb", "max_cores", "max_disk_gb")) lim.put(k, limit(u, k));
        m.put("limits", lim); m.put("usage", usage(u.name)); m.put("backup", H.backups.quota(u.name));
        return m;
    }

    // ------------------------------------------------------------------ servidor: quem vê / quem pode
    static String key(String n) { return n == null ? "" : n.toLowerCase(Locale.ROOT); }
    boolean sees(Users.User u, Instances.Inst i) { return u != null && (u.admin || u.name.equalsIgnoreCase(i.owner) || i.subusers != null && i.subusers.containsKey(key(u.name))); }
    boolean owner(Users.User u, Instances.Inst i) { return u != null && (u.admin || u.name.equalsIgnoreCase(i.owner)); }
    /** a conta DONA do servidor pode usar tipo próprio/trocar o arquivo principal e o ambiente? */
    boolean customAllowed(Instances.Inst i) { Users.User o = H.users.get(i.owner); return o == null || has(o, "custom"); }
    boolean can(Users.User u, Instances.Inst i, String perm) {
        if (u == null || u.suspended) return false;
        if (u.admin) return true;
        Users.User own = H.users.get(i.owner); if (own != null && own.suspended) return false;
        if (u.name.equalsIgnoreCase(i.owner)) {
            if (perm.equals("startup")) return has(u, "startup") || has(u, "custom");
            if (perm.equals("schedules")) return has(u, "schedules");
            return true;
        }
        List<String> p = i.subusers == null ? null : i.subusers.get(key(u.name));
        if (p == null) return false;
        if (perm.equals("files") && p.contains("files.write")) return true;
        return p.contains(perm);
    }
    void need(Users.User u, Instances.Inst i, String perm) { if (!can(u, i, perm)) throw new Api.Err("no_permission", 403); }
    /** permissões deste usuário neste servidor (o painel mostra só as abas permitidas) */
    List<String> serverPerms(Users.User u, Instances.Inst i) {
        List<String> l = new ArrayList<>(); for (String p : SERVER_PERMS) if (can(u, i, p)) l.add(p);
        if (owner(u, i)) { l.add("owner"); if (u.admin || has(u, "subusers")) l.add("subusers"); if (u.admin || has(u, "delete")) l.add("delete"); }
        if (customAllowed(i)) l.add("custom");
        return l;
    }

    // ------------------------------------------------------------------ criar servidor
    /** confere se a conta "owner" pode ter mais este servidor (o dono da host passa por cima dos limites da conta) */
    void checkCreate(Users.User by, Users.User owner, String type, long ram, long cores, long disk) {
        if (owner.suspended) throw new Api.Err("account_suspended", 403);
        if (by.admin) return;
        if (!has(by, "create")) throw new Api.Err("no_permission", 403);
        if (!types(owner).contains(type)) throw new Api.Err("type_not_allowed", 403);
        Map<String, Object> us = usage(owner.name);
        if (((Number) us.get("servers")).longValue() + 1 > limit(owner, "max_servers")) throw new Api.Err("account_max_servers");
        if (!"external".equals(type)) {
            if (((Number) us.get("ram_mb")).longValue() + ram > limit(owner, "max_ram_mb")) throw new Api.Err("account_max_ram");
            if (((Number) us.get("cores")).longValue() + cores > limit(owner, "max_cores")) throw new Api.Err("account_max_cores");
            if (((Number) us.get("disk_gb")).longValue() + disk > limit(owner, "max_disk_gb")) throw new Api.Err("account_max_disk");
        }
    }
    /** mudar recursos de um servidor existente (só o dono da host; mas confere o limite da conta se pedir) */
    void checkResize(Users.User owner, Instances.Inst i, long ram, long cores, long disk) {
        Map<String, Object> us = usage(owner.name);
        if (((Number) us.get("ram_mb")).longValue() - i.ram_mb + ram > limit(owner, "max_ram_mb")) throw new Api.Err("account_max_ram");
        if (((Number) us.get("cores")).longValue() - i.cores + cores > limit(owner, "max_cores")) throw new Api.Err("account_max_cores");
        if (((Number) us.get("disk_gb")).longValue() - i.disk_gb + disk > limit(owner, "max_disk_gb")) throw new Api.Err("account_max_disk");
    }

    // ------------------------------------------------------------------ editar conta (dono da host)
    void update(Users.User u, JsonObject b, Users.User by) {
        if (b.has("perms")) { if (b.get("perms").isJsonNull()) u.perms = null; else { List<String> l = new ArrayList<>(); for (JsonElement e : b.getAsJsonArray("perms")) { String p = e.getAsString(); if (ACCOUNT_PERMS.contains(p) && !l.contains(p)) l.add(p); } u.perms = l; } }
        if (b.has("types")) { if (b.get("types").isJsonNull()) u.types = null; else { List<String> l = new ArrayList<>(); for (JsonElement e : b.getAsJsonArray("types")) { String t = e.getAsString(); if (List.of(Instances.TYPES).contains(t) && !l.contains(t)) l.add(t); } u.types = l.isEmpty() ? null : l; } }
        if (b.has("max_servers")) u.max_servers = opt(b, "max_servers", 0, 10000);
        if (b.has("max_ram_mb")) u.max_ram_mb = opt(b, "max_ram_mb", 0, 1L << 22);
        if (b.has("max_cores")) u.max_cores = opt(b, "max_cores", 0, 1024);
        if (b.has("max_disk_gb")) u.max_disk_gb = opt(b, "max_disk_gb", 0, 1L << 20);
        if (b.has("backup_max_count")) u.backup_max_count = opt(b, "backup_max_count", 0, 1000);
        if (b.has("backup_max_mb")) u.backup_max_mb = opt(b, "backup_max_mb", 0, 1L << 24);
        if (b.has("note")) { String n = Json.s(b, "note", "").replaceAll("[<>]", "").trim(); u.note = n.length() > 200 ? n.substring(0, 200) : n; }
        if (b.has("admin") && !u.name.equalsIgnoreCase(by.name)) u.admin = Json.b(b, "admin", false);
        if (b.has("suspended") && !u.name.equalsIgnoreCase(by.name)) {
            boolean s = Json.b(b, "suspended", false);
            if (s && !u.suspended) { H.users.dropSessions(u.name); for (Instances.Inst i : H.inst.all.values()) if (u.name.equalsIgnoreCase(i.owner) && i.proc != null && i.proc.isAlive()) { H.inst.line(i, "[MinesyncHost] account suspended — stopping"); H.inst.stop(i); } }
            u.suspended = s;
        }
        if (b.has("central")) { String c = Json.s(b, "central", "").trim(); if (c.isEmpty()) { u.central = null; u.central_name = null; } else { if (!c.matches("[A-Za-z0-9_.@-]{2,64}")) throw new Api.Err("bad_central_user"); Users.User o = H.users.byCentral(c); if (o != null && o != u) throw new Api.Err("central_in_use"); u.central = c.toLowerCase(Locale.ROOT); u.central_name = c; } }
        H.users.persist();
    }
    private static Long opt(JsonObject b, String k, long min, long max) {
        if (b.get(k).isJsonNull()) return null; String s = b.get(k).getAsString().trim(); if (s.isEmpty() || s.equals("-1")) return null;
        long v; try { v = Long.parseLong(s); } catch (NumberFormatException e) { throw new Api.Err("bad_limit"); }
        if (v < min || v > max) throw new Api.Err("bad_limit"); return v;
    }
}
