package net.foxsrv.host;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CLOUDFLARE: baixa o cloudflared certo para o sistema (Windows, Linux, macOS · x64/ARM) na pasta cloudflared/ e abre o
 * painel pela internet — "quick" (endereço *.trycloudflare.com, muda a cada vez) ou "token" (túnel permanente da sua conta
 * Cloudflare com o seu domínio). Assim que o túnel sobe, o painel mostra o link.
 * <p>1.3 — "Entrar com a Cloudflare" (sem copiar token): a host roda o {@code cloudflared tunnel login}, o painel abre a
 * página da Cloudflare, a pessoa escolhe o domínio e autoriza; a host recebe o certificado sozinha, cria o túnel
 * ({@code tunnel create}), aponta o endereço escolhido ({@code tunnel route dns}), escreve o tunnel.yml (só o painel) e liga.
 * Certificado e credenciais ficam em cloudflared/ (NÃO compartilhe essa pasta).</p>
 */
final class Cloud {
    private final MinesyncHost H; private final File dir;
    private volatile Process proc; volatile String url = null, state = "off", error = "";
    private static final Pattern QUICK = Pattern.compile("https://[a-z0-9-]+\\.trycloudflare\\.com");
    Cloud(MinesyncHost h, File dir) { this.H = h; this.dir = dir; }

