package net.foxsrv.host;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 2.1 — O NÓ (configurado só pela CENTRAL, aba Máquinas do site): quanto do PC fica para o sistema (reserva), quanto vai
 * para o servidor grátis do dono (0–100% do resto) e o resto é da rede. Sem conta vinculada não existe dono: o PC
 * inteiro (menos a reserva do sistema) é da rede. Também guarda a rede (Cloudflare, IP fixo, portas, DNS) e a pasta
 * de backups da central (só nas máquinas do admin).
 */
final class Node {
    private final MinesyncHost H;
    Node(MinesyncHost h) { this.H = h; }

    long ram() { return Math.max(512, Sys.totalRamMb()); }
    long threads() { return Math.max(1, Runtime.getRuntime().availableProcessors()); }
    /** disco que a host pode usar: o espaço livre + o que os servidores já usam (o resto do disco é do PC) */
    long disk() {
        long used = 0; for (Instances.Inst i : H.inst.all.values()) if (i.diskBytes > 0) used += i.diskBytes;
        long gb = (H.base.getUsableSpace() + used) / 1073741824L;
        long cap = H.conf.num("managed.disk_max_gb", 0); if (cap > 0) gb = Math.min(gb, cap);
        return Math.max(1, gb);
    }
    long reserveRam() { long d = Math.max(1024, Math.round(ram() * 0.15 / 256.0) * 256); return clamp(H.conf.num("managed.reserve.ram_mb", d), 256, ram() - 256); }
    long reserveCores() { long d = threads() >= 4 ? 1 : 0; return clamp(H.conf.num("managed.reserve.cores", d), 0, threads() - 1); }
    long reserveDisk() { long d = Math.max(5, disk() / 10); return clamp(H.conf.num("managed.reserve.disk_gb", d), 1, Math.max(1, disk() - 1)); }
    int ownerPct() { return H.net.linked ? (int) clamp(H.conf.num("managed.owner_pct", 0), 0, 100) : 0; }
    boolean contribute() { return H.conf.bool("managed.contribute", true); }
    private static long clamp(long v, long lo, long hi) { return Math.max(lo, Math.min(Math.max(lo, hi), v)); }

    Map<String, Object> info() {
        long tr = ram(), tc = threads(), td = disk();
        long or = reserveRam(), oc = reserveCores(), od = reserveDisk();
        long fr = Math.max(0, tr - or), fc = Math.max(0, tc - oc), fd = Math.max(0, td - od);
        int pct = ownerPct();
        long wr = fr * pct / 100, wc = Math.round(fc * pct / 100.0), wd = fd * pct / 100;
        if (pct > 0 && wc == 0 && fc > 0) wc = 1;
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("total", res(tr, tc, td)); n.put("os", res(or, oc, od)); n.put("owner", res(wr, wc, wd)); n.put("owner_pct", pct);
        long cr = Math.max(0, fr - wr), cc = Math.max(0, fc - wc), cd = Math.max(0, fd - wd);
        if (!contribute()) { cr = 0; cc = 0; cd = 0; }
        n.put("central", res(cr, cc, cd));
        n.put("inst_max", res(cr, cc, cd));
        long ur = 0, uc = 0, ud = 0, xr = 0, xc = 0, xd = 0;
        for (Instances.Inst i : H.inst.all.values()) if (i.central) { if (i.free) { xr += i.ram_mb; xc += i.cores; xd += i.disk_gb; } else { ur += i.ram_mb; uc += i.cores; ud += i.disk_gb; } }
        n.put("central_used", res(ur, uc, ud)); n.put("owner_used", res(xr, xc, xd));
        n.put("contribute", contribute()); n.put("linked", H.net.linked);
        return n;
    }
    static Map<String, Object> res(long r, long c, long d) { Map<String, Object> m = new LinkedHashMap<>(); m.put("ram_mb", r); m.put("cores", c); m.put("disk_gb", d); return m; }

