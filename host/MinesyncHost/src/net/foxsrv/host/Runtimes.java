package net.foxsrv.host;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * 2.0 — RUNTIMES POR VERSÃO, baixados na hora e guardados DENTRO da pasta da host (./runtimes): nada é instalado no
 * sistema, nada fora da host é tocado. Binários oficiais para o sistema/arquitetura deste PC:
 * <ul>
 *   <li><b>Java</b> 8, 11, 17, 21, 25 — Eclipse Temurin (api.adoptium.net), conferido com o sha256 da Adoptium;</li>
 *   <li><b>Node.js</b> 18, 20, 22, 24 — nodejs.org/dist, conferido com o SHASUMS256.txt (npm incluído);</li>
 *   <li><b>Python</b> 3.10–3.13 — python-build-standalone (Astral, "install_only"), conferido com o .sha256 (pip incluído).</li>
 * </ul>
 * No Linux com bubblewrap a pasta do runtime entra só-leitura na caixa do servidor. As dependências do servidor
 * (npm install / pip install --target .pydeps) ficam na pasta dele.
 */
final class Runtimes {
    static final List<String> JAVA = List.of("8", "11", "17", "21", "25"), NODE = List.of("18", "20", "22", "24"), PY = List.of("3.10", "3.11", "3.12", "3.13");
    private final MinesyncHost H; final File dir;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).followRedirects(HttpClient.Redirect.NORMAL).build();
    private final Map<String, Object> locks = new ConcurrentHashMap<>();
    Runtimes(MinesyncHost h, File dir) { this.H = h; this.dir = dir; dir.mkdirs(); }

    static String os() { return Sys.win() ? "windows" : Sys.mac() ? "mac" : "linux"; }
    static String arch() { String a = Sys.arch(); return a.equals("arm64") ? "aarch64" : "x64"; }
    File home(String kind, String ver) { return new File(dir, kind + "-" + ver); }
    boolean valid(String kind, String ver) { return ver != null && ("java".equals(kind) ? JAVA : "node".equals(kind) ? NODE : PY).contains(ver); }

    /** o executável (java / node / python) se já estiver baixado; null se não */
    String exe(String kind, String ver) {
        if (!valid(kind, ver)) return null;
        File h = home(kind, ver); if (!new File(h, ".ok").isFile()) return null;
        try (Stream<Path> s = Files.walk(h.toPath(), 5)) {
            String want = "java".equals(kind) ? (Sys.win() ? "java.exe" : "java") : "node".equals(kind) ? (Sys.win() ? "node.exe" : "node") : null;
            return s.filter(Files::isRegularFile).filter(p -> {
                String n = p.getFileName().toString(); String par = p.getParent() == null ? "" : p.getParent().getFileName().toString();
                if (want != null) return n.equals(want) && (par.equals("bin") || Sys.win() && "node".equals(kind));
                return Sys.win() ? n.equals("python.exe") : par.equals("bin") && n.matches("python3(\\.\\d+)?");
            }).sorted((a, b) -> a.toString().length() - b.toString().length()).map(p -> p.toAbsolutePath().toString()).findFirst().orElse(null);
        } catch (Exception e) { return null; }
    }
    /** npm (a CLI JavaScript que vem com o Node — roda com o próprio node, sem depender de link simbólico) */
    String npmCli(String nodeExe) {
        if (nodeExe == null) return null;
        File bin = new File(nodeExe).getParentFile(); File root = Sys.win() ? bin : bin.getParentFile();
        for (File f : new File[]{ new File(root, "lib/node_modules/npm/bin/npm-cli.js"), new File(root, "node_modules/npm/bin/npm-cli.js") }) if (f.isFile()) return f.getAbsolutePath();
        return null;
    }
    boolean installed(String kind, String ver) { return exe(kind, ver) != null; }
    Map<String, Object> view() {
        Map<String, Object> m = new LinkedHashMap<>();
        for (String[] k : new String[][]{ { "java", "" }, { "node", "" }, { "python", "" } }) {
            List<Object> l = new ArrayList<>();
            for (String v : ("java".equals(k[0]) ? JAVA : "node".equals(k[0]) ? NODE : PY)) l.add(Map.of("version", v, "installed", installed(k[0], v)));
            m.put(k[0], l);
        }
        m.put("os", os() + "-" + arch()); return m;
    }

    /** garante o runtime (baixa, confere e extrai se faltar); devolve o executável */
    String ensure(String kind, String ver, java.util.function.Consumer<String> say) throws Exception {
        if (!valid(kind, ver)) throw new Api.Err("bad_runtime");
        String e = exe(kind, ver); if (e != null) return e;
        synchronized (locks.computeIfAbsent(kind + ver, k -> new Object())) {
            e = exe(kind, ver); if (e != null) return e;
            File h = home(kind, ver); Files2.deleteTree(h.toPath()); h.mkdirs();
            String[] src = "java".equals(kind) ? java(ver) : "node".equals(kind) ? node(ver) : python(ver);   // {url, sha256, nome}
            say.accept("[MinesyncHost] baixando " + kind + " " + ver + " (" + os() + "-" + arch() + "): " + src[2]);
            File pkg = new File(dir, src[2] + ".part");
            H.inst.get(src[0], pkg, null, src[1]);
            String kindA = src[2].endsWith(".zip") ? "zip" : "tgz";
            Archive.extract(pkg.toPath(), kindA, h.toPath().toAbsolutePath(), h.toPath().toAbsolutePath(), new Archive.Limits(4L << 30, 400_000), null, null);
            pkg.delete();
            // o extrator não guarda permissões: marca como executável o que é programa
            try (Stream<Path> s = Files.walk(h.toPath())) { s.filter(Files::isRegularFile).filter(p -> { String par = p.getParent() == null ? "" : p.getParent().getFileName().toString(); String n = p.getFileName().toString(); return par.equals("bin") || n.equals("jspawnhelper") || n.equals("jexec") || n.endsWith(".so") || n.contains(".so."); }).forEach(p -> p.toFile().setExecutable(true, false)); }
            if (!"java".equals(kind) && !"node".equals(kind) && !Sys.win()) {
                // python-build-standalone: bin/python3 é um link (que o extrator não cria) → copia o python3.X real
                File bin = null; try (Stream<Path> s = Files.walk(h.toPath(), 3)) { bin = s.filter(p -> p.getFileName().toString().equals("bin") && Files.isDirectory(p)).map(Path::toFile).findFirst().orElse(null); }
                if (bin != null && !new File(bin, "python3").isFile()) { File[] r = bin.listFiles((d, n) -> n.matches("python3\\.\\d+")); if (r != null && r.length > 0) { Files.copy(r[0].toPath(), new File(bin, "python3").toPath()); new File(bin, "python3").setExecutable(true, false); } }
            }
            Files.writeString(new File(h, ".ok").toPath(), src[2] + "\n" + System.currentTimeMillis());
            e = exe(kind, ver); if (e == null) throw new Api.Err("runtime_broken");
            say.accept("[MinesyncHost] " + kind + " " + ver + " pronto: " + e);
            return e;
        }
    }
    private JsonElement json(String url) throws Exception {
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(40)).header("User-Agent", "MinesyncHost/" + MinesyncHost.VERSION).header("Accept", "application/json").GET().build(), HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200) throw new Exception("HTTP " + r.statusCode() + " " + url);
        return JsonParser.parseString(r.body());
    }
    private String text(String url) throws Exception {
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(40)).header("User-Agent", "MinesyncHost/" + MinesyncHost.VERSION).GET().build(), HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200) throw new Exception("HTTP " + r.statusCode() + " " + url);
        return r.body();
    }
    /** Temurin JRE (JDK no 8, que não tem JRE para todas as plataformas) */
    private String[] java(String ver) throws Exception {
        for (String img : new String[]{ "jre", "jdk" }) {
            JsonElement j = json("https://api.adoptium.net/v3/assets/latest/" + ver + "/hotspot?architecture=" + arch() + "&image_type=" + img + "&os=" + os() + "&vendor=eclipse");
            if (!j.isJsonArray() || j.getAsJsonArray().isEmpty()) continue;
            JsonObject pk = j.getAsJsonArray().get(0).getAsJsonObject().getAsJsonObject("binary").getAsJsonObject("package");
            return new String[]{ pk.get("link").getAsString(), pk.has("checksum") ? pk.get("checksum").getAsString() : null, pk.get("name").getAsString() };
        }
        throw new Exception("Java " + ver + " não existe para " + os() + "-" + arch());
    }
    private String[] node(String major) throws Exception {
        String plat = (Sys.win() ? "win" : Sys.mac() ? "darwin" : "linux") + "-" + (arch().equals("aarch64") ? "arm64" : "x64");
        String ext = Sys.win() ? ".zip" : ".tar.gz";
        for (JsonElement e : json("https://nodejs.org/dist/index.json").getAsJsonArray()) {
            JsonObject o = e.getAsJsonObject(); String v = o.get("version").getAsString();
            if (!v.startsWith("v" + major + ".")) continue;
            String name = "node-" + v + "-" + plat + ext;
            String sums = text("https://nodejs.org/dist/" + v + "/SHASUMS256.txt"); String sha = null;
            for (String l : sums.split("\n")) if (l.trim().endsWith("  " + name)) sha = l.trim().split("\\s+")[0];
            return new String[]{ "https://nodejs.org/dist/" + v + "/" + name, sha, name };
        }
        throw new Exception("Node " + major + " não encontrado");
    }
    private String[] python(String ver) throws Exception {
        String triple = Sys.win() ? "x86_64-pc-windows-msvc" : Sys.mac() ? (arch().equals("aarch64") ? "aarch64-apple-darwin" : "x86_64-apple-darwin") : (arch().equals("aarch64") ? "aarch64-unknown-linux-gnu" : "x86_64-unknown-linux-gnu");
        JsonObject rel = json("https://api.github.com/repos/astral-sh/python-build-standalone/releases/latest").getAsJsonObject();
        JsonArray as = rel.getAsJsonArray("assets"); String url = null, name = null, shaUrl = null;
        for (JsonElement e : as) {
            JsonObject a = e.getAsJsonObject(); String n = a.get("name").getAsString();
            if (n.startsWith("cpython-" + ver + ".") && n.endsWith("-" + triple + "-install_only.tar.gz")) { url = a.get("browser_download_url").getAsString(); name = n; }
        }
        if (url == null) throw new Exception("Python " + ver + " não encontrado para " + triple);
        for (JsonElement e : as) { JsonObject a = e.getAsJsonObject(); if (a.get("name").getAsString().equals(name + ".sha256")) shaUrl = a.get("browser_download_url").getAsString(); }
        String sha = null; if (shaUrl != null) try { sha = text(shaUrl).trim().split("\\s+")[0]; } catch (Exception ignored) { }
        if (sha == null) { try { String all = text(url.substring(0, url.lastIndexOf('/') + 1) + "SHA256SUMS"); for (String l : all.split("\n")) if (l.trim().endsWith(name)) sha = l.trim().split("\\s+")[0]; } catch (Exception ignored) { } }
        return new String[]{ url, sha, name };
    }
    static String norm(String kind, String v) { if (v == null) return null; String x = v.trim().toLowerCase(Locale.ROOT); return x.isEmpty() || x.equals("host") || x.equals("default") ? null : x; }
}