    File exe() { return new File(dir, Sys.win() ? "cloudflared.exe" : "cloudflared"); }
    String downloadUrl() {
        String b = "https://github.com/cloudflare/cloudflared/releases/latest/download/";
        String a = Sys.arch();
        if (Sys.win()) return b + (a.equals("386") ? "cloudflared-windows-386.exe" : "cloudflared-windows-amd64.exe");
        if (Sys.mac()) return b + (a.equals("arm64") ? "cloudflared-darwin-arm64.tgz" : "cloudflared-darwin-amd64.tgz");
        return b + "cloudflared-linux-" + (a.equals("arm64") ? "arm64" : a.equals("arm") ? "arm" : a.equals("386") ? "386" : "amd64");
    }
    synchronized void ensure() throws Exception {
        if (exe().isFile() && exe().length() > 1_000_000) return;
        dir.mkdirs(); state = "downloading";
        String u = downloadUrl();
        if (u.endsWith(".tgz")) {
            File tgz = new File(dir, "cloudflared.tgz"); H.inst.get(u, tgz, null, null);
            Process p = new ProcessBuilder("tar", "-xzf", tgz.getName()).directory(dir).redirectErrorStream(true).start(); p.waitFor(); tgz.delete();
        } else H.inst.get(u, exe(), null, null);
        exe().setExecutable(true, true);
    }
    // ------------------------------------------------------------------ 1.3 entrar com a Cloudflare
    volatile String loginState = "idle", loginUrl = null, loginError = "", zone = null, setupState = "idle", setupError = "";
    private volatile Process loginProc;
    private static final Pattern LOGIN_URL = Pattern.compile("https://dash\\.cloudflare\\.com/argotunnel\\S+");
    private static final Pattern UUID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    File home() { return new File(dir, "home"); }
    File cert() { return new File(dir, "cert.pem"); }
    File creds() { return new File(dir, "tunnel-credentials.json"); }
    File yml() { return new File(dir, "tunnel.yml"); }
    boolean loggedIn() { return cert().isFile(); }
    private ProcessBuilder pb(List<String> cmd) {
        ProcessBuilder b = new ProcessBuilder(cmd).directory(dir).redirectErrorStream(true);
        home().mkdirs(); String hm = home().getAbsolutePath();
        b.environment().put("HOME", hm); b.environment().put("USERPROFILE", hm); b.environment().put("TUNNEL_ORIGIN_CERT", cert().getAbsolutePath());
        return b;
    }
    /** roda o cloudflared e devolve a saída (para create/route) */
    private String run(List<String> args, int seconds) throws Exception {
        List<String> cmd = new ArrayList<>(); cmd.add(exe().getAbsolutePath()); cmd.addAll(args);
        Process p = pb(cmd).start(); StringBuilder out = new StringBuilder();
        Thread t = new Thread(() -> { try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) { String l; while ((l = r.readLine()) != null) synchronized (out) { out.append(l).append('\n'); } } catch (Exception ignored) { } });
        t.setDaemon(true); t.start();
        if (!p.waitFor(seconds, java.util.concurrent.TimeUnit.SECONDS)) { p.destroyForcibly(); throw new Exception("cloudflared: tempo esgotado"); }
        t.join(2000); synchronized (out) { String o = out.toString(); if (p.exitValue() != 0) throw new CfErr(o); return o; }
    }
    static final class CfErr extends Exception { final String out; CfErr(String o) { super(lastLine(o)); out = o; } }
    private static String lastLine(String o) { String[] l = o.trim().split("\n"); String x = l.length == 0 ? "" : l[l.length - 1]; return x.length() > 300 ? x.substring(0, 300) : x; }
    /** 1º passo: abre o login da Cloudflare (a página para autorizar volta em loginUrl) */
    synchronized void loginStart() {
        if ("waiting".equals(loginState) && loginProc != null && loginProc.isAlive()) return;
        loginState = "starting"; loginUrl = null; loginError = "";
        H.pool.execute(() -> {
            try {
                ensure();
                File hc = new File(home(), ".cloudflared"); new File(hc, "cert.pem").delete();
                ProcessBuilder lb = pb(List.of(exe().getAbsolutePath(), "tunnel", "login")); lb.environment().remove("TUNNEL_ORIGIN_CERT");   // o login grava em ~/.cloudflared (aqui: cloudflared/home)
                Process p = lb.start(); loginProc = p; final String[] saved = { null };
                H.timer.schedule(() -> { if (p.isAlive()) { p.destroyForcibly(); if (!"done".equals(loginState)) { loginState = "error"; loginError = "timeout"; } } }, 15, java.util.concurrent.TimeUnit.MINUTES);
                try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                    String l; while ((l = r.readLine()) != null) { Matcher m = LOGIN_URL.matcher(l); if (loginUrl == null && m.find()) { loginUrl = m.group(); loginState = "waiting"; } Matcher sv = java.util.regex.Pattern.compile("saved to:\\s*(\\S*cert\\.pem)").matcher(l); if (sv.find()) saved[0] = sv.group(1); if (l.contains("ERR") || l.toLowerCase().contains("error")) loginError = l.length() > 300 ? l.substring(0, 300) : l; }
                }
                p.waitFor();
                File got = new File(hc, "cert.pem"); if (!got.isFile() && saved[0] != null && new File(saved[0]).isFile()) got = new File(saved[0]);
                if (got.isFile()) { java.nio.file.Files.move(got.toPath(), cert().toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING); Users.restrict(cert()); zone = zoneName(); if (zone != null) { H.conf.set("cloudflare.zone", zone); H.conf.save(); } loginState = "done"; loginError = ""; MinesyncHost.log("Cloudflare: login feito" + (zone != null ? " (" + zone + ")" : "")); }
                else if (!"error".equals(loginState)) { loginState = "error"; if (loginError.isEmpty()) loginError = "login_canceled"; }
            } catch (Exception e) { loginState = "error"; loginError = String.valueOf(e.getMessage()); }
            finally { loginProc = null; }
        });
    }
    /** domínio escolhido no login (o certificado traz a zona; o nome vem da API da Cloudflare com o token dele) */
    String zoneName() {
        try {
            String pem = java.nio.file.Files.readString(cert().toPath());
            int a = pem.indexOf("-----BEGIN ARGO TUNNEL TOKEN-----"), b = pem.indexOf("-----END ARGO TUNNEL TOKEN-----");
            if (a < 0 || b < 0) return null;
            String js = new String(java.util.Base64.getMimeDecoder().decode(pem.substring(a + 33, b).trim()), StandardCharsets.UTF_8);
            com.google.gson.JsonObject o = Json.obj(js); String zid = Json.s(o, "zoneID", null), tok = Json.s(o, "apiToken", null);
            if (zid == null || tok == null || !zid.matches("[0-9a-f]{32}")) return null;
            java.net.http.HttpResponse<String> r = java.net.http.HttpClient.newHttpClient().send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("https://api.cloudflare.com/client/v4/zones/" + zid)).timeout(java.time.Duration.ofSeconds(15)).header("Authorization", "Bearer " + tok).GET().build(), java.net.http.HttpResponse.BodyHandlers.ofString());
            com.google.gson.JsonObject j = Json.obj(r.body()); if (!j.has("result") || !j.get("result").isJsonObject()) return null;
            String n = Json.s(j.getAsJsonObject("result"), "name", null); return n != null && n.matches("[a-z0-9.-]{3,200}") ? n : null;
        } catch (Exception e) { return null; }
    }
    /** 2º passo: cria (ou reaproveita) o túnel da host, aponta o endereço e liga */
    synchronized void setup(String hostname) {
        if (!loggedIn()) throw new Api.Err("cf_not_logged_in");
        String hn = hostname == null ? "" : hostname.trim().toLowerCase(java.util.Locale.ROOT);
        if (!hn.matches("([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,}")) throw new Api.Err("bad_hostname");
        String z = H.conf.str("cloudflare.zone", "");
        if (!z.isEmpty() && !(hn.equals(z) || hn.endsWith("." + z))) throw new Api.Err("hostname_not_in_zone");
        if ("working".equals(setupState)) throw new Api.Err("busy");
        setupState = "working"; setupError = "";
        H.pool.execute(() -> {
            try {
                ensure();
                String name = "minesynchost-" + H.net.id.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "").substring(0, Math.min(12, H.net.id.length() - 1));
                String id = H.conf.str("cloudflare.tunnel_id", "");
                if (id.isEmpty() || !creds().isFile()) {
                    String out;
                    try { out = run(List.of("tunnel", "create", "--credentials-file", creds().getAbsolutePath(), name), 90); }
                    catch (CfErr e) {
                        if (!e.out.toLowerCase().contains("already exists")) throw e;
                        // o túnel com o nome da host já existe (sem as credenciais aqui): apaga o nosso e cria de novo
                        try { run(List.of("tunnel", "cleanup", name), 60); } catch (Exception ignored) { }
                        run(List.of("tunnel", "delete", "-f", name), 60);
                        out = run(List.of("tunnel", "create", "--credentials-file", creds().getAbsolutePath(), name), 90);
                    }
                    Matcher m = UUID.matcher(out); if (!m.find()) throw new Exception("cloudflared não informou o id do túnel");
                    id = m.group(); Users.restrict(creds());
                }
                run(List.of("tunnel", "route", "dns", "--overwrite-dns", id, hn), 90);
                String y = "# MinesyncHost: túnel do painel (gerado sozinho — não compartilhe esta pasta)\ntunnel: " + id + "\ncredentials-file: " + creds().getAbsolutePath().replace("\\", "/") + "\ningress:\n  - hostname: " + hn + "\n    service: http://127.0.0.1:" + H.conf.num("panel.port", 8765) + "\n  - service: http_status:404\n";
                java.nio.file.Files.writeString(yml().toPath(), y, StandardCharsets.UTF_8); Users.restrict(yml());
                H.conf.set("cloudflare.tunnel_id", id); H.conf.set("cloudflare.hostname", hn); H.conf.set("cloudflare.mode", "login"); H.conf.set("cloudflare.enabled", true); H.conf.save();
                setupState = "done"; start();
            } catch (Exception e) { setupState = "error"; setupError = String.valueOf(e.getMessage()); MinesyncHost.log("Cloudflare: " + e.getMessage()); }
        });
    }
    /** sair da Cloudflare: desliga, apaga o túnel da conta (se der) e os arquivos daqui */
    synchronized void logout() {
        stop(); String id = H.conf.str("cloudflare.tunnel_id", "");
        if (!id.isEmpty() && loggedIn() && exe().isFile()) { try { run(List.of("tunnel", "cleanup", id), 60); run(List.of("tunnel", "delete", "-f", id), 60); } catch (Exception ignored) { } }
        cert().delete(); creds().delete(); yml().delete(); Files2.deleteTree(home().toPath());
        H.conf.set("cloudflare.tunnel_id", ""); H.conf.set("cloudflare.zone", ""); if ("login".equals(H.conf.str("cloudflare.mode", ""))) { H.conf.set("cloudflare.mode", "quick"); H.conf.set("cloudflare.enabled", false); }
        H.conf.save(); loginState = "idle"; loginUrl = null; setupState = "idle"; zone = null;
    }
    java.util.Map<String, Object> view() {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("state", state); m.put("url", url == null ? "" : url); m.put("error", error); m.put("exe", exe().isFile());
        m.put("logged_in", loggedIn()); m.put("login_state", loginState); m.put("login_url", loginUrl == null ? "" : loginUrl); m.put("login_error", loginError);
        m.put("zone", H.conf.str("cloudflare.zone", "")); m.put("setup_state", setupState); m.put("setup_error", setupError); m.put("tunnel", !H.conf.str("cloudflare.tunnel_id", "").isEmpty());
        return m;
    }

    synchronized void start() {
        stop();
        if (!H.conf.bool("cloudflare.enabled", false)) { state = "off"; return; }
        H.pool.execute(() -> {
            try {
                ensure();
                String mode = H.conf.str("cloudflare.mode", "quick"); String tok = H.conf.str("cloudflare.token", "").trim();
                List<String> cmd = new ArrayList<>(List.of(exe().getAbsolutePath(), "tunnel", "--no-autoupdate"));
                String target = "http://127.0.0.1:" + H.conf.num("panel.port", 8765);
                if ("login".equals(mode) && yml().isFile() && creds().isFile()) { cmd.addAll(List.of("--config", yml().getAbsolutePath(), "run")); String hn = H.conf.str("cloudflare.hostname", "").trim(); url = hn.matches("[A-Za-z0-9.-]+") ? "https://" + hn : null; }
                else if ("token".equals(mode) && tok.matches("[A-Za-z0-9_=.+/-]{40,}")) { cmd.addAll(List.of("run", "--token", tok)); String hn = H.conf.str("cloudflare.hostname", "").trim(); url = hn.matches("[A-Za-z0-9.-]+") ? "https://" + hn : null; }
                else { cmd.addAll(List.of("--url", target)); url = null; }
                state = "starting"; error = "";
                Process p = pb(cmd).start(); proc = p;
                try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                    String l; while ((l = r.readLine()) != null) {
                        Matcher m = QUICK.matcher(l); if (url == null && m.find()) { url = m.group(); state = "on"; MinesyncHost.log("Cloudflare: painel em " + url); H.net.helloSoon(); }
                        if (l.contains("Registered tunnel connection") || l.contains("Connection registered")) { state = "on"; }
                        if (l.contains("ERR")) error = l.length() > 300 ? l.substring(0, 300) : l;
                    }
                }
                if (proc == p) { state = "off"; url = null; }
            } catch (Exception e) { state = "error"; error = String.valueOf(e.getMessage()); MinesyncHost.log("Cloudflare: " + e.getMessage()); }
        });
    }
    synchronized void stop() { Process p = proc; proc = null; if (p != null) { p.descendants().forEach(ProcessHandle::destroy); p.destroy(); } state = "off"; url = null; }
}