    /** o que a central mandou (aba Máquinas): grava e aplica na hora */
    synchronized Map<String, Object> apply(JsonObject c) {
        if (c.has("contribute")) H.conf.set("managed.contribute", Json.b(c, "contribute", true));
        if (c.has("owner_pct")) H.conf.set("managed.owner_pct", clamp(Json.n(c, "owner_pct", 0), 0, 100));
        if (c.has("disk_max_gb")) H.conf.set("managed.disk_max_gb", Math.max(0, Json.n(c, "disk_max_gb", 0)));
        if (c.has("name")) { String nm = Json.s(c, "name", "").replaceAll("[^\\p{L}\\p{N} _.()-]", "").trim(); if (!nm.isEmpty()) H.conf.set("network.name", nm.length() > 40 ? nm.substring(0, 40) : nm); }
        if (c.has("reserve") && c.get("reserve").isJsonObject()) {
            JsonObject r = c.getAsJsonObject("reserve");
            if (r.has("ram_mb")) H.conf.set("managed.reserve.ram_mb", clamp(Json.n(r, "ram_mb", 1024), 256, ram() - 256));
            if (r.has("cores")) H.conf.set("managed.reserve.cores", clamp(Json.n(r, "cores", 1), 0, threads() - 1));
            if (r.has("disk_gb")) H.conf.set("managed.reserve.disk_gb", Math.max(1, Json.n(r, "disk_gb", 10)));
        }
        if (c.has("network") && c.get("network").isJsonObject()) {
            JsonObject n = c.getAsJsonObject("network");
            if (n.has("public_ip")) H.conf.set("public_ip.enabled", Json.b(n, "public_ip", false));
            if (n.has("address")) { String a = Json.s(n, "address", "").trim().toLowerCase(); if (!a.isEmpty() && !a.matches("[a-z0-9.:-]{3,200}")) throw new Api.Err("bad_address"); H.conf.set("public_ip.address", a); }
            if (n.has("ports")) { String p = Json.s(n, "ports", "").trim(); if (!p.isEmpty() && !p.matches("\\d{2,5}(-\\d{2,5})?(,\\d{2,5}(-\\d{2,5})?)*")) throw new Api.Err("bad_ports"); H.conf.set("public_ip.ports", p); }
            if (n.has("bandwidth_mbps")) H.conf.set("network.bandwidth_mbps", Math.max(0, Json.n(n, "bandwidth_mbps", 0)));
            String cf = Json.s(n, "cloudflare", null);
            if (cf != null) {
                H.conf.set("cloudflare.enabled", !"off".equals(cf)); H.conf.set("cloudflare.mode", "token".equals(cf) ? "token" : "quick");
                String t = Json.s(n, "cf_token", null); if (t != null) { t = t.trim(); if (!t.isEmpty() && !t.matches("[A-Za-z0-9_=.+/-]{40,}")) throw new Api.Err("bad_token"); if (!t.isEmpty()) H.conf.set("cloudflare.token", t); }
                String hn = Json.s(n, "cf_hostname", null); if (hn != null) { hn = hn.trim().toLowerCase(); if (!hn.isEmpty() && !hn.matches("[a-z0-9.-]{3,200}")) throw new Api.Err("bad_hostname"); H.conf.set("cloudflare.hostname", hn); }
            }
        }
        if (c.has("backup_dir")) {
            String d = Json.s(c, "backup_dir", "").trim();
            if (!d.isEmpty()) {
                File f = new File(d); if (!f.isAbsolute()) throw new Api.Err("bad_backup_dir");
                f.mkdirs(); if (!f.isDirectory() || !f.canWrite()) throw new Api.Err("backup_dir_not_writable");
                String sv = H.servers.getAbsolutePath(); if (f.getAbsolutePath().startsWith(sv)) throw new Api.Err("bad_backup_dir");
            }
            H.conf.set("managed.backup_dir", d);
        }
        if (c.has("bench") && Json.b(c, "bench", false)) H.bench.runAsync(() -> H.agent.statsSoon());
        H.conf.save();
        // rede: Cloudflare liga/desliga na hora
        try { if (H.conf.bool("cloudflare.enabled", false)) H.cloud.start(); else H.cloud.stop(); } catch (Throwable t) { MinesyncHost.log("cloudflare: " + t.getMessage()); }
        Map<String, Object> r = new LinkedHashMap<>(); r.put("node", info()); r.put("managed", view()); return r;
    }
    Map<String, Object> view() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("contribute", contribute()); m.put("owner_pct", H.conf.num("managed.owner_pct", 0)); m.put("disk_max_gb", H.conf.num("managed.disk_max_gb", 0));
        m.put("reserve", res(reserveRam(), reserveCores(), reserveDisk()));
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("cloudflare", H.conf.bool("cloudflare.enabled", false) ? H.conf.str("cloudflare.mode", "quick") : "off"); n.put("cf_hostname", H.conf.str("cloudflare.hostname", "")); n.put("cf_token_set", !H.conf.str("cloudflare.token", "").isEmpty());
        n.put("public_ip", H.conf.bool("public_ip.enabled", false)); n.put("address", H.conf.str("public_ip.address", "")); n.put("ports", H.conf.str("public_ip.ports", "")); n.put("bandwidth_mbps", H.conf.num("network.bandwidth_mbps", 0));
        n.put("cloud_state", H.cloud.state); n.put("cloud_url", H.cloud.url == null ? "" : H.cloud.url);
        m.put("network", n); m.put("backup_dir", H.conf.str("managed.backup_dir", "")); m.put("name", H.conf.str("network.name", "MinesyncHost"));
        return m;
    }
    @SuppressWarnings("unused") private static boolean has(JsonObject o, String k) { JsonElement e = o.get(k); return e != null && !e.isJsonNull(); }
}
