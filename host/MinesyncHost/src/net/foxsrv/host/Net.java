package net.foxsrv.host;

import com.google.gson.JsonObject;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REDE: com "Aparecer na rede" ligado (ou para vincular a conta), a host se anuncia na central a cada 60 s — o que tem,
 * o que sobra, preços, moedas, teste grátis e o link do painel (só o da Cloudflare, nunca o IP). A identidade da host é
 * um id + segredo gerados aqui (.msh/host.json), que a central guarda como hash.
 */
final class Net {
    private final MinesyncHost H; private final File idFile;
    String id, secret; volatile boolean linked; volatile String owner = null, lastError = "", pendingCode = null; volatile long lastOk;
    private volatile boolean soon; private volatile boolean unlink;
    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(10)).build();
    Net(MinesyncHost h, File idFile) {
        this.H = h; this.idFile = idFile;
        try { if (idFile.isFile()) { JsonObject o = Json.obj(Files.readString(idFile.toPath(), StandardCharsets.UTF_8)); id = Json.s(o, "id", null); secret = Json.s(o, "secret", null); linked = Json.b(o, "linked", false); owner = Json.s(o, "owner", null); } } catch (Exception ignored) { }
        if (id == null || secret == null) { id = "h" + Users.rnd(15).replaceAll("[^A-Za-z0-9]", "x"); secret = Users.rnd(32); persist(); }
    }
    private void persist() { try { Map<String, Object> m = new LinkedHashMap<>(); m.put("id", id); m.put("secret", secret); m.put("linked", linked); m.put("owner", owner); Files.writeString(idFile.toPath(), Json.PRETTY.toJson(m)); Users.restrict(idFile); } catch (Exception ignored) { } }
    void link(String code) { if (code == null || !code.trim().toUpperCase().matches("[A-Z0-9]{6,12}")) throw new Api.Err("bad_code"); pendingCode = code.trim().toUpperCase(); hello(); if (!linked) throw new Api.Err(lastError.isEmpty() ? "link_failed" : lastError); }
    void unlink() { unlink = true; hello(); linked = false; owner = null; persist(); }
    void helloSoon() { soon = true; }
    boolean soon() { return soon; }
    /** 2.1: a host sempre se registra na central (com conta = do dono; sem conta = host grátis da rede) */
    boolean wanted() { return true; }
    void tick() { if (soon || wanted()) { soon = false; if (wanted()) hello(); } }
    synchronized void hello() {
        try {
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("id", id); b.put("secret", secret); b.put("name", H.conf.str("network.name", "MinesyncHost")); b.put("version", MinesyncHost.VERSION); b.put("os", System.getProperty("os.name") + " " + Sys.arch());
            Map<String, Object> nd = H.node.info();
            b.put("total", nd.get("total")); b.put("free", nd.get("central"));
            b.put("prices", H.conf.get("network.prices")); b.put("trial", H.conf.get("network.trial")); b.put("kinds", H.conf.get("network.kinds"));
            String pu = H.cloud.url; if (pu != null && pu.startsWith("https://")) b.put("panel_url", pu);
            b.put("instances", H.inst.all.size()); b.put("online_servers", H.inst.all.values().stream().filter(i -> "running".equals(i.state)).count());
            b.put("isolated", H.inst.isolatedLinux() || H.conf.bool("isolated", true)); b.put("bridge", H.conf.bool("network.bridge", true));
            if (pendingCode != null) b.put("link_code", pendingCode);
            if (unlink) b.put("unlink", true);
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(H.conf.central() + "/botcraft/api/host/hello")).timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json").header("User-Agent", "MinesyncHost/" + MinesyncHost.VERSION)
                    .POST(HttpRequest.BodyPublishers.ofString(Json.str(b))).build(), HttpResponse.BodyHandlers.ofString());
            JsonObject j = Json.obj(r.body());
            if (r.statusCode() == 200 && Json.b(j, "ok", false)) { linked = Json.b(j, "linked", false); owner = Json.s(j, "owner", null); lastOk = System.currentTimeMillis(); lastError = ""; if (pendingCode != null && !linked) lastError = "invalid_code"; pendingCode = null; unlink = false; persist(); try { if (!H.agent.connected()) H.agent.reconnect(); } catch (Throwable ignored) { } }   // 2.1: registrou → o agente liga na hora (sem esperar 30 s)
            else { lastError = Json.s(j, "error", "HTTP " + r.statusCode()); pendingCode = null; }
        } catch (Exception e) { lastError = "central_offline"; }
    }
}
