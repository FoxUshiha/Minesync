package net.foxsrv.host;

import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 1.3 — CONTA DA REDE (central) sem copiar código:
 * <ul>
 *   <li><b>Pelo site</b>: a host pede um pedido de entrada à central ({@code launcher/link/start}), o painel abre o site,
 *       a pessoa só clica em "Confirmar" (já logada no site) e a host recebe a confirmação ({@code link/poll}).</li>
 *   <li><b>Por usuário e senha da central</b> ({@code launcher/login}): a senha passa direto para a central e NÃO é guardada.</li>
 * </ul>
 * Com a confirmação a host: vincula a si mesma à conta (código de vínculo pedido com a sessão e usado na hora) ou
 * entra/vincula uma conta da host. A sessão da central é encerrada logo depois (a host não guarda token nenhum).
 */
final class Central {
    private final MinesyncHost H;
    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
    Central(MinesyncHost h) { this.H = h; }

    /** resultado de uma entrada: o perfil (usuário da central, nome) e o token (de uso imediato) */
    static final class Who { String token, username, name; }
    /** pedido de entrada pelo site (o painel guarda só o id; código/segredo ficam aqui) */
    static final class Pending { String code, secret, purpose, by, url; long at; volatile String state = "pending"; volatile Who who; volatile String error; }
    final Map<String, Pending> pend = new ConcurrentHashMap<>();

    private JsonObject post(String path, Object body, String token) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(H.conf.central() + "/botcraft/api/" + path)).timeout(Duration.ofSeconds(20))
            .header("Content-Type", "application/json").header("User-Agent", "MinesyncHost/" + MinesyncHost.VERSION).header("X-Botcraft", "1")
            .POST(HttpRequest.BodyPublishers.ofString(Json.str(body)));
        if (token != null) b.header("x-fl-token", token);
        HttpResponse<String> r;
        try { r = http.send(b.build(), HttpResponse.BodyHandlers.ofString()); } catch (Exception e) { throw new Api.Err("central_offline", 502); }
        JsonObject j = Json.obj(r.body());
        if (r.statusCode() >= 500 && !j.has("error")) throw new Api.Err("central_offline", 502);
        return j;
    }
    private static Who who(JsonObject j) {
        Who w = new Who(); w.token = Json.s(j, "token", null);
        JsonObject p = j.has("profile") && j.get("profile").isJsonObject() ? j.getAsJsonObject("profile") : new JsonObject();
        w.username = Json.s(p, "username", null); w.name = Json.s(p, "name", w.username);
        return w;
    }
    String device() { return "MinesyncHost · " + H.conf.str("network.name", "MinesyncHost"); }

    /** usuário e senha da central (a senha não fica guardada em lugar nenhum) */
    Who login(String user, String pass) throws Exception {
        if (user == null || user.isBlank() || pass == null || pass.isEmpty() || user.length() > 120 || pass.length() > 300) throw new Api.Err("missing_credentials");
        JsonObject j = post("launcher/login", Map.of("id", user.trim(), "password", pass, "device", device()), null);
        if (!Json.b(j, "ok", false) || !j.has("token")) throw new Api.Err(Json.s(j, "error", "invalid_credentials"), 401);
        Who w = who(j); if (w.username == null) { logout(w.token); throw new Api.Err("central_no_username"); }
        return w;
    }
    /** começa a entrada pelo site: devolve o id do pedido e o endereço para abrir */
    String start(String purpose, String by) throws Exception {
        pend.values().removeIf(p -> System.currentTimeMillis() - p.at > 15 * 60_000L);
        if (pend.size() > 200) throw new Api.Err("busy", 503);
        JsonObject j = post("launcher/link/start", Map.of("device", device()), null);
        if (!Json.b(j, "ok", false)) throw new Api.Err(Json.s(j, "error", "central_offline"), 502);
        Pending p = new Pending(); p.code = Json.s(j, "code", ""); p.secret = Json.s(j, "secret", ""); p.url = Json.s(j, "url", ""); p.purpose = purpose; p.by = by; p.at = System.currentTimeMillis();
        if (!p.url.startsWith("https://") && !p.url.startsWith("http://")) throw new Api.Err("central_offline", 502);
        String id = Users.rnd(24); pend.put(id, p); p.state = "pending";
        H.pool.execute(() -> watch(id, p));
        return id;
    }
    /** o pedido (só para quem o abriu: o mesmo propósito e o mesmo usuário do painel) */
    Pending get(String id, String purpose, String by) {
        Pending p = id == null ? null : pend.get(id);
        if (p == null || !p.purpose.equals(purpose) || !String.valueOf(p.by).equalsIgnoreCase(String.valueOf(by))) throw new Api.Err("link_expired", 404);
        return p;
    }
    /** a host pergunta à central até a pessoa confirmar no site (ou recusar / vencer) */
    private void watch(String id, Pending p) {
        long end = p.at + 15 * 60_000L;
        while (System.currentTimeMillis() < end && pend.get(id) == p) {
            try {
                JsonObject j = post("launcher/link/poll", Map.of("code", p.code, "secret", p.secret), null);
                String st = Json.s(j, "status", Json.b(j, "ok", false) ? "pending" : "error");
                if ("ok".equals(st)) { Who w = who(j); if (w.username == null) { logout(w.token); p.error = "central_no_username"; p.state = "error"; return; } p.who = w; p.state = "ok"; return; }
                if ("denied".equals(st)) { p.state = "denied"; return; }
                if (!Json.b(j, "ok", false)) { String e = Json.s(j, "error", ""); if ("link_expired".equals(e) || "forbidden".equals(e)) { p.state = "expired"; return; } }
            } catch (Exception ignored) { }
            try { Thread.sleep(2000); } catch (InterruptedException e) { return; }
        }
        if ("pending".equals(p.state)) p.state = "expired";
    }
    /** vincula ESTA host à conta (pede o código de vínculo com a sessão e usa na hora) */
    void linkHost(Who w) throws Exception {
        try {
            JsonObject j = post("host/linkcode", Map.of(), w.token);
            if (!Json.b(j, "ok", false)) throw new Api.Err(Json.s(j, "error", "link_failed"));
            H.net.link(Json.s(j, "code", ""));
        } finally { logout(w.token); }
    }
    void logout(String token) { if (token == null) return; try { post("launcher/logout", Map.of(), token); } catch (Exception ignored) { } }
}
