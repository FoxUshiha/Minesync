package net.foxsrv.host;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;

/**
 * PAINEL WEB (http://127.0.0.1:8765): uma página só (panel.html) + API JSON. Login com sessão (cookie HttpOnly,
 * SameSite=Strict), toda ação com o cabeçalho X-MSH (outro site não consegue mandar), cabeçalhos de segurança (CSP,
 * sem iframe). Dono (admin) vê e muda tudo; os outros usuários só os próprios servidores.
 */
final class Panel {
    private final MinesyncHost H; private HttpServer srv;
    Panel(MinesyncHost h) { this.H = h; }

    void start() throws Exception {
        String host = H.conf.str("panel.host", "127.0.0.1"); int port = (int) H.conf.num("panel.port", 8765);
        srv = HttpServer.create(new InetSocketAddress(host, port), 64);
        srv.setExecutor(Executors.newFixedThreadPool(8, r -> { Thread t = new Thread(r, "msh-http"); t.setDaemon(true); return t; }));
        srv.createContext("/", this::handle);
        srv.start();
        MinesyncHost.log("painel: http://" + ("0.0.0.0".equals(host) ? "localhost" : host) + ":" + port);
    }
    void stop() { if (srv != null) srv.stop(0); }

    // ------------------------------------------------------------------ http
    private static final Set<String> STATIC = Set.of("/", "/panel.html", "/cloudflare.png", "/icon.png");
    /** 2.0: pedido vindo da CENTRAL (agente): roda as mesmas rotas como o usuário "central" */
    static final ThreadLocal<Users.User> AS = new ThreadLocal<>();
    static final Users.User CENTRAL = centralUser();
    private static Users.User centralUser() { Users.User u = new Users.User(); u.name = "central"; u.admin = true; return u; }
    void runCentral(CentralExchange x) { AS.set(CENTRAL); try { handle(x); } finally { AS.remove(); } }
    private void handle(HttpExchange x) {
        try {
            sec(x);
            String path = x.getRequestURI().getPath();
            if (!path.startsWith("/api/")) { serveStatic(x, path); return; }
            Object out = route(x, path);
            if (out instanceof byte[]) return;   // já respondido (download)
            json(x, 200, out);
        } catch (Api.Err e) { json(x, e.status, Map.of("ok", false, "error", e.getMessage())); }
        catch (Throwable t) { MinesyncHost.log("painel: " + t); json(x, 500, Map.of("ok", false, "error", "internal_error")); }
        finally { Files2.RELAX.remove(); x.close(); }
    }
    private static void sec(HttpExchange x) {
        var h = x.getResponseHeaders();
        h.set("X-Frame-Options", "DENY"); h.set("X-Content-Type-Options", "nosniff"); h.set("Referrer-Policy", "no-referrer");
        h.set("Content-Security-Policy", "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline'; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'");
        h.set("Cache-Control", "no-store");
    }
    private void serveStatic(HttpExchange x, String path) throws Exception {
        if (!STATIC.contains(path)) { json(x, 404, Map.of("ok", false, "error", "not_found")); return; }
        String res = path.equals("/") ? "/panel.html" : path;
        try (InputStream in = Panel.class.getResourceAsStream("/web" + res)) {
            if (in == null) { json(x, 404, Map.of("ok", false)); return; }
            byte[] b = in.readAllBytes();
            x.getResponseHeaders().set("Content-Type", res.endsWith(".html") ? "text/html; charset=utf-8" : "image/png");
            x.sendResponseHeaders(200, b.length); try (OutputStream o = x.getResponseBody()) { o.write(b); }
        }
    }
    private static void json(HttpExchange x, int code, Object o) {
        try { byte[] b = Json.str(o).getBytes(StandardCharsets.UTF_8); x.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8"); x.sendResponseHeaders(code, b.length); try (OutputStream out = x.getResponseBody()) { out.write(b); } } catch (Exception ignored) { }
    }
    private static byte[] body(HttpExchange x, long max) throws Exception {
        try (InputStream in = x.getRequestBody()) {
            ByteArrayOutputStream b = new ByteArrayOutputStream(); byte[] buf = new byte[65536]; int n; long t = 0;
            while ((n = in.read(buf)) > 0) { t += n; if (t > max) throw new Api.Err("too_big", 413); b.write(buf, 0, n); }
            return b.toByteArray();
        }
    }
    private static JsonObject jbody(HttpExchange x) throws Exception { return Json.obj(new String(body(x, 1 << 20), StandardCharsets.UTF_8)); }
    private static Map<String, String> query(HttpExchange x) {
        Map<String, String> m = new LinkedHashMap<>(); String q = x.getRequestURI().getRawQuery(); if (q == null) return m;
        for (String p : q.split("&")) { int i = p.indexOf('='); if (i > 0) m.put(URLDecoder.decode(p.substring(0, i), StandardCharsets.UTF_8), URLDecoder.decode(p.substring(i + 1), StandardCharsets.UTF_8)); }
        return m;
    }
    private static String cookie(HttpExchange x, String name) {
        for (String c : x.getRequestHeaders().getOrDefault("Cookie", List.of())) for (String p : c.split(";")) { String t = p.trim(); if (t.startsWith(name + "=")) return t.substring(name.length() + 1); }
        return null;
    }
    private static String ip(HttpExchange x) {
        String cf = x.getRequestHeaders().getFirst("CF-Connecting-IP");
        String rem = x.getRemoteAddress().getAddress().getHostAddress();
        return cf != null && x.getRemoteAddress().getAddress().isLoopbackAddress() ? cf : rem;   // pelo túnel: o IP real que a Cloudflare informa
    }
    private static boolean https(HttpExchange x) { return "https".equalsIgnoreCase(x.getRequestHeaders().getFirst("X-Forwarded-Proto")) || x.getRequestHeaders().getFirst("CF-Connecting-IP") != null; }

    // ------------------------------------------------------------------ rotas
    private Object route(HttpExchange x, String path) throws Exception {
        String m = x.getRequestMethod();
        if (!"GET".equals(m) && !"1".equals(x.getRequestHeaders().getFirst("X-MSH"))) throw new Api.Err("csrf", 403);
        // 2.1: o painel LOCAL só vincula a conta e troca o endereço da central — todo o resto é pela central (site)
        if (AS.get() == null) return local(x, path, m);
        Users.User u = AS.get() != null ? AS.get() : H.users.session(cookie(x, "msh"));
        if (path.equals("/api/state")) return state(u);
        if (path.equals("/api/setup") && "POST".equals(m)) {
            synchronized (H.users) { if (!H.users.empty()) throw new Api.Err("already_setup", 403); JsonObject b = jbody(x); H.users.add(Json.s(b, "user", ""), Json.s(b, "pass", ""), true); return login(x, Json.s(b, "user", ""), Json.s(b, "pass", "")); }
        }
        if (path.equals("/api/login") && "POST".equals(m)) { JsonObject b = jbody(x); return login(x, Json.s(b, "user", ""), Json.s(b, "pass", "")); }
        // ---- 1.3: criar conta (se o dono liberou) e ENTRAR COM A CONTA DA REDE (pelo site ou usuário/senha da central)
        if (path.equals("/api/signup") && "POST".equals(m)) {
            if (!H.conf.bool("accounts.signup", false)) throw new Api.Err("signup_disabled", 403);
            JsonObject b = jbody(x); String code = H.conf.str("accounts.signup_code", "").trim();
            if (!code.isEmpty() && !java.security.MessageDigest.isEqual(code.getBytes(StandardCharsets.UTF_8), Json.s(b, "code", "").trim().getBytes(StandardCharsets.UTF_8))) throw new Api.Err("bad_invite_code", 403);
            H.users.throttle(ip(x)); H.users.failed(ip(x));   // conta criação como tentativa (freio contra robôs)
            H.users.add(Json.s(b, "user", ""), Json.s(b, "pass", ""), false); return login(x, Json.s(b, "user", ""), Json.s(b, "pass", ""));
        }
        if (path.equals("/api/auth/net/start") && "POST".equals(m)) { if (!H.conf.bool("accounts.network_login", true)) throw new Api.Err("network_login_disabled", 403); H.users.throttle(ip(x)); String id = H.central.start("login", ""); return Map.of("ok", true, "id", id, "url", H.central.pend.get(id).url); }
        if (path.equals("/api/auth/net/poll") && "POST".equals(m)) {
            Central.Pending p = H.central.get(Json.s(jbody(x), "id", ""), "login", "");
            if (!"ok".equals(p.state)) return Map.of("ok", true, "state", p.state, "error", p.error == null ? "" : p.error);
            H.central.pend.values().remove(p); return netLogin(x, p.who);
        }
        if (path.equals("/api/auth/net/login") && "POST".equals(m)) {
            if (!H.conf.bool("accounts.network_login", true)) throw new Api.Err("network_login_disabled", 403);
            String ipx = ip(x); H.users.throttle(ipx); JsonObject b = jbody(x); Central.Who w;
            try { w = H.central.login(Json.s(b, "user", ""), Json.s(b, "pass", "")); } catch (Api.Err e) { if (e.status == 401) H.users.failed(ipx); throw e; }
            return netLogin(x, w);
        }
        if (path.equals("/api/logout") && "POST".equals(m)) { H.users.logout(cookie(x, "msh")); x.getResponseHeaders().add("Set-Cookie", "msh=; Path=/; Max-Age=0; HttpOnly; SameSite=Strict"); return Map.of("ok", true); }
        if (u == null) throw new Api.Err("not_logged_in", 401);
        if (path.equals("/api/versions")) return Map.of("ok", true, "versions", H.inst.versions(query(x).getOrDefault("type", "paper")));
        if (path.equals("/api/servers") && "GET".equals(m)) { List<Object> l = new ArrayList<>(); for (Instances.Inst i : H.inst.all.values()) if (H.accounts.sees(u, i)) { Map<String, Object> v = H.inst.view(i); if (i.central && AS.get() == null) H.inst.anonymize(v); v.put("perms", i.central && AS.get() == null ? List.of() : H.accounts.serverPerms(u, i)); l.add(v); } return Map.of("ok", true, "servers", l, "free", H.inst.free(), "account", H.accounts.view(u), "node", H.inst.nodeInfo()); }
        if (path.equals("/api/servers") && "POST".equals(m)) { Instances.Inst ni = H.inst.create(u, jbody(x)); H.activity.log(ni, u, "server.create", ni.type + " · " + ni.owner); return Map.of("ok", true, "server", H.inst.view(ni)); }
        if (path.equals("/api/runtimes")) return Map.of("ok", true, "runtimes", H.inst.runtimes());
        // ---- 1.3: minha conta
        if (path.equals("/api/me") && "GET".equals(m)) return Map.of("ok", true, "account", H.accounts.view(u));
        if (path.equals("/api/me/password") && "POST".equals(m)) { JsonObject b = jbody(x); if (!H.users.check(u, Json.s(b, "old", ""))) throw new Api.Err("invalid_login", 401); H.users.setPassword(u.name, Json.s(b, "pass", "")); return Map.of("ok", true); }
        if (path.equals("/api/me/central/start") && "POST".equals(m)) { String id = H.central.start("bind", u.name); return Map.of("ok", true, "id", id, "url", H.central.pend.get(id).url); }
        if (path.equals("/api/me/central/poll") && "POST".equals(m)) {
            Central.Pending p = H.central.get(Json.s(jbody(x), "id", ""), "bind", u.name);
            if (!"ok".equals(p.state)) return Map.of("ok", true, "state", p.state, "error", p.error == null ? "" : p.error);
            H.central.pend.values().remove(p); H.central.logout(p.who.token); bindCentral(u, p.who); return Map.of("ok", true, "state", "ok", "account", H.accounts.view(u));
        }
        if (path.equals("/api/me/central/login") && "POST".equals(m)) { String ipx = ip(x); H.users.throttle(ipx); JsonObject b = jbody(x); Central.Who w; try { w = H.central.login(Json.s(b, "user", ""), Json.s(b, "pass", "")); } catch (Api.Err e) { if (e.status == 401) H.users.failed(ipx); throw e; } H.central.logout(w.token); bindCentral(u, w); return Map.of("ok", true, "account", H.accounts.view(u)); }
        if (path.equals("/api/me/central/unlink") && "POST".equals(m)) { u.central = null; u.central_name = null; H.users.persist(); return Map.of("ok", true, "account", H.accounts.view(u)); }
        if (path.startsWith("/api/servers/")) return server(x, u, path.substring("/api/servers/".length()));
        // ---- só o dono da host
        if (!u.admin) throw new Api.Err("forbidden", 403);
        if (path.equals("/api/hosting") && "POST".equals(m)) { boolean on = Json.b(jbody(x), "on", false); H.conf.set("enabled", on); H.conf.save(); if (!on) H.inst.stopAll(false); H.net.helloSoon(); return state(u); }
        if (path.equals("/api/config") && "POST".equals(m)) { patch(jbody(x)); return state(u); }
        if (path.equals("/api/cloud") && "POST".equals(m)) {
            JsonObject b = jbody(x);
            if (b.has("enabled")) H.conf.set("cloudflare.enabled", Json.b(b, "enabled", false));
            if (b.has("mode")) H.conf.set("cloudflare.mode", "token".equals(Json.s(b, "mode", "")) ? "token" : "quick");
            if (b.has("token")) { String t = Json.s(b, "token", "").trim(); if (!t.isEmpty() && !t.matches("[A-Za-z0-9_=.+/-]{40,}")) throw new Api.Err("bad_token"); H.conf.set("cloudflare.token", t); }
            if (b.has("hostname")) { String hn = Json.s(b, "hostname", "").trim().toLowerCase(); if (!hn.isEmpty() && !hn.matches("[a-z0-9.-]{3,200}")) throw new Api.Err("bad_hostname"); H.conf.set("cloudflare.hostname", hn); }
            H.conf.save(); H.cloud.start(); return state(u);
        }
        // 1.3: vincular a HOST à conta da rede sem código: pelo site (só confirmar) ou com usuário e senha da central
        if (path.equals("/api/net/browser/start") && "POST".equals(m)) { String id = H.central.start("host", u.name); return Map.of("ok", true, "id", id, "url", H.central.pend.get(id).url); }
        if (path.equals("/api/net/browser/poll") && "POST".equals(m)) {
            Central.Pending p = H.central.get(Json.s(jbody(x), "id", ""), "host", u.name);
            if (!"ok".equals(p.state)) return Map.of("ok", true, "state", p.state, "error", p.error == null ? "" : p.error);
            H.central.pend.values().remove(p); H.central.linkHost(p.who); Map<String, Object> st = state(u); st.put("state", "ok"); return st;
        }
        if (path.equals("/api/net/login") && "POST".equals(m)) { String ipx = ip(x); H.users.throttle(ipx); JsonObject b = jbody(x); Central.Who w; try { w = H.central.login(Json.s(b, "user", ""), Json.s(b, "pass", "")); } catch (Api.Err e) { if (e.status == 401) H.users.failed(ipx); throw e; } H.central.linkHost(w); return state(u); }
        // 1.3: Cloudflare pelo navegador (login → escolher o endereço → túnel criado e ligado)
        if (path.equals("/api/cloud/login") && "POST".equals(m)) { H.cloud.loginStart(); return Map.of("ok", true, "cloud", H.cloud.view()); }
        if (path.equals("/api/cloud/status")) return Map.of("ok", true, "cloud", H.cloud.view());
        if (path.equals("/api/cloud/setup") && "POST".equals(m)) { H.cloud.setup(Json.s(jbody(x), "hostname", "")); return Map.of("ok", true, "cloud", H.cloud.view()); }
        if (path.equals("/api/cloud/logout") && "POST".equals(m)) { H.cloud.logout(); return Map.of("ok", true, "cloud", H.cloud.view()); }
        if (path.equals("/api/users/update") && "POST".equals(m)) { JsonObject b = jbody(x); Users.User t = H.users.get(Json.s(b, "user", "")); if (t == null) throw new Api.Err("not_found", 404); H.accounts.update(t, b, u); return Map.of("ok", true, "users", usersList()); }
        if (path.equals("/api/net/link") && "POST".equals(m)) { H.net.link(Json.s(jbody(x), "code", "")); return state(u); }
        if (path.equals("/api/net/unlink") && "POST".equals(m)) { H.net.unlink(); return state(u); }
        if (path.equals("/api/users") && "GET".equals(m)) return Map.of("ok", true, "users", usersList());
        if (path.equals("/api/users") && "POST".equals(m)) { JsonObject b = jbody(x); H.users.add(Json.s(b, "user", ""), Json.s(b, "pass", ""), Json.b(b, "admin", false)); return Map.of("ok", true, "users", usersList()); }
        if (path.equals("/api/users/delete") && "POST".equals(m)) { String n = Json.s(jbody(x), "user", ""); if (n.equalsIgnoreCase(u.name)) throw new Api.Err("cannot_delete_self"); H.users.remove(n); return Map.of("ok", true, "users", usersList()); }
        if (path.equals("/api/users/backup-limits") && "POST".equals(m)) { JsonObject b = jbody(x); H.users.setBackupLimits(Json.s(b, "user", ""), optLong(b, "max_count"), optLong(b, "max_mb")); return Map.of("ok", true, "users", usersList()); }
        if (path.equals("/api/users/password") && "POST".equals(m)) { JsonObject b = jbody(x); H.users.setPassword(Json.s(b, "user", u.name), Json.s(b, "pass", "")); return Map.of("ok", true); }
        throw new Api.Err("not_found", 404);
    }
    // ------------------------------------------------------------------ 2.1: painel local (só entrar/vincular)
    /** só deste computador: nada de túnel, nada de rede local, e o endereço tem que ser localhost (contra DNS rebinding) */
    private static void localOnly(HttpExchange x) {
        var a = x.getRemoteAddress().getAddress();
        if (!a.isLoopbackAddress() || x.getRequestHeaders().getFirst("CF-Connecting-IP") != null || x.getRequestHeaders().getFirst("X-Forwarded-For") != null) throw new Api.Err("local_only", 403);
        String host = String.valueOf(x.getRequestHeaders().getFirst("Host")).toLowerCase(java.util.Locale.ROOT).replaceAll(":\\d+$", "");
        if (!host.equals("localhost") && !host.equals("127.0.0.1") && !host.equals("[::1]")) throw new Api.Err("local_only", 403);
    }
    private Map<String, Object> localState() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true); m.put("version", MinesyncHost.VERSION); m.put("language", H.conf.str("language", "auto"));
        m.put("central", H.conf.central()); m.put("central_default", "https://www.foxsrv.net");
        m.put("linked", H.net.linked); m.put("owner", H.net.owner); m.put("host_id", H.net.id); m.put("net_error", H.net.lastError); m.put("last_ok", H.net.lastOk);
        m.put("agent", Map.of("state", H.agent.state, "error", H.agent.error, "connected", H.agent.connected(), "since", H.agent.connectedAt));
        m.put("machine", H.bench.view()); m.put("node", H.node.info());
        m.put("isolation", H.inst.isolatedLinux() ? "strong" : "basic"); m.put("bwrap_missing", Sys.linux() && Sys.which("bwrap") == null);
        long n = 0, run = 0; for (Instances.Inst i : H.inst.all.values()) if (i.central) { n++; if ("running".equals(i.state)) run++; }
        m.put("servers", n); m.put("running", run);
        m.put("site", H.conf.central() + "/servers/host.html#machines");
        return m;
    }
    private Object local(HttpExchange x, String path, String m) throws Exception {
        localOnly(x);
        if (path.equals("/api/state")) return localState();
        if (!"POST".equals(m)) throw new Api.Err("not_found", 404);
        if (path.equals("/api/language")) { String l = Json.s(jbody(x), "language", "auto"); H.conf.set("language", l.matches("pt|en|auto") ? l : "auto"); H.conf.save(); return localState(); }
        // endereço da central (com o botão de voltar para a padrão)
        if (path.equals("/api/central")) {
            JsonObject b = jbody(x); String c = Json.b(b, "reset", false) ? "https://www.foxsrv.net/" : Json.s(b, "central", "").trim();
            if (!c.matches("^https?://[A-Za-z0-9.:-]{3,200}(/[A-Za-z0-9._~/-]*)?$")) throw new Api.Err("bad_central");
            if (!c.endsWith("/")) c = c + "/";
            if (!c.equalsIgnoreCase(H.conf.central() + "/")) { H.conf.set("central", c); H.conf.save(); if (H.net.linked) H.net.unlink(); H.agent.reconnect(); H.net.helloSoon(); }
            return localState();
        }
        // vincular pelo site (a pessoa só confirma no site, já logada)
        if (path.equals("/api/link/site/start")) { String id = H.central.start("host", "local"); return Map.of("ok", true, "id", id, "url", H.central.pend.get(id).url); }
        if (path.equals("/api/link/site/poll")) {
            Central.Pending p = H.central.get(Json.s(jbody(x), "id", ""), "host", "local");
            if (!"ok".equals(p.state)) return Map.of("ok", true, "state", p.state, "error", p.error == null ? "" : p.error);
            H.central.pend.values().remove(p); H.central.linkHost(p.who); H.agent.reconnect(); Map<String, Object> st = localState(); st.put("state", "ok"); return st;
        }
        // usuário e senha da central (a senha passa direto para a central e não fica guardada)
        if (path.equals("/api/link/login")) {
            String ipx = ip(x); H.users.throttle(ipx); JsonObject b = jbody(x); Central.Who w;
            try { w = H.central.login(Json.s(b, "user", ""), Json.s(b, "pass", "")); } catch (Api.Err e) { if (e.status == 401) H.users.failed(ipx); throw e; }
            H.central.linkHost(w); H.agent.reconnect(); return localState();
        }
        // código gerado no site (Hosts › Minhas hosts › Gerar código)
        if (path.equals("/api/link/code")) { H.users.throttle(ip(x)); H.net.link(Json.s(jbody(x), "code", "")); H.agent.reconnect(); return localState(); }
        if (path.equals("/api/unlink")) { H.net.unlink(); H.conf.set("managed.owner_pct", 0L); H.conf.save(); H.agent.reconnect(); return localState(); }
        if (path.equals("/api/bench")) { H.bench.runAsync(H.agent::statsSoon); return localState(); }
        throw new Api.Err("not_found", 404);
    }
    /** 1.2: usuários + limites e uso de backup */
    private List<Map<String, Object>> usersList() { List<Map<String, Object>> l = H.users.list(); for (Map<String, Object> m : l) { Users.User x = H.users.get(String.valueOf(m.get("name"))); m.put("backup", H.backups.quota(String.valueOf(m.get("name")))); if (x != null) { Map<String, Object> v = H.accounts.view(x); m.put("eff_perms", v.get("perms")); m.put("eff_types", v.get("types")); m.put("limits", v.get("limits")); m.put("usage", v.get("usage")); } } return l; }
    /** 1.3: entrou pela conta da rede → a conta da host vinculada (ou uma nova, se o dono liberou) */
    private Object netLogin(HttpExchange x, Central.Who w) {
        H.central.logout(w.token);
        Users.User u = H.users.byCentral(w.username);
        if (u == null) {
            if (H.users.empty()) throw new Api.Err("setup_first", 403);
            if (!H.conf.bool("accounts.network_signup", false)) { Map<String, Object> r = new LinkedHashMap<>(); r.put("ok", false); r.put("error", "no_account"); r.put("central", w.username); return r; }
            synchronized (H.users) { u = H.users.add(H.users.freeName(w.username), Users.rnd(24), false); u.central = w.username.toLowerCase(java.util.Locale.ROOT); u.central_name = w.username; H.users.persist(); }
            MinesyncHost.log("conta criada pela rede: " + u.name + " (" + w.username + ")");
        }
        String t = H.users.loginAs(u, ip(x));
        x.getResponseHeaders().add("Set-Cookie", "msh=" + t + "; Path=/; Max-Age=" + (Users.TTL / 1000) + "; HttpOnly; SameSite=Strict" + (https(x) ? "; Secure" : ""));
        Map<String, Object> st = state(u); st.put("state", "ok"); return st;
    }
    private void bindCentral(Users.User u, Central.Who w) {
        Users.User o = H.users.byCentral(w.username); if (o != null && o != u) throw new Api.Err("central_in_use");
        u.central = w.username.toLowerCase(java.util.Locale.ROOT); u.central_name = w.username; H.users.persist();
    }
    private static Long optLong(JsonObject b, String k) { if (!b.has(k) || b.get(k).isJsonNull()) return null; String s = b.get(k).getAsString().trim(); if (s.isEmpty() || s.equals("-1")) return null; try { return Long.parseLong(s); } catch (NumberFormatException e) { throw new Api.Err("bad_limit"); } }
    private Object login(HttpExchange x, String user, String pass) {
        String t = H.users.login(user, pass, ip(x));
        x.getResponseHeaders().add("Set-Cookie", "msh=" + t + "; Path=/; Max-Age=" + (Users.TTL / 1000) + "; HttpOnly; SameSite=Strict" + (https(x) ? "; Secure" : ""));
        return state(H.users.get(user));
    }
    /** só estas chaves do config.yml mudam pelo painel (e com conferência) */
    private void patch(JsonObject b) {
        for (Map.Entry<String, JsonElement> e : b.entrySet()) {
            String k = e.getKey(); JsonElement v = e.getValue();
            switch (k) {
                case "language": { String l = v.getAsString(); if (List.of("auto", "pt", "en").contains(l)) H.conf.set(k, l); break; }
                case "central": { String c = v.getAsString().trim(); if (c.isEmpty() || c.equals("default")) c = "https://www.foxsrv.net/"; if (!c.matches("^https://[A-Za-z0-9.-]+(:\\d+)?(/.*)?$")) throw new Api.Err("bad_central"); H.conf.set(k, c); break; }
                case "isolated": H.conf.set(k, v.getAsBoolean()); break;
                case "limits.max_ram_mb": { long r = v.getAsLong(); if (r < 800 || r > Sys.totalRamMb()) throw new Api.Err("bad_ram"); H.conf.set(k, r); break; }
                case "limits.max_cores": { long c = v.getAsLong(); if (c < 1 || c > Runtime.getRuntime().availableProcessors()) throw new Api.Err("bad_cores"); H.conf.set(k, c); break; }
                case "limits.max_disk_gb": { long d = v.getAsLong(); if (d < 4 || d > 1 << 20) throw new Api.Err("bad_disk"); H.conf.set(k, d); break; }
                case "limits.bandwidth_mbps": H.conf.set(k, Math.max(0, v.getAsLong())); break;
                case "network.share_with_network": case "network.install_minesync": case "network.bridge": case "network.bridge_lan": case "network.prices.free": case "network.prices.bot_credits": case "network.trial.on": case "network.users_can_create": case "public_ip.enabled": H.conf.set(k, v.getAsBoolean()); break;
                case "network.name": { String n = v.getAsString().replaceAll("[<>\"]", "").trim(); if (n.isEmpty() || n.length() > 40) throw new Api.Err("bad_name"); H.conf.set(k, n); break; }
                case "network.prices.per_core": case "network.prices.per_gb_ram": H.conf.set(k, Math.max(0, Math.min(1e6, v.getAsDouble()))); break;
                case "network.prices.currencies": { List<Object> l = new ArrayList<>(); for (JsonElement c : v.getAsJsonArray()) { String s = c.getAsString().toUpperCase(); if (List.of("COIN", "BIPO", "USD", "BTC", "BRL").contains(s) && !l.contains(s)) l.add(s); } H.conf.set(k, l); break; }
                case "network.trial.ram_mb": H.conf.set(k, Math.max(800, Math.min(65536, v.getAsLong()))); break;
                case "network.trial.cores": H.conf.set(k, Math.max(1, Math.min(32, v.getAsLong()))); break;
                case "network.trial.days": H.conf.set(k, Math.max(1, Math.min(365, v.getAsLong()))); break;
                case "network.kinds": { List<Object> l = new ArrayList<>(); for (JsonElement c : v.getAsJsonArray()) { String s = c.getAsString().toLowerCase(); if (List.of("java", "horizon", "js").contains(s) && !l.contains(s)) l.add(s); } if (l.isEmpty()) l.add("java"); H.conf.set(k, l); break; }
                case "backups.enabled": H.conf.set(k, v.getAsBoolean()); break;
                case "backups.max_count": H.conf.set(k, lim(v, 0, 1000)); break;
                case "backups.max_mb": H.conf.set(k, lim(v, 0, 1L << 24)); break;
                case "backups.max_total_gb": H.conf.set(k, lim(v, 0, 1L << 20)); break;
                case "backups.min_every_h": H.conf.set(k, lim(v, 1, 720)); break;
                case "archive.max_extract_mb": H.conf.set(k, lim(v, 16, 1L << 20)); break;
                case "archive.max_entries": H.conf.set(k, lim(v, 100, 10_000_000)); break;
                case "accounts.signup": case "accounts.network_login": case "accounts.network_signup": case "network.agent": H.conf.set(k, v.getAsBoolean()); break;
                case "node.os_reserve.ram_mb": case "node.owner_reserve.ram_mb": case "node.instance_max.ram_mb": H.conf.set(k, lim(v, 0, 1L << 22)); break;
                case "node.os_reserve.cores": case "node.owner_reserve.cores": case "node.instance_max.cores": H.conf.set(k, lim(v, 0, 1024)); break;
                case "node.os_reserve.disk_gb": case "node.owner_reserve.disk_gb": case "node.instance_max.disk_gb": H.conf.set(k, lim(v, 0, 1L << 20)); break;
                case "network.prices.per_gb_disk": H.conf.set(k, Math.max(0, Math.min(1e6, v.getAsDouble()))); break;
                case "network.bandwidth_mbps": H.conf.set(k, lim(v, 0, 100000)); break;
                case "accounts.signup_code": { String c = v.getAsString().trim(); if (c.length() > 40 || !c.matches("[A-Za-z0-9_.-]*")) throw new Api.Err("bad_code"); H.conf.set(k, c); break; }
                case "accounts.default.perms": { List<Object> l = new ArrayList<>(); for (JsonElement c : v.getAsJsonArray()) { String s = c.getAsString(); if (Accounts.ACCOUNT_PERMS.contains(s) && !l.contains(s)) l.add(s); } H.conf.set(k, l); break; }
                case "accounts.default.types": { List<Object> l = new ArrayList<>(); for (JsonElement c : v.getAsJsonArray()) { String s = c.getAsString(); if (List.of(Instances.TYPES).contains(s) && !l.contains(s)) l.add(s); } H.conf.set(k, l); break; }
                case "accounts.default.max_servers": H.conf.set(k, lim(v, 0, 10000)); break;
                case "accounts.default.max_ram_mb": H.conf.set(k, lim(v, 0, 1L << 22)); break;
                case "accounts.default.max_cores": H.conf.set(k, lim(v, 0, 1024)); break;
                case "accounts.default.max_disk_gb": H.conf.set(k, lim(v, 0, 1L << 20)); break;
                case "runtimes.java": { List<Object> l = new ArrayList<>(); for (JsonElement c : v.getAsJsonArray()) { String s = c.getAsString().trim(); if (s.isEmpty()) continue; int q = s.indexOf('='); if (q < 1 || !s.substring(0, q).trim().matches("[A-Za-z0-9._-]{1,24}") || !new File(s.substring(q + 1).trim()).isFile()) throw new Api.Err("bad_runtime"); l.add(s.substring(0, q).trim() + "=" + s.substring(q + 1).trim()); } H.conf.set(k, l); break; }
                case "runtimes.node": case "runtimes.python": { String s = v.getAsString().trim(); if (!s.isEmpty() && !new File(s).isFile()) throw new Api.Err("bad_runtime"); H.conf.set(k, s); break; }
                case "public_ip.address": { String a = v.getAsString().trim(); if (!a.isEmpty() && !a.matches("[A-Za-z0-9.:-]{3,253}")) throw new Api.Err("bad_address"); H.conf.set(k, a); break; }
                case "public_ip.ports": { String p = v.getAsString().trim(); if (!p.matches("\\d{2,5}(-\\d{2,5})?")) throw new Api.Err("bad_ports"); H.conf.set(k, p); break; }
                default: throw new Api.Err("bad_key: " + k);
            }
        }
        H.conf.save(); H.net.helloSoon();
    }
    private static long lim(JsonElement v, long min, long max) { long n; try { n = v.getAsLong(); } catch (Exception e) { throw new Api.Err("bad_limit"); } if (n < min || n > max) throw new Api.Err("bad_limit"); return n; }
    private Map<String, Object> state(Users.User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true); m.put("version", MinesyncHost.VERSION); m.put("setup_needed", H.users.empty());
        m.put("user", u == null ? null : Map.of("name", u.name, "admin", u.admin));
        m.put("signup", H.conf.bool("accounts.signup", false)); m.put("signup_code", !H.conf.str("accounts.signup_code", "").isBlank()); m.put("network_login", H.conf.bool("accounts.network_login", true)); m.put("host_name", H.conf.str("network.name", "MinesyncHost"));
        if (u == null) return m;
        m.put("account", H.accounts.view(u));
        m.put("enabled", H.conf.bool("enabled", false)); m.put("language", H.conf.str("language", "auto"));
        Map<String, Object> sys = new LinkedHashMap<>();
        sys.put("os", System.getProperty("os.name") + " " + System.getProperty("os.version") + " · " + Sys.arch()); sys.put("java", System.getProperty("java.version"));
        sys.put("ram_mb", Sys.totalRamMb()); sys.put("free_ram_mb", Sys.freeRamMb()); sys.put("cores", Runtime.getRuntime().availableProcessors()); sys.put("cpu", Math.round(Sys.cpuLoad() * 1000) / 10.0);
        sys.put("bwrap", Sys.linux() && Sys.which("bwrap") != null); sys.put("isolation", H.inst.isolatedLinux() ? "strong" : H.conf.bool("isolated", true) ? "basic" : "off");
        m.put("system", sys); m.put("free", H.inst.free());
        m.put("node", H.inst.nodeInfo()); m.put("agent", Map.of("state", H.agent.state, "error", H.agent.error, "connected", H.agent.connected()));
        long nc = 0; for (Instances.Inst i : H.inst.all.values()) if (i.central) nc++; m.put("central_servers", nc);
        Map<String, Object> bq = H.backups.quota(u.name); bq.put("enabled", H.backups.enabled()); bq.put("min_every_h", H.conf.num("backups.min_every_h", 1)); m.put("backups", bq);
        if (u.admin) {
            m.put("config", H.conf.snapshot());
            ((Map<String, Object>) m.get("config")).computeIfPresent("cloudflare", (k, v) -> { @SuppressWarnings("unchecked") Map<String, Object> c = (Map<String, Object>) v; c.put("token", String.valueOf(c.get("token")).isEmpty() ? "" : "••••"); return c; });
            m.put("cloud", H.cloud.view()); m.put("runtimes", H.inst.runtimes()); m.put("usernames", H.users.all().stream().map(z -> z.name).sorted(String.CASE_INSENSITIVE_ORDER).toList());
            Map<String, Object> net = new LinkedHashMap<>(); net.put("id", H.net.id); net.put("linked", H.net.linked); net.put("owner", H.net.owner); net.put("last_ok", H.net.lastOk); net.put("error", H.net.lastError); net.put("central", H.conf.central());
            m.put("net", net);
        }
        return m;
    }
    @SuppressWarnings("unchecked")
    private Object server(HttpExchange x, Users.User u, String rest) throws Exception {
        String[] p = rest.split("/", 2); Instances.Inst i = H.inst.all.get(p[0]);
        if (i == null || !H.accounts.sees(u, i)) throw new Api.Err("not_found", 404);
        String act = p.length > 1 ? p[1] : ""; String m = x.getRequestMethod(); File d = H.inst.dir(i);
        // 2.0: servidor da CENTRAL — no painel local só aparece (recursos e estado); quem controla é a central
        if (i.central && AS.get() == null && !(act.isEmpty() && "GET".equals(m))) throw new Api.Err("central_managed", 403);
        if (i.central && AS.get() != null && !"GET".equals(m)) H.integrity.touch(i);
        // 1.3: permissão de cada ação (dono da host: tudo · dono do servidor: quase tudo · subusuário: o que o dono deu)
        String need = perm(act, m);
        if ("owner.delete".equals(need)) { if (!H.accounts.owner(u, i) || !(u.admin || H.accounts.has(u, "delete"))) throw new Api.Err("no_permission", 403); }
        else if ("owner.subusers".equals(need)) { if (!H.accounts.owner(u, i) || !(u.admin || H.accounts.has(u, "subusers"))) throw new Api.Err("no_permission", 403); }
        else if (need != null) H.accounts.need(u, i, need);
        Files2.RELAX.set(Instances.custom(i.type));   // Java/Node/Python próprios: nada protegido (Minecraft: só eula.txt e os argumentos do Forge)
        if (!"GET".equals(m) && !Set.of("jobs", "console", "cmd", "startup", "schedules/save", "subusers/set", "subusers/delete").contains(act)) H.activity.log(i, u, act.isEmpty() ? "view" : act, query(x).getOrDefault("path", ""));
        if (act.equals("startup")) return startup(x, u, i);
        if (act.startsWith("schedules")) return schedules(x, u, i, act);
        if (act.startsWith("subusers")) return subusers(x, u, i, act);
        if (act.equals("activity")) return Map.of("ok", true, "activity", H.activity.of(i.id));
        // 1.2: restaurando um backup — arquivos e ligar ficam bloqueados até terminar
        if (i.restoring && Set.of("start", "restart", "reinstall", "file", "upload", "mkdir", "rename", "rm", "rm-many", "compress", "extract", "delete").contains(act)) throw new Api.Err("busy");
        if (act.startsWith("backups")) return backups(x, u, i, act);
        switch (act) {
            case "jobs": return Map.of("ok", true, "job", H.backups.jobView(i) == null ? Map.of() : H.backups.jobView(i));
            case "rm-many": {
                JsonObject b = jbody(x); int n = 0;
                Path base = d.toPath().toAbsolutePath().normalize(); List<String> ps = paths(b);
                for (String r : ps) { Path q = Files2.safe(d, r); if (q.equals(base) || Files2.protectedName(base, q)) throw new Api.Err("protected_file"); }   // confere tudo antes de apagar qualquer um
                for (String r : ps) { Files2.delete(d, r); n++; }
                return Map.of("ok", true, "removed", n);
            }
            case "compress": {
                JsonObject b = jbody(x); List<String> ps = paths(b); if (ps.isEmpty()) throw new Api.Err("nothing_selected");
                Path base = d.toPath().toAbsolutePath().normalize();
                List<Path> src = new ArrayList<>(); Path parent = null;
                for (String r : ps) { Path q = Files2.safe(d, r); if (q.equals(base)) throw new Api.Err("bad_path"); if (!Files.exists(q, java.nio.file.LinkOption.NOFOLLOW_LINKS)) throw new Api.Err("not_found", 404); if (parent == null) parent = q.getParent(); else if (!parent.equals(q.getParent())) throw new Api.Err("bad_path"); src.add(q); }
                String dn = Json.s(b, "dest", "").trim(); if (dn.isEmpty()) dn = (src.size() == 1 ? src.get(0).getFileName().toString() : "arquivos") + ".zip";
                String kind = Archive.kind(dn); if (kind == null) { dn = dn + ".zip"; kind = "zip"; }
                if (!dn.contains("/")) dn = (base.relativize(parent).toString().replace('\\', '/') + "/" + dn).replaceAll("^/", "");
                Path dest = Files2.safe(d, dn); if (Files2.protectedName(base, dest) || Files.isDirectory(dest)) throw new Api.Err("protected_file");
                long used = Sys.dirSize(base); long allow = i.disk_gb * 1073741824L - used; if (allow <= 0) throw new Api.Err("disk_full");
                final String k2 = kind; final Path part = dest.resolveSibling(dest.getFileName() + ".msh-tmp"); final Path pf = parent;
                H.backups.job(i, "compress", base.relativize(dest).toString().replace('\\', '/'), pr -> {
                    List<Archive.Item> items = Archive.collect(pf, src, q -> q.equals(part) || q.equals(dest));
                    try (OutputStream o = new Archive.Counting(new java.io.BufferedOutputStream(Files.newOutputStream(part), 1 << 16), allow)) { Archive.write(k2, items, o, pr, 6); }
                    catch (java.io.IOException e) { Files.deleteIfExists(part); if (e.getMessage() != null && e.getMessage().startsWith("quota")) throw new Api.Err("disk_full"); throw e; }
                    Files.move(part, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING); i.diskBytes = -1;
                });
                return Map.of("ok", true, "job", H.backups.jobView(i));
            }
            case "extract": {
                JsonObject b = jbody(x); Path base = d.toPath().toAbsolutePath().normalize();
                Path arc = Files2.safe(d, Json.s(b, "path", "")); if (!Files.isRegularFile(arc, java.nio.file.LinkOption.NOFOLLOW_LINKS)) throw new Api.Err("not_found", 404);
                String kind = Archive.kind(arc.getFileName().toString()); if (kind == null) throw new Api.Err("not_archive");
                String dr = Json.s(b, "dest", null); Path dest = dr == null ? arc.getParent() : Files2.safe(d, dr);
                if (Files.exists(dest, java.nio.file.LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(dest, java.nio.file.LinkOption.NOFOLLOW_LINKS)) throw new Api.Err("bad_path");
                long free = i.disk_gb * 1073741824L - Sys.dirSize(base); if (free <= 0) throw new Api.Err("disk_full");
                Archive.Limits lim = new Archive.Limits(Math.min(free, H.conf.num("archive.max_extract_mb", 4096) * 1048576L), H.conf.num("archive.max_entries", 200000));
                final boolean relax = Files2.RELAX.get();
                H.backups.job(i, "extract", base.relativize(arc).toString().replace('\\', '/'), pr -> {
                    Files.createDirectories(dest);
                    try { Archive.extract(arc, kind, base, dest, lim, q -> !relax && Files2.protectedName(base, q), pr); }
                    catch (Api.Err e) { if ("archive_too_big".equals(e.getMessage()) && lim.maxBytes == free) throw new Api.Err("disk_full"); throw e; }
                    i.diskBytes = -1;
                });
                return Map.of("ok", true, "job", H.backups.jobView(i));
            }
            case "download-zip": {
                Path base = d.toPath().toAbsolutePath().normalize(); List<Path> src = new ArrayList<>(); Path parent = null;
                for (String r : query(x).getOrDefault("paths", "").split("\n")) { if (r.isBlank()) continue; Path q = Files2.safe(d, r); if (parent == null) parent = q.equals(base) ? base : q.getParent(); src.add(q); }
                if (src.isEmpty()) throw new Api.Err("nothing_selected");
                Path relTo = src.size() == 1 && src.get(0).equals(base) ? base : parent;
                for (Path q : src) if (!q.equals(base) && !q.getParent().equals(relTo)) throw new Api.Err("bad_path");
                List<Archive.Item> items = Archive.collect(relTo, src, null);
                String nm = (src.size() == 1 ? (src.get(0).equals(base) ? i.name : src.get(0).getFileName().toString()) : "arquivos").replaceAll("[^A-Za-z0-9._ -]", "_") + ".zip";
                x.getResponseHeaders().set("Content-Type", "application/zip"); x.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + nm + "\"");
                x.sendResponseHeaders(200, 0);
                try (OutputStream o = new java.io.BufferedOutputStream(x.getResponseBody(), 1 << 16)) { Archive.write("zip", items, o, null, 5); }
                return new byte[0];
            }
            case "": { Map<String, Object> v = H.inst.view(i); if (i.central && AS.get() == null) { H.inst.anonymize(v); v.put("perms", List.of()); } else v.put("perms", H.accounts.serverPerms(u, i)); return Map.of("ok", true, "server", v); }
            case "console": return Map.of("ok", true, "console", H.inst.console(i, Long.parseLong(query(x).getOrDefault("since", "0"))), "server", H.inst.view(i));
            case "start": H.inst.start(i); return Map.of("ok", true, "server", H.inst.view(i));
            case "stop": H.inst.stop(i); return Map.of("ok", true, "server", H.inst.view(i));
            case "kill": H.inst.kill(i); return Map.of("ok", true, "server", H.inst.view(i));
            case "restart": { H.inst.stop(i); H.pool.execute(() -> { for (int k = 0; k < 120 && i.proc != null && i.proc.isAlive(); k++) try { Thread.sleep(500); } catch (InterruptedException ignored) { } try { H.inst.start(i); } catch (Exception e) { H.inst.line(i, "[MinesyncHost] restart: " + e.getMessage()); } }); return Map.of("ok", true, "server", H.inst.view(i)); }
            case "cmd": { String c = Json.s(jbody(x), "cmd", ""); H.inst.command(i, c); H.activity.log(i, u, "console", c); return Map.of("ok", true); }
            case "reinstall": { if (!Instances.mc(i.type)) throw new Api.Err("bad_type"); JsonObject b = jbody(x); String v = Json.s(b, "version", i.version); if (!v.matches("[0-9A-Za-z._-]{1,32}")) throw new Api.Err("bad_version"); i.version = v; H.inst.save(); H.inst.install(i); return Map.of("ok", true, "server", H.inst.view(i)); }
            case "settings": {
                JsonObject b = jbody(x);
                if (b.has("name")) { String n = Json.s(b, "name", "").replaceAll("[<>\"'\\\\]", "").trim(); if (n.isEmpty() || n.length() > 40) throw new Api.Err("bad_name"); i.name = n; }
                if (b.has("autostart")) i.autostart = Json.b(b, "autostart", false);
                if (b.has("owner") && u.admin) { Users.User no = H.users.get(Json.s(b, "owner", "")); if (no == null) throw new Api.Err("no_such_user"); i.owner = no.name; if (i.subusers != null) i.subusers.remove(Accounts.key(no.name)); }
                if (b.has("bridge")) i.bridge = Json.b(b, "bridge", true);
                if (b.has("address") && "external".equals(i.type)) { String a = Json.s(b, "address", "").trim().toLowerCase(); if (!a.matches("[a-z0-9.-]{1,253}:\\d{1,5}") || !Instances.localAddr(a)) throw new Api.Err("bad_address"); i.address = a; }
                if (u.admin && !"external".equals(i.type) && (b.has("ram_mb") || b.has("cores") || b.has("disk_gb"))) {
                    long ram = Json.n(b, "ram_mb", i.ram_mb), cores = Json.n(b, "cores", i.cores), disk = Json.n(b, "disk_gb", i.disk_gb);
                    if (ram < 800 || ram > 65536 || cores < 1 || cores > 32 || disk < 4 || disk > 128) throw new Api.Err("bad_resources");
                    if (ram > H.conf.num("limits.max_ram_mb", 2048) - H.inst.usedRam(i.id) || cores > H.conf.num("limits.max_cores", 2) - H.inst.usedCores(i.id) || disk > H.conf.num("limits.max_disk_gb", 32) - H.inst.usedDisk(i.id)) throw new Api.Err("not_enough_resources");
                    i.ram_mb = ram; i.cores = cores; i.disk_gb = disk;
                }
                H.inst.save(); return Map.of("ok", true, "server", H.inst.view(i));
            }
            case "delete": { H.inst.delete(i); return Map.of("ok", true); }
            case "files": return Map.of("ok", true, "files", Files2.list(d, query(x).getOrDefault("path", "")));
            case "file": if ("GET".equals(m)) return Map.of("ok", true, "text", Files2.read(d, query(x).get("path")));
                { JsonObject b = jbody(x); Files2.write(d, Json.s(b, "path", ""), Json.s(b, "text", "").getBytes(StandardCharsets.UTF_8)); return Map.of("ok", true); }
            case "upload": { if (!"PUT".equals(m) && !"POST".equals(m)) throw new Api.Err("bad_method"); byte[] data = body(x, 256L << 20); checkDisk(i, data.length); Files2.write(d, query(x).get("path"), data); return Map.of("ok", true); }
            case "download": {
                Path f = Files2.safe(d, query(x).get("path")); if (!Files.isRegularFile(f, java.nio.file.LinkOption.NOFOLLOW_LINKS)) throw new Api.Err("not_found", 404);
                x.getResponseHeaders().set("Content-Type", "application/octet-stream"); x.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + f.getFileName().toString().replaceAll("[\"\\\\\\r\\n]", "_") + "\"");
                x.sendResponseHeaders(200, Files.size(f)); try (OutputStream o = x.getResponseBody()) { Files.copy(f, o); }
                return new byte[0];
            }
            case "mkdir": Files2.mkdir(d, Json.s(jbody(x), "path", "")); return Map.of("ok", true);
            case "rename": { JsonObject b = jbody(x); Files2.rename(d, Json.s(b, "from", ""), Json.s(b, "to", "")); return Map.of("ok", true); }
            case "rm": Files2.delete(d, Json.s(jbody(x), "path", "")); return Map.of("ok", true);
            default: throw new Api.Err("not_found", 404);
        }
    }
    private static List<String> paths(JsonObject b) {
        List<String> l = new ArrayList<>();
        if (b.has("paths") && b.get("paths").isJsonArray()) for (JsonElement e : b.getAsJsonArray("paths")) { String s = e.getAsString(); if (!s.isBlank()) l.add(s); }
        if (l.size() > 5000) throw new Api.Err("too_many_files");
        return l;
    }
    /** 1.3: permissão necessária para cada ação do servidor */
    private static String perm(String act, String m) {
        if (act.startsWith("backups")) return "backups";
        if (act.startsWith("schedules")) return "schedules";
        if (act.startsWith("subusers")) return "owner.subusers";
        switch (act) {
            case "": case "jobs": case "activity": return null;
            case "console": case "cmd": return "console";
            case "start": case "stop": case "kill": case "restart": return "power";
            case "reinstall": case "settings": return "settings";
            case "startup": return "startup";
            case "delete": return "owner.delete";
            case "files": case "download": case "download-zip": return "files";
            case "file": return "GET".equals(m) ? "files" : "files.write";
            default: return "files.write";   // upload, mkdir, rename, rm, rm-many, compress, extract
        }
    }
    /** 1.3: INICIALIZAÇÃO do servidor (arquivo principal, Java, flags, argumentos, variáveis, parar, pronto, reiniciar se cair) */
    private Object startup(HttpExchange x, Users.User u, Instances.Inst i) throws Exception {
        boolean cust = Instances.custom(i.type) || H.accounts.customAllowed(i) || (i.central && AS.get() == CENTRAL);   // 2.1: servidor da rede — quem decide o que o cliente pode mudar é a central
        if ("POST".equals(x.getRequestMethod())) {
            JsonObject b = jbody(x);
            if (b.has("args")) { String a = Json.s(b, "args", ""); if (a.length() > 1000 || a.contains("\n") || a.contains("\0")) throw new Api.Err("bad_args"); i.args = a; }
            if (b.has("stop_cmd")) { String a = Json.s(b, "stop_cmd", "").trim(); if (a.length() > 100 || a.contains("\n")) throw new Api.Err("bad_args"); i.stop_cmd = a; }
            if (b.has("ready")) { String a = Json.s(b, "ready", ""); if (a.length() > 100 || a.contains("\n")) throw new Api.Err("bad_args"); i.ready = a; }
            if (b.has("restart_on_crash")) i.restart_on_crash = Json.b(b, "restart_on_crash", false);
            if (b.has("deps")) i.deps = Json.b(b, "deps", true);
            // 2.0: versão do runtime (baixada na pasta da host na primeira vez que ligar)
            if (b.has("java_ver")) { String v = Runtimes.norm("java", Json.s(b, "java_ver", "")); if (v != null && !H.runtimes.valid("java", v)) throw new Api.Err("bad_runtime"); i.java_ver = v; }
            if (b.has("node_ver")) { String v = Runtimes.norm("node", Json.s(b, "node_ver", "")); if (v != null && !H.runtimes.valid("node", v)) throw new Api.Err("bad_runtime"); i.node_ver = v; }
            if (b.has("py_ver")) { String v = Runtimes.norm("python", Json.s(b, "py_ver", "")); if (v != null && !H.runtimes.valid("python", v)) throw new Api.Err("bad_runtime"); i.py_ver = v; }
            boolean wantsCustom = b.has("main") && !Instances.checkMain(Json.s(b, "main", "")).equals(Instances.mainOf(i)) || b.has("jvm_flags") && !Json.s(b, "jvm_flags", "").equals(i.jvm_flags == null ? "" : i.jvm_flags) || b.has("env") || b.has("java_rt") || b.has("aikar");
            if (wantsCustom && !cust) throw new Api.Err("no_permission_custom", 403);
            if (b.has("main")) {   // 2.1: o arquivo principal precisa existir na pasta do servidor (e ser .jar nos servidores Java/Minecraft)
                String mc = Instances.checkMain(Json.s(b, "main", ""));
                if ((Instances.mc(i.type) || "java".equals(i.type)) && !"forge".equals(i.type) && !mc.toLowerCase(java.util.Locale.ROOT).endsWith(".jar")) throw new Api.Err("bad_main");
                File mf = Files2.safe(H.inst.dir(i), mc).toFile(); if (!mf.isFile()) throw new Api.Err("main_not_found");
            }
            if (b.has("main")) { String mn = Instances.checkMain(Json.s(b, "main", "")); i.main = mn.equals(Instances.defMain(i.type)) && !"forge".equals(i.type) ? null : mn; }
            if (b.has("jvm_flags")) { String f = Json.s(b, "jvm_flags", "").trim(); if (f.length() > 2000 || f.contains("\n") || f.contains("\0")) throw new Api.Err("bad_flags"); for (String t : Instances.split(f)) if (!t.startsWith("-")) throw new Api.Err("bad_flags"); i.jvm_flags = f; }
            if (b.has("java_rt")) { String r = Json.s(b, "java_rt", "").trim(); if (!r.isEmpty() && !H.inst.javaRuntimes().containsKey(r)) throw new Api.Err("bad_runtime"); i.java_rt = r.isEmpty() ? null : r; }
            if (b.has("aikar")) i.aikar = Json.b(b, "aikar", true);
            if (b.has("env")) {
                Map<String, String> env = new LinkedHashMap<>();
                if (b.get("env").isJsonObject()) for (Map.Entry<String, JsonElement> e : b.getAsJsonObject("env").entrySet()) {
                    String k = e.getKey().trim(), v = e.getValue().isJsonNull() ? "" : e.getValue().getAsString();
                    if (k.isEmpty()) continue; if (!Instances.okEnv(k)) throw new Api.Err("bad_env: " + k); if (v.length() > 1000 || v.contains("\n") || v.contains("\0")) throw new Api.Err("bad_env: " + k);
                    env.put(k, v); if (env.size() > 50) throw new Api.Err("bad_env");
                }
                i.env = env.isEmpty() ? null : env;
            }
            H.inst.save(); H.activity.log(i, u, "startup", Instances.mainOf(i));
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ok", true); r.put("type", i.type); r.put("main", Instances.mainOf(i)); r.put("default_main", Instances.defMain(i.type)); r.put("jvm_flags", i.jvm_flags == null ? "" : i.jvm_flags); r.put("args", i.args == null ? (Instances.mc(i.type) ? "nogui" : "") : i.args);
        r.put("env", i.env == null ? Map.of() : i.env); r.put("java_rt", i.java_rt == null ? "" : i.java_rt); r.put("stop_cmd", i.stop_cmd == null ? (Instances.mc(i.type) ? "stop" : "") : i.stop_cmd); r.put("ready", i.ready == null ? "" : i.ready);
        r.put("java_ver", i.java_ver == null ? "" : i.java_ver); r.put("node_ver", i.node_ver == null ? "" : i.node_ver); r.put("py_ver", i.py_ver == null ? "" : i.py_ver);
        r.put("restart_on_crash", i.restart_on_crash); r.put("deps", i.deps); r.put("aikar", i.aikar == null ? !Instances.custom(i.type) : i.aikar); r.put("custom_allowed", cust); r.put("runtimes", H.inst.runtimes());
        r.put("vars", Map.of("SERVER_PORT", i.port, "SERVER_IP", H.inst.ip(), "SERVER_MEMORY", i.ram_mb));
        String pv; try { pv = String.join(" ", H.inst.raw(i, H.inst.dir(i))); } catch (Api.Err e) { pv = "⚠ " + e.getMessage(); } r.put("preview", pv);
        return r;
    }
    /** 1.3: AGENDAMENTOS */
    private Object schedules(HttpExchange x, Users.User u, Instances.Inst i, String act) throws Exception {
        if (i.schedules == null) i.schedules = new ArrayList<>();
        switch (act) {
            case "schedules": break;
            case "schedules/save": {
                JsonObject b = jbody(x); String id = Json.s(b, "id", null); Schedules.Sched cur = null;
                if (id != null) for (Schedules.Sched s : i.schedules) if (s.id.equals(id)) cur = s;
                if (id != null && cur == null) throw new Api.Err("not_found", 404);
                if (cur == null && i.schedules.size() >= 30) throw new Api.Err("too_many_schedules");
                Schedules.Sched s = H.schedules.parse(b, cur); if (cur == null) i.schedules.add(s);
                H.inst.save(); H.activity.log(i, u, "schedule.save", s.name + " · " + s.action); break;
            }
            case "schedules/delete": { String id = Json.s(jbody(x), "id", ""); i.schedules.removeIf(s -> s.id.equals(id)); H.inst.save(); break; }
            case "schedules/run": { String id = Json.s(jbody(x), "id", ""); for (Schedules.Sched s : i.schedules) if (s.id.equals(id)) H.schedules.run(i, s); break; }
            default: throw new Api.Err("not_found", 404);
        }
        return Map.of("ok", true, "schedules", i.schedules);
    }
    /** 1.3: SUBUSUÁRIOS (outras contas da host com acesso a este servidor) */
    private Object subusers(HttpExchange x, Users.User u, Instances.Inst i, String act) throws Exception {
        if (i.subusers == null) i.subusers = new LinkedHashMap<>();
        switch (act) {
            case "subusers": break;
            case "subusers/set": {
                JsonObject b = jbody(x); Users.User t = H.users.get(Json.s(b, "user", "").trim());
                if (t == null) throw new Api.Err("no_such_user"); if (t.name.equalsIgnoreCase(i.owner)) throw new Api.Err("is_owner");
                List<String> ps = new ArrayList<>(); if (b.has("perms") && b.get("perms").isJsonArray()) for (JsonElement e : b.getAsJsonArray("perms")) { String pp = e.getAsString(); if (Accounts.SERVER_PERMS.contains(pp) && !ps.contains(pp)) ps.add(pp); }
                if (!i.subusers.containsKey(Accounts.key(t.name)) && i.subusers.size() >= 20) throw new Api.Err("too_many_subusers");
                i.subusers.put(Accounts.key(t.name), ps); H.inst.save(); H.activity.log(i, u, "subuser.set", t.name + " · " + String.join(",", ps)); break;
            }
            case "subusers/delete": { String n = Accounts.key(Json.s(jbody(x), "user", "")); i.subusers.remove(n); H.inst.save(); H.activity.log(i, u, "subuser.delete", n); break; }
            default: throw new Api.Err("not_found", 404);
        }
        List<Object> l = new ArrayList<>(); for (Map.Entry<String, List<String>> e : i.subusers.entrySet()) { Users.User t = H.users.get(e.getKey()); l.add(Map.of("user", t == null ? e.getKey() : t.name, "perms", e.getValue())); }
        return Map.of("ok", true, "subusers", l, "perms", Accounts.SERVER_PERMS);
    }
    /** 1.2: backups do servidor */
    private Object backups(HttpExchange x, Users.User u, Instances.Inst i, String act) throws Exception {
        String m = x.getRequestMethod(); Backups B = H.backups;
        switch (act) {
            case "backups": {
                List<Object> l = new ArrayList<>(); for (Backups.Bk b : B.of(i.id)) l.add(B.view(b));
                List<Object> mine = new ArrayList<>(); for (Backups.Bk b : H.accounts.owner(u, i) ? B.ofOwner(i.owner) : B.of(i.id)) mine.add(B.view(b));   // para escolher qual sobrescrever (de qualquer servidor do dono)
                Map<String, Object> r = new LinkedHashMap<>(); r.put("ok", true); r.put("backups", l); r.put("owner_backups", mine); r.put("quota", B.quota(i.owner)); r.put("enabled", B.enabled());
                r.put("auto", Map.of("every_h", i.backup_every_h, "keep", i.backup_keep <= 0 ? 3 : i.backup_keep, "last", i.backup_last, "min_every_h", H.conf.num("backups.min_every_h", 1)));
                r.put("job", B.jobView(i)); return r;
            }
            case "backups/create": { if (!"POST".equals(m)) throw new Api.Err("bad_method"); JsonObject b = jbody(x); B.create(i, Json.s(b, "label", ""), Json.s(b, "replace", null), false, u); return Map.of("ok", true, "job", B.jobView(i)); }
            case "backups/restore": { Backups.Bk b = mineBk(B, Json.s(jbody(x), "id", null), u, i); B.restore(i, b); return Map.of("ok", true, "job", B.jobView(i)); }
            case "backups/delete": { Backups.Bk b = mineBk(B, Json.s(jbody(x), "id", null), u, i); if (b.locked) throw new Api.Err("backup_locked"); B.delete(b); return Map.of("ok", true); }
            case "backups/lock": { JsonObject j = jbody(x); Backups.Bk b = mineBk(B, Json.s(j, "id", null), u, i); B.lock(b, Json.b(j, "locked", true)); return Map.of("ok", true); }
            case "backups/rename": { JsonObject j = jbody(x); Backups.Bk b = mineBk(B, Json.s(j, "id", null), u, i); B.rename(b, Json.s(j, "label", "")); return Map.of("ok", true); }
            case "backups/auto": {
                JsonObject j = jbody(x); long ev = Json.n(j, "every_h", 0), keep = Json.n(j, "keep", 3);
                if (ev != 0 && (ev < H.conf.num("backups.min_every_h", 1) || ev > 720)) throw new Api.Err("bad_interval");
                if (keep < 1 || keep > Math.max(1, B.maxCount(i.owner))) throw new Api.Err("bad_keep");
                if (ev > 0 && i.backup_every_h <= 0) i.backup_last = System.currentTimeMillis() - ev * 3600_000L + 5 * 60_000L;   // o primeiro sai em ~5 min
                i.backup_every_h = ev; i.backup_keep = keep; H.inst.save(); return Map.of("ok", true, "server", H.inst.view(i));
            }
            case "backups/download": {
                Backups.Bk b = mineBk(B, query(x).get("id"), u, i); File f = B.file(b); if (!f.isFile()) throw new Api.Err("not_found", 404);
                String nm = (i.name + "-" + new java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm").format(new java.util.Date(b.created))).replaceAll("[^A-Za-z0-9._ -]", "_") + ".zip";
                x.getResponseHeaders().set("Content-Type", "application/zip"); x.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + nm + "\"");
                x.sendResponseHeaders(200, f.length()); try (OutputStream o = x.getResponseBody()) { Files.copy(f.toPath(), o); }
                return new byte[0];
            }
            default: throw new Api.Err("not_found", 404);
        }
    }
    /** backup do dono deste servidor; subusuário só mexe nos backups DESTE servidor */
    private Backups.Bk mineBk(Backups B, String id, Users.User u, Instances.Inst i) { Backups.Bk b = B.get(id, i.owner, false); if (!H.accounts.owner(u, i) && !b.server.equals(i.id)) throw new Api.Err("not_found", 404); return b; }
    private void checkDisk(Instances.Inst i, long add) { long used = i.diskBytes >= 0 ? i.diskBytes : Sys.dirSize(H.inst.dir(i).toPath()); if (used + add > i.disk_gb * 1073741824L) throw new Api.Err("disk_full"); }
}
