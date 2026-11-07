package net.foxsrv.host;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/**
 * 2.0 — o AGENTE: esta host liga para a central (conexão de saída, sem IP fixo nem porta aberta) e a central controla
 * os servidores dela. Recebe pedidos (criar, painel do cliente — as mesmas rotas do painel local —, apagar, suspender,
 * exportar/guardar/restaurar backups, importar numa migração, conferir integridade) e manda o estado a cada 30 s.
 * Arquivos grandes vão em FLUXO por um "encontro" HTTP na central (/botcraft/hn/pipe/…), nada fica em memória.
 */
final class Agent {
    private final MinesyncHost H;
    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(15)).build();
    private volatile WebSocket ws; private volatile boolean connecting; private volatile long backoffUntil, backoff = 5000;
    volatile String state = "off", error = ""; volatile long connectedAt;
    private final StringBuilder rx = new StringBuilder(); private ByteBuffer bin;
    private final File store0;   // espaço de backup da central (só nas hosts marcadas como armazenamento pelo admin)
    Agent(MinesyncHost h) { this.H = h; this.store0 = new File(h.base, "central-store"); }
    /** 2.1: a pasta escolhida pelo admin (um disco grande só para backups) ou ./central-store */
    File store() { String d = H.conf.str("managed.backup_dir", "").trim(); return d.isEmpty() ? store0 : new File(d, "minesync-central-store"); }
    void statsSoon() { try { H.timer.schedule(() -> { try { sendStats("stats"); } catch (Throwable ignored) { } }, 500, java.util.concurrent.TimeUnit.MILLISECONDS); } catch (Throwable ignored) { } }

    // ------------------------------------------------------------------ conexão
    /** 2.1: sempre ligado (com ou sem conta: sem conta o PC vira host grátis da rede depois de 5 min) */
    boolean wanted() { return H.conf.bool("network.agent", true); }
    void tick() {
        if (!wanted()) { if (ws != null) { try { ws.abort(); } catch (Exception ignored) { } ws = null; } state = "off"; return; }
        if (ws != null && !ws.isOutputClosed() && !ws.isInputClosed()) { sendStats("stats"); return; }
        if (connecting || System.currentTimeMillis() < backoffUntil) return;
        connect();
    }
    /** 2.1: conta nova / central nova → reconecta já com a identidade nova */
    void reconnect() { WebSocket w = ws; ws = null; if (w != null) try { w.abort(); } catch (Exception ignored) { } backoff = 2000; backoffUntil = 0; H.timer.schedule(this::tick, 1500, java.util.concurrent.TimeUnit.MILLISECONDS); }
    private void connect() {
        connecting = true; state = "connecting";
        String c = H.conf.central(); String u = (c.startsWith("https://") ? "wss://" + c.substring(8) : "ws://" + c.replaceFirst("^http://", "")) + "/botcraft/hn/agent";
        http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(20)).header("x-host-id", H.net.id).header("x-host-secret", H.net.secret)
            .buildAsync(URI.create(u), new WebSocket.Listener() {
                @Override public void onOpen(WebSocket w) { ws = w; connecting = false; state = "on"; error = ""; backoff = 5000; connectedAt = System.currentTimeMillis(); w.request(1); sendStats("hello"); MinesyncHost.log("rede da central: conectada"); }
                @Override public CompletionStage<?> onBinary(WebSocket w, ByteBuffer data, boolean last) {
                    synchronized (Agent.this) {
                        if (bin == null) bin = ByteBuffer.allocate(Math.max(8192, data.remaining()));
                        if (bin.remaining() < data.remaining()) { ByteBuffer nb = ByteBuffer.allocate((bin.position() + data.remaining()) * 2); bin.flip(); nb.put(bin); bin = nb; }
                        bin.put(data);
                        if (last) { bin.flip(); byte[] b = new byte[bin.remaining()]; bin.get(b); bin = null; String s = new String(b, StandardCharsets.UTF_8); H.pool.execute(() -> onMsg(s)); }
                    }
                    w.request(1); return null;
                }
                @Override public CompletionStage<?> onText(WebSocket w, CharSequence data, boolean last) { synchronized (Agent.this) { rx.append(data); if (last) { String s = rx.toString(); rx.setLength(0); H.pool.execute(() -> onMsg(s)); } } w.request(1); return null; }
                @Override public CompletionStage<?> onClose(WebSocket w, int code, String reason) { down("fechada (" + code + ")"); return null; }
                @Override public void onError(WebSocket w, Throwable e) { down(String.valueOf(e.getMessage())); }
            }).exceptionally(e -> { down(String.valueOf(e.getCause() != null ? e.getCause().getMessage() : e.getMessage())); return null; });
    }
    private void down(String why) {
        ws = null; connecting = false; state = "error"; error = why == null ? "" : why;
        backoffUntil = System.currentTimeMillis() + backoff; backoff = Math.min(120_000, backoff * 2);
    }
    private synchronized void send(Object o) {
        WebSocket w = ws; if (w == null) return;
        try { w.sendBinary(ByteBuffer.wrap(Json.str(o).getBytes(StandardCharsets.UTF_8)), true).get(30, TimeUnit.SECONDS); } catch (Exception e) { down("envio: " + e.getMessage()); try { w.abort(); } catch (Exception ignored) { } }
    }
    /** violação de integridade / aviso para a central (vai para o log da host) */
    void event(String kind, String inst, int points, String text) {
        Map<String, Object> m = new LinkedHashMap<>(); m.put("t", "event"); m.put("kind", kind); m.put("inst", inst); m.put("points", points); m.put("text", text);
        MinesyncHost.log("integridade: " + kind + " " + inst + " — " + text);
        send(m);
    }
    void log(String inst, String text) { Map<String, Object> m = new LinkedHashMap<>(); m.put("t", "event"); m.put("kind", "log"); m.put("inst", inst); m.put("text", text); send(m); }

    // ------------------------------------------------------------------ estado
    Map<String, Object> info() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", H.conf.str("network.name", "MinesyncHost")); m.put("version", MinesyncHost.VERSION); m.put("os", System.getProperty("os.name") + " " + Sys.arch()); m.put("java", System.getProperty("java.version"));
        m.put("cpu_model", Sys.cpuModel()); m.put("node", H.inst.nodeInfo()); m.put("contribute", H.node.contribute());
        m.put("machine", H.bench.view()); m.put("managed", H.node.view()); m.put("linked", H.net.linked); m.put("os_name", Bench.osName());
        m.put("cloud_url", H.cloud.url == null ? "" : H.cloud.url); m.put("load", Math.round(Sys.cpuLoad() * 1000) / 10.0); m.put("free_ram_mb", Sys.freeRamMb());
        Map<String, Object> p = new LinkedHashMap<>(); p.put("per_core", H.conf.dbl("network.prices.per_core", 0)); p.put("per_gb_ram", H.conf.dbl("network.prices.per_gb_ram", 0)); p.put("per_gb_disk", H.conf.dbl("network.prices.per_gb_disk", 0)); m.put("prices", p);
        m.put("fixed_ip", H.conf.bool("public_ip.enabled", false)); m.put("dns", !H.conf.str("public_ip.address", "").isEmpty() && !H.conf.str("public_ip.address", "").matches("[0-9.:]+")); m.put("cloudflare", "on".equals(H.cloud.state));
        m.put("bandwidth_mbps", H.conf.num("limits.bandwidth_mbps", 0) > 0 ? H.conf.num("limits.bandwidth_mbps", 0) : H.conf.num("network.bandwidth_mbps", 100)); m.put("isolated", H.inst.isolatedLinux());
        List<String> types = new ArrayList<>(); for (String t : Instances.TYPES) if (!"external".equals(t)) types.add(t); m.put("types", types);
        File st = store(); m.put("store", st.isDirectory()); m.put("store_free_gb", (st.isDirectory() ? st : H.base).getUsableSpace() / 1073741824L); return m;
    }
    private void sendStats(String t) {
        List<Object> l = new ArrayList<>();
        for (Instances.Inst i : H.inst.all.values()) if (i.central) { Map<String, Object> x = new LinkedHashMap<>(); x.put("id", i.id); x.put("state", i.state); x.put("rss_mb", i.rss); x.put("cpu", Math.round(i.cpu * 10) / 10.0); x.put("disk_mb", i.diskBytes < 0 ? -1 : i.diskBytes / 1048576); x.put("suspended", i.suspended); l.add(x); }
        Map<String, Object> m = new LinkedHashMap<>(); m.put("t", t); m.put("info", info()); m.put("instances", l); send(m);
    }

    // ------------------------------------------------------------------ pedidos da central
    private void onMsg(String s) {
        JsonObject m = Json.obj(s); String t = Json.s(m, "t", "");
        if ("ping".equals(t)) { send(Map.of("t", "pong", "at", Json.n(m, "at", 0))); return; }
        if (!"cmd".equals(t)) return;
        String rid = Json.s(m, "rid", ""), op = Json.s(m, "op", "");
        Map<String, Object> res;
        try { res = run(op, m); res.put("ok", res.getOrDefault("ok", true)); }
        catch (Api.Err e) { res = new LinkedHashMap<>(); res.put("ok", false); res.put("error", e.getMessage()); res.put("status", e.status); }
        catch (Throwable e) { res = new LinkedHashMap<>(); res.put("ok", false); res.put("error", "host_error: " + e.getMessage()); MinesyncHost.log("central " + op + ": " + e); }
        res.put("t", "res"); res.put("rid", rid); send(res);
        // estado novo logo na central (sem esperar os 30 s)
        if (op.equals("create") || op.equals("delete") || op.equals("import") || op.equals("suspend") || op.equals("http") && !"GET".equals(Json.s(m, "method", "GET"))) try { H.timer.schedule(() -> { try { sendStats("stats"); } catch (Throwable ignored) { } }, 2, java.util.concurrent.TimeUnit.SECONDS); } catch (Throwable ignored) { }
    }
    private Instances.Inst inst(JsonObject m) { Instances.Inst i = H.inst.all.get(Json.s(m, "id", "")); if (i == null || !i.central) throw new Api.Err("not_found", 404); return i; }
    private String url(String path) { return H.conf.central() + path; }

    private Map<String, Object> run(String op, JsonObject m) throws Exception {
        Map<String, Object> r = new LinkedHashMap<>();
        switch (op) {
            case "create": {
                JsonObject spec = m.has("spec") && m.get("spec").isJsonObject() ? m.getAsJsonObject("spec") : new JsonObject();
                Instances.Inst i = H.inst.createCentral(Json.s(m, "id", ""), spec);
                H.integrity.touch(i); r.put("id", i.id); return r;
            }
            case "http": return http(m);
            case "delete": { Instances.Inst i = H.inst.all.get(Json.s(m, "id", "")); if (i != null && i.central) { H.integrity.forget(i.id); H.inst.delete(i); } return r; }
            case "suspend": { Instances.Inst i = inst(m); i.suspended = Json.b(m, "on", true); H.inst.save(); if (i.suspended && i.proc != null && i.proc.isAlive()) H.inst.stop(i); H.inst.line(i, "[MinesyncHost] " + (i.suspended ? "suspenso pela central" : "reativado pela central")); return r; }
            case "verify": return H.integrity.check(inst(m), Json.b(m, "report", true));
            case "export": return export(inst(m), Json.s(m, "pipe", ""));
            case "store_put": return storePut(Json.s(m, "key", ""), Json.s(m, "pipe", ""));
            case "store_get": { File f = storeFile(Json.s(m, "key", "")); if (!f.isFile()) throw new Api.Err("not_found", 404); postFile(Json.s(m, "pipe", ""), f, "application/zip"); r.put("size", f.length()); return r; }
            case "store_verify": { File f = storeFile(Json.s(m, "key", "")); if (!f.isFile()) throw new Api.Err("not_found", 404); r.put("sha256", Integrity.sha(f.toPath())); r.put("size", f.length()); return r; }
            case "store_delete": { storeFile(Json.s(m, "key", "")).delete(); return r; }
            case "import": return importInst(m);
            case "config": { JsonObject c = m.has("config") && m.get("config").isJsonObject() ? m.getAsJsonObject("config") : new JsonObject(); r.putAll(H.node.apply(c)); statsSoon(); return r; }
            case "bench": { H.bench.runAsync(this::statsSoon); r.put("machine", H.bench.view()); return r; }
            case "proxy": return proxy(m);
            case "limits": {   // 2.1: plano mudou (adicionais, modo grátis da cobrança, fim do teste): a central manda os limites novos
                Instances.Inst i = inst(m);
                if (m.has("ram_mb")) i.ram_mb = Math.max(128, Json.n(m, "ram_mb", i.ram_mb));
                if (m.has("cores")) i.cores = Math.max(1, Math.min(Runtime.getRuntime().availableProcessors(), Json.n(m, "cores", i.cores)));
                if (m.has("disk_gb")) i.disk_gb = Math.max(1, Json.n(m, "disk_gb", i.disk_gb));
                if (m.has("backup_keep")) { long mx = Math.max(0, Json.n(m, "backup_keep", 0)); if (i.backup_keep > mx) i.backup_keep = mx; }
                H.inst.save(); H.inst.line(i, "[MinesyncHost] limites do plano: " + i.ram_mb + " MB · " + i.cores + " vCPU · " + i.disk_gb + " GB (valem no próximo início)");
                r.put("ram_mb", i.ram_mb); r.put("cores", i.cores); r.put("disk_gb", i.disk_gb); return r;
            }
            case "retype": {   // 2.1: o dono troca o tipo/versão (jar) — os arquivos do mundo ficam
                Instances.Inst i = inst(m); String type = Json.s(m, "type", i.type).toLowerCase(java.util.Locale.ROOT);
                if (!java.util.List.of(Instances.TYPES).contains(type) || "external".equals(type)) throw new Api.Err("bad_type");
                String v = Json.s(m, "version", Instances.custom(type) ? "-" : i.version); if (!Instances.custom(type) && !v.matches("[0-9A-Za-z._-]{1,32}")) throw new Api.Err("bad_version");
                if (i.proc != null && i.proc.isAlive()) H.inst.stop(i);
                i.type = type; i.version = Instances.custom(type) ? "-" : v; i.minesync = Instances.bukkit(type);
                H.inst.save(); if (Instances.mc(type)) H.inst.install(i); else i.state = "stopped";
                H.integrity.touch(i); r.put("type", i.type); r.put("version", i.version); return r;
            }
            case "wipe": {   // 2.1: cobrança suspensa — para e apaga os arquivos (a central já guardou o backup); a instância fica travada
                Instances.Inst i = inst(m); i.suspended = true;
                if (i.proc != null && i.proc.isAlive()) H.inst.kill(i);
                File d = H.inst.dir(i); try { for (int k = 0; k < 20 && i.proc != null && i.proc.isAlive(); k++) Thread.sleep(250); Files2.deleteTree(d.toPath()); } catch (Exception e) { MinesyncHost.log("wipe " + i.id + ": " + e.getMessage()); }
                d.mkdirs(); H.inst.save(); H.integrity.touch(i); H.inst.line(i, "[MinesyncHost] arquivos removidos (fatura vencida) — pagar restaura do backup"); return r;
            }
            case "metrics": { Instances.Inst i = inst(m); r.put("hist", H.inst.history(i)); r.put("cores", i.cores); r.put("ram_mb", i.ram_mb); return r; }
            default: throw new Api.Err("bad_op");
        }
    }
    /** uma ação do painel do cliente (as MESMAS rotas do painel local) */
    private Map<String, Object> http(JsonObject m) throws Exception {
        String method = Json.s(m, "method", "GET").toUpperCase(), path = Json.s(m, "path", "");
        if (!path.matches("/api/servers/[A-Za-z0-9_-]{3,40}(/[A-Za-z0-9/_-]*)?")) throw new Api.Err("bad_path");
        Instances.Inst i = H.inst.all.get(path.split("/")[3]); if (i == null || !i.central) throw new Api.Err("not_found", 404);
        StringBuilder q = new StringBuilder();
        if (m.has("query") && m.get("query").isJsonObject()) for (Map.Entry<String, JsonElement> e : m.getAsJsonObject("query").entrySet()) { if (q.length() > 0) q.append('&'); q.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)).append('=').append(URLEncoder.encode(e.getValue().isJsonPrimitive() ? e.getValue().getAsString() : e.getValue().toString(), StandardCharsets.UTF_8)); }
        URI uri = URI.create(path + (q.length() > 0 ? "?" + q : ""));
        InputStream body;
        String pin = Json.s(m, "pipe_in", null), pout = Json.s(m, "pipe_out", null);
        if (pin != null) { HttpResponse<InputStream> g = http.send(HttpRequest.newBuilder(URI.create(url(pin))).timeout(Duration.ofHours(6)).GET().build(), HttpResponse.BodyHandlers.ofInputStream()); if (g.statusCode() != 200) throw new Api.Err("pipe_failed"); body = g.body(); }
        else body = new ByteArrayInputStream(m.has("body") && !m.get("body").isJsonNull() ? m.get("body").toString().getBytes(StandardCharsets.UTF_8) : new byte[0]);
        final CompletableFuture<HttpResponse<String>>[] post = new CompletableFuture[1];
        CentralExchange.OutFactory of = pout == null ? null : (h, len) -> {
            PipedInputStream pi = new PipedInputStream(1 << 16); PipedOutputStream po = new PipedOutputStream(pi);
            String fn = String.valueOf(h.getFirst("Content-Disposition")).replaceAll(".*filename=\"?([^\"]*)\"?.*", "$1");
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url(pout))).timeout(Duration.ofHours(6)).header("Content-Type", h.getFirst("Content-Type") == null ? "application/octet-stream" : h.getFirst("Content-Type"));
            if (fn != null && !fn.equals("null") && !fn.isEmpty()) b.header("X-File-Name", fn);
            post[0] = http.sendAsync(b.POST(HttpRequest.BodyPublishers.ofInputStream(() -> pi)).build(), HttpResponse.BodyHandlers.ofString());
            return po;
        };
        CentralExchange x = new CentralExchange(method, uri, body, of);
        H.panel.runCentral(x);
        Map<String, Object> r = new LinkedHashMap<>(); r.put("status", x.status);
        if (x.streamed) { if (post[0] != null) post[0].get(6, TimeUnit.HOURS); r.put("json", Map.of("ok", true)); return r; }
        r.put("json", Json.obj(x.buf.toString(StandardCharsets.UTF_8)));
        return r;
    }
    /** BACKUP DA CENTRAL: a pasta inteira do servidor em .zip, em fluxo, para o encontro da central */
    private Map<String, Object> export(Instances.Inst i, String pipe) throws Exception {
        boolean running = i.proc != null && i.proc.isAlive() && "running".equals(i.state);
        if (running && Instances.mc(i.type)) { try { H.inst.command(i, "save-off"); H.inst.command(i, "save-all flush"); Thread.sleep(3000); } catch (Exception ignored) { } }
        try {
            Path root = H.inst.dir(i).toPath().toAbsolutePath().normalize();
            List<Archive.Item> items = Archive.collect(root, List.of(root), p -> { String n = p.getFileName() == null ? "" : p.getFileName().toString(); return n.equals("session.lock") || n.endsWith(".msh-tmp") || n.contains(".msh-x"); });
            PipedInputStream pi = new PipedInputStream(1 << 16); PipedOutputStream po = new PipedOutputStream(pi);
            CompletableFuture<HttpResponse<String>> f = http.sendAsync(HttpRequest.newBuilder(URI.create(url(pipe))).timeout(Duration.ofHours(6)).header("Content-Type", "application/zip").POST(HttpRequest.BodyPublishers.ofInputStream(() -> pi)).build(), HttpResponse.BodyHandlers.ofString());
            Archive.Progress pr = new Archive.Progress(); i.progPr = pr; Instances.prog(i, "central_backup", -1);   // 2.1.2: aparece no painel de atividades do site
            int skipped; try (OutputStream o = new java.io.BufferedOutputStream(po, 1 << 16)) { skipped = Archive.write("zip", items, o, pr, 9); } finally { i.progWhat = null; i.progPr = null; }
            HttpResponse<String> rr = f.get(6, TimeUnit.HOURS); if (rr.statusCode() != 200) throw new Api.Err("pipe_failed");
            Map<String, Object> r = new LinkedHashMap<>(); r.put("files", items.size()); r.put("skipped", skipped); return r;
        } finally { if (running && Instances.mc(i.type) && i.proc != null && i.proc.isAlive()) try { H.inst.command(i, "save-on"); } catch (Exception ignored) { } }
    }
    private File storeFile(String key) { if (!key.matches("[A-Za-z0-9_-]{3,64}")) throw new Api.Err("bad_key"); File st = store(); st.mkdirs(); return new File(st, key + ".zip"); }
    /** guarda um backup da central (substitui o anterior só quando o novo terminou) */
    private Map<String, Object> storePut(String key, String pipe) throws Exception {
        File f = storeFile(key), part = new File(f.getPath() + ".part");
        HttpResponse<InputStream> g = http.send(HttpRequest.newBuilder(URI.create(url(pipe))).timeout(Duration.ofHours(6)).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
        if (g.statusCode() != 200) throw new Api.Err("pipe_failed");
        MessageDigest md = MessageDigest.getInstance("SHA-256"); long n = 0;
        try (InputStream in = g.body(); OutputStream o = Files.newOutputStream(part.toPath())) { byte[] b = new byte[1 << 16]; int k; while ((k = in.read(b)) > 0) { md.update(b, 0, k); o.write(b, 0, k); n += k; } }
        if (n == 0) { part.delete(); throw new Api.Err("empty_backup"); }
        Files.move(part.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING); Users.restrict(f);
        Map<String, Object> r = new LinkedHashMap<>(); r.put("size", n); r.put("sha256", HexFormat.of().formatHex(md.digest())); return r;
    }
    private void postFile(String pipe, File f, String ct) throws Exception {
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(url(pipe))).timeout(Duration.ofHours(6)).header("Content-Type", ct).POST(HttpRequest.BodyPublishers.ofFile(f.toPath())).build(), HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200) throw new Api.Err("pipe_failed");
    }
    /** MIGRAÇÃO: recebe o backup da central e monta o servidor aqui */
    private Map<String, Object> importInst(JsonObject m) throws Exception {
        String id = Json.s(m, "id", ""); JsonObject spec = m.has("spec") && m.get("spec").isJsonObject() ? m.getAsJsonObject("spec").deepCopy() : new JsonObject();
        spec.addProperty("no_install", true);
        Instances.Inst i = H.inst.all.get(id);
        if (i != null && i.central) { H.inst.kill(i); Thread.sleep(800); Files2.deleteTree(H.inst.dir(i).toPath()); H.inst.dir(i).mkdirs(); }
        else i = H.inst.createCentral(id, spec);
        H.integrity.touch(i);
        File tmp = new File(H.state, "import-" + id + ".zip");
        HttpResponse<InputStream> g = http.send(HttpRequest.newBuilder(URI.create(url(Json.s(m, "pipe", "")))).timeout(Duration.ofHours(6)).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
        if (g.statusCode() != 200) throw new Api.Err("pipe_failed");
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        Instances.prog(i, "central_download", g.headers().firstValueAsLong("content-length").orElse(-1));
        try (InputStream in = g.body(); OutputStream o = Files.newOutputStream(tmp.toPath())) { byte[] b = new byte[1 << 16]; int k; long n = 0; while ((k = in.read(b)) > 0) { md.update(b, 0, k); o.write(b, 0, k); n += k; i.progDone = n; } }
        finally { i.progWhat = null; }
        String got = HexFormat.of().formatHex(md.digest()), want = Json.s(m, "sha256", null);
        try {
            if (want != null && !want.equalsIgnoreCase(got)) throw new Api.Err("backup_sha_mismatch");
            Path d = H.inst.dir(i).toPath().toAbsolutePath().normalize();
            Archive.Progress pr = new Archive.Progress(); i.progPr = pr; Instances.prog(i, "central_restore", -1);
            long[] x; try { x = Archive.extract(tmp.toPath(), "zip", d, d, new Archive.Limits(Math.max(i.disk_gb, 1) * 1073741824L * 2, 2_000_000), null, pr); } finally { i.progWhat = null; i.progPr = null; }
            i.state = "stopped"; i.diskBytes = -1; H.integrity.snap(i);
            H.inst.line(i, "[MinesyncHost] servidor recebido da central (migração): " + x[0] + " arquivos");
            Map<String, Object> r = new LinkedHashMap<>(); r.put("files", x[0]); r.put("sha256", got); return r;
        } finally { tmp.delete(); }
    }
    /**
     * 2.1 TÚNEL (proxy reverso): um pedido HTTP do site (url/servers/&lt;nome&gt;/api/&lt;túnel&gt;/…) vai para a porta interna de UM
     * servidor desta host. Só portas que estão de fato escutando por um processo DAQUELE servidor (nunca o painel, nunca outro
     * servidor, nunca outro programa do PC). Corpo de ida e de volta em fluxo pela central.
     */
    private Map<String, Object> proxy(JsonObject m) throws Exception {
        Instances.Inst i = inst(m); int port = (int) Json.n(m, "port", 0);
        if (port < 1 || port > 65535) throw new Api.Err("bad_port");
        if (i.proc == null || !i.proc.isAlive()) throw new Api.Err("server_offline", 503);
        if (!Ports.ownedBy(port, i.proc.toHandle())) throw new Api.Err("port_not_listening", 502);
        String method = Json.s(m, "method", "GET").toUpperCase(java.util.Locale.ROOT); if (!method.matches("GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS")) throw new Api.Err("bad_method");
        String path = Json.s(m, "path", "/"); if (!path.startsWith("/") || path.contains("\r") || path.contains("\n") || path.length() > 4000) throw new Api.Err("bad_path");
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofMinutes(10));
        if (m.has("headers") && m.get("headers").isJsonObject()) for (Map.Entry<String, JsonElement> e : m.getAsJsonObject("headers").entrySet()) {
            String k = e.getKey().toLowerCase(java.util.Locale.ROOT);
            if (k.matches("host|connection|content-length|transfer-encoding|upgrade|expect|keep-alive|te|trailer|proxy-.*|http2-settings|cookie|authorization|x-fl-token") || !k.matches("[a-z0-9-]{1,64}")) continue;
            try { b.header(e.getKey(), e.getValue().getAsString()); } catch (Exception ignored) { }
        }
        String pin = Json.s(m, "pipe_in", null), pout = Json.s(m, "pipe_out", "");
        if (pin != null) {
            HttpResponse<InputStream> g = http.send(HttpRequest.newBuilder(URI.create(url(pin))).timeout(Duration.ofHours(1)).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
            if (g.statusCode() != 200) throw new Api.Err("pipe_failed");
            InputStream gi = g.body(); b.method(method, HttpRequest.BodyPublishers.ofInputStream(() -> gi));
        } else b.method(method, HttpRequest.BodyPublishers.noBody());
        HttpResponse<InputStream> rs;
        try { rs = http.send(b.build(), HttpResponse.BodyHandlers.ofInputStream()); } catch (Exception e) { throw new Api.Err("service_unreachable", 502); }
        Map<String, Object> hh = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : rs.headers().map().entrySet()) { String k = e.getKey().toLowerCase(java.util.Locale.ROOT); if (k.matches("content-type|content-encoding|cache-control|etag|last-modified|location|content-disposition|content-language|expires|vary|x-[a-z0-9-]+") && !e.getValue().isEmpty()) hh.put(k, e.getValue().get(0)); }
        String enc = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(Json.str(hh).getBytes(StandardCharsets.UTF_8));
        InputStream body = rs.body();
        HttpResponse<String> up = http.send(HttpRequest.newBuilder(URI.create(url(pout))).timeout(Duration.ofMinutes(10)).header("Content-Type", "application/octet-stream").header("X-Proxy-Status", String.valueOf(rs.statusCode())).header("X-Proxy-Headers", enc)
            .POST(HttpRequest.BodyPublishers.ofInputStream(() -> body)).build(), HttpResponse.BodyHandlers.ofString());
        Map<String, Object> r = new LinkedHashMap<>(); r.put("status", rs.statusCode()); r.put("delivered", up.statusCode() == 200); return r;
    }
    boolean connected() { WebSocket w = ws; return w != null && !w.isOutputClosed(); }
    static boolean exists(File f) { return Files.exists(f.toPath(), LinkOption.NOFOLLOW_LINKS); }
}
