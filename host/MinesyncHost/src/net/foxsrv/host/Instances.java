package net.foxsrv.host;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SERVIDORES DA HOST: cada um numa pasta própria (servers/&lt;id&gt;/), com RAM, núcleos e disco limitados.
 * <ul>
 *  <li>Tipos: vanilla, paper, folia, purpur, fabric, forge, spigot (BuildTools) e canvas — sempre baixados das fontes
 *      oficiais (não aceita .jar próprio como servidor);</li>
 *  <li>Limites: -Xmx/-Xms, -XX:ActiveProcessorCount, núcleos fixos (taskset no Linux, afinidade no Windows), disco
 *      conferido a cada minuto (passou → desliga), RAM física conferida (estourou muito → força a parada);</li>
 *  <li>Isolamento (isolated: true): o servidor só escuta em 127.0.0.1 (a rede entra pela ponte, sem expor nada na rede
 *      local) e, no Linux com o bubblewrap, roda numa caixa: só enxerga a própria pasta (montada em /home/server) e o
 *      Java em modo só-leitura, com PID/IPC/UTS próprios e morre junto com a host.</li>
 * </ul>
 */
final class Instances {
    static final class Inst {
        String id, name, owner, type, version;
        long ram_mb, cores, disk_gb, port, created;
        boolean autostart, eula, minesync;
        // 1.1: ponte da rede pela host (null = ligada) · cadastro do servidor na rede · endereço do servidor externo
        Boolean bridge; String bridge_name, bridge_secret, bridge_id, address;
        // 1.2: backup automático (de X em X horas, mantendo N) · último automático
        long backup_every_h, backup_keep, backup_last;
        transient volatile boolean restoring;
        // 1.3: INICIALIZAÇÃO (arquivo principal, runtime, flags, argumentos, ambiente, parar, pronto, reiniciar se cair)
        String main, jvm_flags, args, stop_cmd, ready, java_rt, deps_hash; Map<String, String> env; Boolean aikar; boolean restart_on_crash, deps = true;
        // 1.3: subusuários (conta → permissões neste servidor) e agendamentos
        Map<String, List<String>> subusers; List<Schedules.Sched> schedules;
        transient volatile boolean userStop; transient final List<Long> crashes = new ArrayList<>();
        // 2.0: servidor CONTROLADO PELA CENTRAL (do cliente da rede ou o grátis do dono): no PC da host só aparece
        boolean central, free, unlimited, suspended;
        // 2.0: versão do runtime (baixado na pasta da host): Java 8–25, Node 18–24, Python 3.10–3.13 (null = o da host)
        String java_ver, node_ver, py_ver;
        transient volatile Map<String, Object> job; transient volatile Archive.Progress jobProgress;
        transient volatile String progWhat; transient volatile long progDone, progTotal = -1, progStart; transient volatile Archive.Progress progPr;   // 2.1.2: o que está instalando/baixando agora (para a % no site)
        transient volatile String state = "stopped";      // stopped | installing | starting | running | stopping | error
        transient volatile String note = "";
        transient volatile Process proc;
        transient volatile OutputStream stdin;
        transient final List<String> log = new ArrayList<>();
        transient final AtomicLong seq = new AtomicLong();
        transient volatile long rss = -1, diskBytes = -1, startedAt, cpuLastNs, cpuLastAt; transient volatile double cpu;
        // 2.1: histórico curto (gráficos do painel) e vigia de RAM (aviso em 99%, freio e parada só se passar do limite)
        transient final java.util.ArrayDeque<long[]> hist = new java.util.ArrayDeque<>(); transient volatile long overSince, warnAt, pingFail, lastPingOk;
        transient volatile String coreList = "";
    }
    static final String[] TYPES = { "paper", "purpur", "folia", "vanilla", "fabric", "forge", "spigot", "canvas", "external", "java", "nodejs", "python", "horizon" };
    /** 1.3: tipos "próprios" (o arquivo principal é seu: .jar, Node.js, Python ou 2.0 o executável do Horizon) */
    static boolean custom(String t) { return "java".equals(t) || "nodejs".equals(t) || "python".equals(t) || "horizon".equals(t); }
    static boolean mc(String t) { return !custom(t) && !"external".equals(t); }
    static String defMain(String t) { return "nodejs".equals(t) ? "index.js" : "python".equals(t) ? "main.py" : "horizon".equals(t) ? (Sys.win() ? "horizon-server.exe" : "horizon-server") : "server.jar"; }
    static String mainOf(Inst i) { return i.main == null || i.main.isBlank() ? defMain(i.type) : i.main; }
    static boolean bukkit(String t) { return List.of("paper", "purpur", "folia", "spigot", "canvas").contains(t); }

    private final MinesyncHost H;
    private final File root, store;
    final Map<String, Inst> all = new ConcurrentHashMap<>();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NORMAL).build();

    Instances(MinesyncHost h, File root, File store) { this.H = h; this.root = root; this.store = store; load(); }

    // ------------------------------------------------------------------ persistência
    private void load() {
        try { if (store.isFile()) { List<Inst> l = Json.G.fromJson(Files.readString(store.toPath(), StandardCharsets.UTF_8), new TypeToken<List<Inst>>() {}.getType()); if (l != null) for (Inst i : l) { fix(i); all.put(i.id, i); } } }
        catch (Exception e) { MinesyncHost.log("instances.json: " + e.getMessage()); }
    }
    private static void fix(Inst i) { try { java.lang.reflect.Field f = Inst.class.getDeclaredField("log"); f.setAccessible(true); if (f.get(i) == null) f.set(i, new ArrayList<String>()); f = Inst.class.getDeclaredField("seq"); f.setAccessible(true); if (f.get(i) == null) f.set(i, new AtomicLong()); f = Inst.class.getDeclaredField("crashes"); f.setAccessible(true); if (f.get(i) == null) f.set(i, new ArrayList<Long>()); i.state = "stopped"; i.note = ""; i.rss = -1; i.diskBytes = -1; i.coreList = ""; } catch (Exception ignored) { } }
    synchronized void save() {
        try { File t = new File(store.getPath() + ".tmp"); Files.writeString(t.toPath(), Json.PRETTY.toJson(new ArrayList<>(all.values())), StandardCharsets.UTF_8); Files.move(t.toPath(), store.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING); Users.restrict(store); }
        catch (Exception e) { MinesyncHost.log("instances.json: " + e.getMessage()); }
    }
    File dir(Inst i) { return new File(root, i.id); }

    // ------------------------------------------------------------------ recursos
    long usedRam(String except) { long s = 0; for (Inst i : all.values()) if (!i.id.equals(except)) s += i.ram_mb; return s; }
    long usedCores(String except) { long s = 0; for (Inst i : all.values()) if (!i.id.equals(except)) s += i.cores; return s; }
    long usedDisk(String except) { long s = 0; for (Inst i : all.values()) if (!i.id.equals(except)) s += i.disk_gb; return s; }
    Map<String, Object> free() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ram_mb", Math.max(0, H.conf.num("limits.max_ram_mb", 2048) - usedRam("")));
        m.put("cores", Math.max(0, H.conf.num("limits.max_cores", 2) - usedCores("")));
        m.put("disk_gb", Math.max(0, H.conf.num("limits.max_disk_gb", 32) - usedDisk("")));
        return m;
    }
    private long nextPort() {
        boolean pub = H.conf.bool("public_ip.enabled", false);
        long lo = 30000, hi = 30999;
        if (pub) { try { String[] r = H.conf.str("public_ip.ports", "25565-25600").split("-"); lo = Long.parseLong(r[0].trim()); hi = Long.parseLong(r[r.length - 1].trim()); } catch (Exception ignored) { } }
        for (long p = lo; p <= hi; p++) { final long q = p; if (all.values().stream().noneMatch(i -> i.port == q) && free((int) p)) return p; }
        throw new Api.Err("no_free_port");
    }
    private static boolean free(int port) { try (java.net.ServerSocket s = new java.net.ServerSocket()) { s.setReuseAddress(false); s.bind(new java.net.InetSocketAddress("127.0.0.1", port)); return true; } catch (Exception e) { return false; } }

    // ------------------------------------------------------------------ criar / apagar
    Inst create(Users.User by, JsonObject b) {
        if (!H.conf.bool("enabled", false)) throw new Api.Err("hosting_disabled");
        String type = Json.s(b, "type", "paper").toLowerCase(Locale.ROOT);
        if (!List.of(TYPES).contains(type)) throw new Api.Err("bad_type");
        String name = Json.s(b, "name", "Servidor").replaceAll("[<>\"'\\\\]", "").trim(); if (name.isEmpty() || name.length() > 40) throw new Api.Err("bad_name");
        // 1.3: o dono da host cria PARA uma conta (o servidor entra no limite dela)
        Users.User owner = by; String on = Json.s(b, "owner", "").trim();
        if (by.admin && !on.isEmpty()) { owner = H.users.get(on); if (owner == null) throw new Api.Err("no_such_user"); }
        if ("external".equals(type)) {   // 1.1: só a ponte para um servidor que já roda neste PC/rede (Horizon, outro programa…)
            String addr = Json.s(b, "address", "").trim().toLowerCase(Locale.ROOT);
            if (!addr.matches("[a-z0-9.-]{1,253}:\\d{1,5}") || !localAddr(addr)) throw new Api.Err("bad_address");
            H.accounts.checkCreate(by, owner, type, 0, 0, 0);
            Inst i = new Inst(); fix(i);
            i.id = "s" + Long.toString(System.currentTimeMillis(), 36) + Users.rnd(3).replaceAll("[^A-Za-z0-9]", "x").toLowerCase(Locale.ROOT);
            i.name = name; i.owner = owner.name; i.type = "external"; i.version = "-"; i.address = addr; i.created = System.currentTimeMillis(); i.eula = true; i.autostart = Json.b(b, "autostart", true); i.bridge = true;
            dir(i).mkdirs(); all.put(i.id, i); save(); i.state = "stopped";
            return i;
        }
        boolean cus = custom(type);
        String ver = cus ? "-" : Json.s(b, "version", "").trim(); if (!cus && !ver.matches("[0-9A-Za-z._-]{1,32}")) throw new Api.Err("bad_version");
        long ram = Json.n(b, "ram_mb", 2048), cores = Json.n(b, "cores", 2), disk = Json.n(b, "disk_gb", 10);
        if (ram < (cus ? 128 : 800) || ram > 65536) throw new Api.Err("bad_ram"); if (cores < 1 || cores > 32) throw new Api.Err("bad_cores"); if (disk < (cus ? 1 : 4) || disk > 1024) throw new Api.Err("bad_disk");
        if (!cus && !Json.b(b, "eula", false)) throw new Api.Err("eula_required");
        H.accounts.checkCreate(by, owner, type, ram, cores, disk);
        Map<String, Object> f = free();
        if (ram > ((Number) f.get("ram_mb")).longValue()) throw new Api.Err("not_enough_ram"); if (cores > ((Number) f.get("cores")).longValue()) throw new Api.Err("not_enough_cores"); if (disk > ((Number) f.get("disk_gb")).longValue()) throw new Api.Err("not_enough_disk");
        Inst i = new Inst(); fix(i);
        i.id = "s" + Long.toString(System.currentTimeMillis(), 36) + Users.rnd(3).replaceAll("[^A-Za-z0-9]", "x").toLowerCase(Locale.ROOT);
        i.name = name; i.owner = owner.name; i.type = type; i.version = ver; i.ram_mb = ram; i.cores = cores; i.disk_gb = disk; i.port = nextPort(); i.created = System.currentTimeMillis(); i.eula = true; i.autostart = Json.b(b, "autostart", false);
        i.minesync = bukkit(type) && H.conf.bool("network.install_minesync", true) && Json.b(b, "minesync", true);
        i.bridge = Json.b(b, "bridge", true);
        dir(i).mkdirs();
        if (cus) {   // 1.3: tipo próprio — nada a baixar; Node/Python ganham um exemplo que já sobe
            String mn = Json.s(b, "main", "").trim(); if (!mn.isEmpty()) i.main = checkMain(mn);
            i.aikar = false; i.stop_cmd = ""; i.ready = "";
            try {
                if ("nodejs".equals(type) && !new File(dir(i), mainOf(i)).exists()) Files.writeString(new File(dir(i), mainOf(i)).toPath(), "// MinesyncHost: exemplo — troque pelo seu app (porta em process.env.SERVER_PORT)\nconst http = require('http');\nconst port = Number(process.env.SERVER_PORT || 3000);\nhttp.createServer((q, r) => r.end('Olá do MinesyncHost!')).listen(port, process.env.SERVER_IP || '127.0.0.1', () => console.log('Rodando na porta ' + port));\n");
                if ("python".equals(type) && !new File(dir(i), mainOf(i)).exists()) Files.writeString(new File(dir(i), mainOf(i)).toPath(), "# MinesyncHost: exemplo — troque pelo seu app (porta em SERVER_PORT)\nimport os, http.server, socketserver\nport = int(os.environ.get('SERVER_PORT', '8000'))\nclass H(http.server.SimpleHTTPRequestHandler):\n    def do_GET(self):\n        self.send_response(200); self.end_headers(); self.wfile.write('Olá do MinesyncHost!'.encode())\nwith socketserver.TCPServer((os.environ.get('SERVER_IP', '127.0.0.1'), port), H) as s:\n    print('Rodando na porta', port, flush=True); s.serve_forever()\n");
            } catch (Exception ignored) { }
            all.put(i.id, i); save(); i.state = "stopped"; line(i, "[MinesyncHost] " + type + " pronto: envie seus arquivos e defina o arquivo principal em Inicialização.");
            return i;
        }
        all.put(i.id, i); save();
        install(i);
        return i;
    }
    void delete(Inst i) {
        kill(i);
        Bridge br = bridges.remove(i.id); if (br != null) br.stop();
        all.remove(i.id); save();
        H.pool.execute(() -> { try { Thread.sleep(1500); } catch (InterruptedException ignored) { } Files2.deleteTree(dir(i).toPath()); H.backups.removeServer(i.id); H.activity.remove(i.id); });
    }

    // ------------------------------------------------------------------ instalação (fontes oficiais)
    void install(Inst i) {
        if (!mc(i.type)) throw new Api.Err("bad_type");
        if ("running".equals(i.state) || "starting".equals(i.state) || "installing".equals(i.state)) throw new Api.Err("busy");
        i.state = "installing"; i.note = "";
        H.pool.execute(() -> {
            try {
                File d = dir(i); d.mkdirs();
                line(i, "[MinesyncHost] installing " + i.type + " " + i.version + "…");
                if (fromLibrary(i, d)) { /* 2.1: veio da biblioteca da central */ } else
                switch (i.type) {
                    case "vanilla": get(vanillaUrl(i.version), new File(d, "server.jar"), i, null); break;
                    case "paper": case "folia": paper(i, d); break;
                    case "purpur": get("https://api.purpurmc.org/v2/purpur/" + i.version + "/latest/download", new File(d, "server.jar"), i, null); break;
                    case "fabric": fabric(i, d); break;
                    case "forge": forge(i, d); break;
                    case "spigot": spigot(i, d); break;
                    case "canvas": canvas(i, d); break;
                    default: throw new Api.Err("bad_type");
                }
                if (i.minesync) {
                    File pl = new File(d, "plugins"); pl.mkdirs();
                    installMineSync(i, pl);
                }
                Files.writeString(new File(d, "eula.txt").toPath(), "# accepted in the MinesyncHost panel by " + i.owner + "\neula=true\n");
                props(i);
                i.state = "stopped"; i.note = ""; line(i, "[MinesyncHost] ready.");
                if (i.central) H.integrity.snap(i);
            } catch (Exception e) { i.state = "error"; i.note = "install: " + e.getMessage(); line(i, "[MinesyncHost] install failed: " + e.getMessage()); }
        });
    }
    /** 2.1: MineSync sempre o publicado (e conferido) na central — storage/assets da central */
    void installMineSync(Inst i, File pl) {
        String c = H.conf.central();
        for (String u : new String[]{ c + "/botcraft/api/minesync/download", c + "/botcraft/download/MineSync.jar" }) {
            try {
                File[] old = pl.listFiles((dd, n) -> n.toLowerCase(Locale.ROOT).matches("minesync([-_ ]?v?\\d[\\w.\\-]*)?\\.jar"));
                File to = new File(pl, "MineSync.jar"); getCentral(u, to, i);
                if (old != null) for (File f : old) if (!f.getName().equals("MineSync.jar")) f.delete();
                line(i, "[MinesyncHost] MineSync (o da central) instalado: rede sem IP fixo, login automático, voz"); return;
            } catch (Exception e) { if (u.endsWith(".jar")) line(i, "[MinesyncHost] MineSync não pôde ser baixado agora: " + e.getMessage()); }
        }
    }
    /** 2.1: BIBLIOTECA DE SERVIDORES DA CENTRAL (o admin envia uma vez e a rede inteira baixa dali primeiro) */
    boolean fromLibrary(Inst i, File d) {
        if ("forge".equals(i.type)) return false;
        try {
            getCentral(H.conf.central() + "/botcraft/hn/lib/" + i.type + "/" + java.net.URLEncoder.encode(i.version, StandardCharsets.UTF_8), new File(d, "server.jar"), i);
            line(i, "[MinesyncHost] " + i.type + " " + i.version + " veio da biblioteca da central"); return true;
        } catch (Exception e) { return false; }
    }
    /** baixa da CENTRAL (com a identidade da host e o sha256 que a central informa) */
    static void prog(Inst i, String what, long total) { i.progWhat = what; i.progTotal = total; i.progDone = 0; i.progStart = System.currentTimeMillis(); }
    void getCentral(String url, File to, Inst i) throws Exception {
        HttpResponse<java.io.InputStream> r = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(10)).header("User-Agent", "MinesyncHost/" + MinesyncHost.VERSION).header("x-host-id", H.net.id).header("x-host-secret", H.net.secret).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
        if (r.statusCode() != 200) { try { r.body().close(); } catch (Exception ignored) { } throw new Exception("HTTP " + r.statusCode()); }
        String sha = r.headers().firstValue("X-SHA256").orElse(null);
        File tmp = new File(to.getPath() + ".part"); MessageDigest md = MessageDigest.getInstance("SHA-256"); long n = 0;
        if (i != null) prog(i, to.getName(), r.headers().firstValueAsLong("content-length").orElse(-1));
        try (var in = r.body(); var out = Files.newOutputStream(tmp.toPath())) { byte[] b = new byte[65536]; int k; while ((k = in.read(b)) > 0) { n += k; if (i != null) i.progDone = n; if (n > 1024L * 1048576) throw new Exception("file too big"); md.update(b, 0, k); out.write(b, 0, k); } }
        finally { if (i != null) i.progWhat = null; }
        String got = HexFormat.of().formatHex(md.digest());
        if (n < 1024) { tmp.delete(); throw new Exception("empty"); }
        if (sha != null && !sha.isEmpty() && !sha.equalsIgnoreCase(got)) { tmp.delete(); throw new Exception("SHA-256 does not match"); }
        to.getParentFile().mkdirs(); Files.move(tmp.toPath(), to.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        if (i != null) line(i, "[MinesyncHost] " + to.getName() + " · " + (n / 1048576) + " MB · sha256 " + got.substring(0, 16) + "…");
    }
    private JsonElement getJson(String url) throws Exception {
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).header("User-Agent", "MinesyncHost/" + MinesyncHost.VERSION).GET().build(), HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200) throw new Exception("HTTP " + r.statusCode() + " " + url);
        return com.google.gson.JsonParser.parseString(r.body());
    }
    /** baixa conferindo o SHA-256 quando a fonte informa */
    void get(String url, File to, Inst i, String sha256) throws Exception {
        if (!url.startsWith("https://")) throw new Exception("only https");
        HttpResponse<java.io.InputStream> r = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(10)).header("User-Agent", "MinesyncHost/" + MinesyncHost.VERSION).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
        if (r.statusCode() != 200) throw new Exception("HTTP " + r.statusCode() + " " + url);
        File tmp = new File(to.getPath() + ".part"); MessageDigest md = MessageDigest.getInstance("SHA-256");
        long n = 0, max = 1024L * 1048576;
        if (i != null) prog(i, to.getName(), r.headers().firstValueAsLong("content-length").orElse(-1));
        try (var in = r.body(); var out = Files.newOutputStream(tmp.toPath())) { byte[] b = new byte[65536]; int k; while ((k = in.read(b)) > 0) { n += k; if (i != null) i.progDone = n; if (n > max) throw new Exception("file too big"); md.update(b, 0, k); out.write(b, 0, k); } }
        finally { if (i != null) i.progWhat = null; }
        String got = HexFormat.of().formatHex(md.digest());
        if (sha256 != null && !sha256.isEmpty() && !sha256.equalsIgnoreCase(got)) { tmp.delete(); throw new Exception("SHA-256 does not match (" + url + ")"); }
        Files.move(tmp.toPath(), to.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        if (i != null) line(i, "[MinesyncHost] " + to.getName() + " · " + (n / 1048576) + " MB · sha256 " + got.substring(0, 16) + "…");
    }
    String vanillaUrl(String v) throws Exception {
        JsonObject m = getJson("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json").getAsJsonObject();
        for (JsonElement e : m.getAsJsonArray("versions")) { JsonObject o = e.getAsJsonObject(); if (v.equals(Json.s(o, "id", ""))) { JsonObject d = getJson(Json.s(o, "url", "")).getAsJsonObject(); return d.getAsJsonObject("downloads").getAsJsonObject("server").get("url").getAsString(); } }
        throw new Exception("version not found");
    }
    private void paper(Inst i, File d) throws Exception {
        String proj = i.type; String url = null, sha = null;
        try {   // Fill v3
            JsonObject b = getJson("https://fill.papermc.io/v3/projects/" + proj + "/versions/" + i.version + "/builds/latest").getAsJsonObject();
            JsonObject dl = b.getAsJsonObject("downloads").getAsJsonObject("server:default"); url = Json.s(dl, "url", null);
            JsonObject cs = dl.getAsJsonObject("checksums"); if (cs != null) sha = Json.s(cs, "sha256", null);
        } catch (Exception e) {   // API v2 (antiga)
            JsonArray builds = getJson("https://api.papermc.io/v2/projects/" + proj + "/versions/" + i.version + "/builds").getAsJsonObject().getAsJsonArray("builds");
            JsonObject last = builds.get(builds.size() - 1).getAsJsonObject(); JsonObject app = last.getAsJsonObject("downloads").getAsJsonObject("application");
            url = "https://api.papermc.io/v2/projects/" + proj + "/versions/" + i.version + "/builds/" + last.get("build").getAsInt() + "/downloads/" + Json.s(app, "name", ""); sha = Json.s(app, "sha256", null);
        }
        get(url, new File(d, "server.jar"), i, sha);
    }
    private void fabric(Inst i, File d) throws Exception {
        String loader = getJson("https://meta.fabricmc.net/v2/versions/loader/" + i.version).getAsJsonArray().get(0).getAsJsonObject().getAsJsonObject("loader").get("version").getAsString();
        String inst = getJson("https://meta.fabricmc.net/v2/versions/installer").getAsJsonArray().get(0).getAsJsonObject().get("version").getAsString();
        get("https://meta.fabricmc.net/v2/versions/loader/" + i.version + "/" + loader + "/" + inst + "/server/jar", new File(d, "server.jar"), i, null);
    }
    private void forge(Inst i, File d) throws Exception {
        JsonObject promos = getJson("https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json").getAsJsonObject().getAsJsonObject("promos");
        String fv = Json.s(promos, i.version + "-recommended", Json.s(promos, i.version + "-latest", null)); if (fv == null) throw new Exception("Forge has no build for " + i.version);
        String full = i.version + "-" + fv; File inst = new File(d, "forge-installer.jar");
        get("https://maven.minecraftforge.net/net/minecraftforge/forge/" + full + "/forge-" + full + "-installer.jar", inst, i, null);
        run(i, d, List.of(Sys.java(), "-jar", inst.getName(), "--installServer"), 20);
        inst.delete();
    }
    private void spigot(Inst i, File d) throws Exception {
        File bt = new File(d, "BuildTools.jar");
        get("https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar", bt, i, null);
        File work = new File(d, ".buildtools"); work.mkdirs(); Files.move(bt.toPath(), new File(work, "BuildTools.jar").toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        run(i, work, List.of(Sys.java(), "-jar", "BuildTools.jar", "--rev", i.version, "--output-dir", d.getAbsolutePath()), 40);
        File[] out = d.listFiles((x, n) -> n.startsWith("spigot-") && n.endsWith(".jar"));
        if (out == null || out.length == 0) throw new Exception("BuildTools did not produce spigot.jar");
        Files.move(out[0].toPath(), new File(d, "server.jar").toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Files2.deleteTree(work.toPath());
    }
    /** Canvas (fork do Folia): endereço da API configurável em downloads.canvas_api (a resposta precisa trazer a URL do .jar) */
    private void canvas(Inst i, File d) throws Exception {
        String tpl = H.conf.str("downloads.canvas_api", "https://canvasmc.io/api/v2/builds/latest?minecraft_version={version}");
        JsonElement j = getJson(tpl.replace("{version}", i.version));
        String url = null;
        if (j.isJsonObject()) { JsonObject o = j.getAsJsonObject(); for (String k : new String[]{ "downloadUrl", "download_url", "url", "jar" }) { url = Json.s(o, k, null); if (url != null) break; } }
        if (url == null) throw new Exception("Canvas API did not return a download URL (set downloads.canvas_api in config.yml)");
        get(url, new File(d, "server.jar"), i, null);
    }
    private void run(Inst i, File cwd, List<String> cmd, int minutes) throws Exception {
        Process p = new ProcessBuilder(cmd).directory(cwd).redirectErrorStream(true).start();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) { String l; while ((l = r.readLine()) != null) line(i, l); }
        if (!p.waitFor(minutes, java.util.concurrent.TimeUnit.MINUTES)) { p.destroyForcibly(); throw new Exception("timeout"); }
        if (p.exitValue() != 0) throw new Exception("exit " + p.exitValue());
    }
    /** server.properties: porta da host, só 127.0.0.1 (a rede entra pela ponte), aceita quem chega por portal */
    void props(Inst i) {
        if (!mc(i.type)) return;
        try {
            File f = new File(dir(i), "server.properties"); Properties p = new Properties();
            if (f.isFile()) try (var in = Files.newBufferedReader(f.toPath(), StandardCharsets.ISO_8859_1)) { p.load(in); }
            p.setProperty("server-port", String.valueOf(i.port));
            boolean pub = H.conf.bool("public_ip.enabled", false);
            p.setProperty("server-ip", pub ? "" : "127.0.0.1");
            p.setProperty("accepts-transfers", "true");
            p.setProperty("enable-rcon", "false"); p.setProperty("enable-query", "false");
            if (!p.containsKey("motd")) p.setProperty("motd", i.name);
            try (var out = Files.newBufferedWriter(f.toPath(), StandardCharsets.ISO_8859_1)) { p.store(out, "MinesyncHost: server-port/server-ip are managed by the host"); }
        } catch (Exception e) { line(i, "[MinesyncHost] server.properties: " + e.getMessage()); }
    }

    // ------------------------------------------------------------------ ligar / desligar
    synchronized void start(Inst i) {
        if (i.restoring) throw new Api.Err("busy");
        if (i.suspended) throw new Api.Err("suspended", 403);
        if (!H.conf.bool("enabled", false)) throw new Api.Err("hosting_disabled");
        if (i.proc != null && i.proc.isAlive()) throw new Api.Err("already_running");
        if ("external".equals(i.type)) { i.state = "running"; i.startedAt = System.currentTimeMillis(); line(i, "[MinesyncHost] bridge on for " + i.address); return; }
        if ("installing".equals(i.state)) throw new Api.Err("busy");
        File d = dir(i);
        boolean forge = "forge".equals(i.type) && (i.main == null || i.main.isBlank());
        if (!forge && !new File(d, mainOf(i)).isFile()) throw new Api.Err(custom(i.type) ? "main_not_found" : "not_installed");
        Users.User own = H.users.get(i.owner); if (own != null && own.suspended) throw new Api.Err("account_suspended", 403);
        long disk = Sys.dirSize(d.toPath()); i.diskBytes = disk;
        if (disk > i.disk_gb * 1073741824L) throw new Api.Err("disk_full");
        props(i);
        // 2.0: runtime da versão escolhida (baixa na pasta da host na primeira vez)
        String need = needRuntime(i);
        if (need != null) {
            i.state = "installing";
            H.pool.execute(() -> {
                try { String[] k = need.split(":"); prog(i, "runtime " + k[0] + " " + k[1], -1); try { H.runtimes.ensure(k[0], k[1], l -> line(i, l)); } finally { i.progWhat = null; } i.state = "stopped"; start(i); }
                catch (Exception e) { i.state = "error"; i.note = "runtime: " + e.getMessage(); line(i, "[MinesyncHost] runtime: " + e.getMessage()); }
            });
            return;
        }
        List<String> dep = deps(i, d);
        if (dep != null) {   // 1.3: npm install / pip install antes (só quando package.json / requirements.txt mudou)
            i.state = "installing"; line(i, "[MinesyncHost] dependências: " + String.join(" ", dep.subList(0, Math.min(3, dep.size()))) + "…");
            prog(i, "deps " + ("nodejs".equals(i.type) ? "npm" : "pip"), -1);
            H.pool.execute(() -> {
                try { Process p = env(i, d, new ProcessBuilder(wrap(i, d, dep, false)).directory(d).redirectErrorStream(true)).start(); try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) { String l; while ((l = r.readLine()) != null) line(i, l); } if (!p.waitFor(20, java.util.concurrent.TimeUnit.MINUTES)) p.destroyForcibly(); if (p.exitValue() == 0) { i.deps_hash = depsHash(d, i); save(); } else line(i, "[MinesyncHost] dependências: saiu com " + p.exitValue()); }
                catch (Exception e) { line(i, "[MinesyncHost] dependências: " + e.getMessage()); }
                i.progWhat = null; i.state = "stopped"; try { launch(i, d); } catch (Exception e) { line(i, "[MinesyncHost] " + e.getMessage()); }
            });
            return;
        }
        launch(i, d);
    }
    private synchronized void launch(Inst i, File d) {
        List<String> cmd = command(i, d);
        i.userStop = false;
        try {
            ProcessBuilder pb = env(i, d, new ProcessBuilder(cmd).directory(d).redirectErrorStream(true));
            Process p = pb.start(); i.proc = p; i.stdin = p.getOutputStream(); i.state = "starting"; i.note = ""; i.startedAt = System.currentTimeMillis(); i.cpuLastNs = 0;
            line(i, "[MinesyncHost] starting: " + i.ram_mb + " MB RAM · " + i.cores + " cores" + (i.coreList.isEmpty() ? "" : " (" + i.coreList + ")") + " · port " + i.port + (isolatedLinux() ? " · isolated (bubblewrap)" : H.conf.bool("isolated", true) ? " · isolated (127.0.0.1 only)" : ""));
            if (Sys.win() && !i.coreList.isEmpty()) winAffinity(p.pid(), i.coreList);
            Thread t = new Thread(() -> pump(i, p), "msh-" + i.id); t.setDaemon(true); t.start();
        } catch (Exception e) { i.state = "error"; i.note = e.getMessage(); throw new Api.Err("start_failed: " + e.getMessage()); }
    }
    boolean isolatedLinux() { return H.conf.bool("isolated", true) && Sys.linux() && Sys.which("bwrap") != null; }
    private String cores(Inst i) {
        int total = Runtime.getRuntime().availableProcessors(); int need = (int) Math.min(i.cores, total);
        // núcleos fixos e diferentes para cada servidor (o primeiro núcleo fica para o sistema quando dá)
        int start = total > need + 1 ? 1 : 0; int off = 0;
        for (Inst o : all.values()) if (o != i && o.proc != null && o.proc.isAlive()) off += o.cores;
        StringBuilder b = new StringBuilder();
        for (int k = 0; k < need; k++) { if (b.length() > 0) b.append(','); b.append(start + (off + k) % Math.max(1, total - start)); }
        return b.toString();
    }
    /** 1.3: ambiente limpo + variáveis da host (porta, IP, memória) + as do servidor */
    ProcessBuilder env(Inst i, File d, ProcessBuilder pb) {
        Map<String, String> e = pb.environment();
        e.keySet().removeIf(k -> !List.of("PATH", "LANG", "LC_ALL", "SystemRoot", "TEMP", "TMP", "windir", "ComSpec", "PATHEXT").contains(k));   // nada do ambiente da host vaza
        String home = isolatedLinux() ? "/home/server" : d.getAbsolutePath();
        e.put("HOME", home);
        e.put("SERVER_PORT", String.valueOf(i.port)); e.put("SERVER_IP", ip()); e.put("SERVER_MEMORY", String.valueOf(i.ram_mb)); e.put("SERVER_NAME", i.name);
        if ("python".equals(i.type)) { e.put("PYTHONUNBUFFERED", "1"); e.put("PYTHONPATH", home + "/.pydeps"); e.put("PYTHONUSERBASE", home + "/.pydeps"); }
        if ("nodejs".equals(i.type)) { e.put("NODE_ENV", "production"); e.put("npm_config_cache", home + "/.npm"); }
        if (i.env != null) for (Map.Entry<String, String> x : i.env.entrySet()) if (okEnv(x.getKey())) e.put(x.getKey(), vars(i, x.getValue()));
        return pb;
    }
    static boolean okEnv(String k) { return k != null && k.matches("[A-Za-z_][A-Za-z0-9_]{0,63}") && !k.equals("PATH") && !k.equals("HOME") && !k.startsWith("LD_") && !k.startsWith("DYLD_") && !k.equals("JAVA_TOOL_OPTIONS") && !k.equals("_JAVA_OPTIONS") && !k.equals("NODE_OPTIONS"); }
    String ip() { return H.conf.bool("public_ip.enabled", false) ? "0.0.0.0" : "127.0.0.1"; }
    String vars(Inst i, String v) { return v == null ? "" : v.replace("{{PORT}}", String.valueOf(i.port)).replace("{{IP}}", ip()).replace("{{RAM}}", String.valueOf(i.ram_mb)).replace("{{MAIN}}", mainOf(i)).replace("{{NAME}}", i.name); }
    /** separa argumentos respeitando aspas */
    static List<String> split(String s) {
        List<String> out = new ArrayList<>(); if (s == null) return out; StringBuilder b = new StringBuilder(); char q = 0; boolean any = false;
        for (char c : s.toCharArray()) { if (q != 0) { if (c == q) q = 0; else b.append(c); } else if (c == '"' || c == '\'') { q = c; any = true; } else if (Character.isWhitespace(c)) { if (b.length() > 0 || any) { out.add(b.toString()); b.setLength(0); any = false; } } else b.append(c); }
        if (b.length() > 0 || any) out.add(b.toString()); return out;
    }
    /** arquivo principal: caminho dentro da pasta do servidor (sem "..", sem caminho absoluto) */
    static String checkMain(String m) {
        String x = m.replace('\\', '/').trim(); while (x.startsWith("./")) x = x.substring(2);
        if (x.isEmpty() || x.length() > 200 || x.startsWith("/") || x.matches("^[A-Za-z]:.*") || ("/" + x + "/").contains("/../") || !x.matches("[A-Za-z0-9._ /+@-]+")) throw new Api.Err("bad_main");
        return x;
    }
    // ------------------------------------------------------------------ 1.3 runtimes (Java extra, Node.js, Python)
    /** Java para este servidor: o da host ou um extra do config (runtimes.java: ["17=/caminho/do/java", …]) */
    /** "java:17" etc. quando a versão escolhida ainda não foi baixada */
    private String needRuntime(Inst i) {
        if (i.java_ver != null && !"nodejs".equals(i.type) && !"python".equals(i.type) && !"horizon".equals(i.type) && !H.runtimes.installed("java", i.java_ver)) return "java:" + i.java_ver;
        if ("nodejs".equals(i.type) && i.node_ver != null && !H.runtimes.installed("node", i.node_ver)) return "node:" + i.node_ver;
        if ("python".equals(i.type) && i.py_ver != null && !H.runtimes.installed("python", i.py_ver)) return "python:" + i.py_ver;
        return null;
    }
    String nodeFor(Inst i) { if (i.node_ver != null) { String e = H.runtimes.exe("node", i.node_ver); if (e != null) return e; } return node(); }
    String pythonFor(Inst i) { if (i.py_ver != null) { String e = H.runtimes.exe("python", i.py_ver); if (e != null) return e; } return python(); }
    String javaFor(Inst i) {
        if (i.java_ver != null) { String e = H.runtimes.exe("java", i.java_ver); if (e != null) return e; }
        if (i.java_rt != null && !i.java_rt.isBlank()) for (Map.Entry<String, String> e : javaRuntimes().entrySet()) if (e.getKey().equals(i.java_rt) && new File(e.getValue()).isFile()) return e.getValue();
        return Sys.java();
    }
    Map<String, String> javaRuntimes() {
        Map<String, String> m = new LinkedHashMap<>();
        for (Object o : H.conf.list("runtimes.java")) { String l = String.valueOf(o); int k = l.indexOf('='); if (k > 0) { String n = l.substring(0, k).trim(), p = l.substring(k + 1).trim(); if (n.matches("[A-Za-z0-9._-]{1,24}") && !p.isEmpty()) m.put(n, p); } }
        return m;
    }
    String node() { String c = H.conf.str("runtimes.node", "").trim(); if (!c.isEmpty() && new File(c).isFile()) return c; return Sys.which("node"); }
    String npm() { String n = node(); if (n != null) { File f = new File(new File(n).getParentFile(), Sys.win() ? "npm.cmd" : "npm"); if (f.isFile()) return f.getAbsolutePath(); } return Sys.which(Sys.win() ? "npm.cmd" : "npm"); }
    String python() { String c = H.conf.str("runtimes.python", "").trim(); if (!c.isEmpty() && new File(c).isFile()) return c; String p = Sys.which("python3"); return p != null ? p : Sys.which("python"); }
    Map<String, Object> runtimes() {
        Map<String, Object> m = new LinkedHashMap<>();
        List<String> j = new ArrayList<>(); j.add(""); j.addAll(javaRuntimes().keySet()); m.put("java", j); m.put("java_default", System.getProperty("java.version"));
        m.put("node", node() != null); m.put("python", python() != null);
        m.put("versions", Map.of("java", Runtimes.JAVA, "node", Runtimes.NODE, "python", Runtimes.PY)); m.put("downloaded", H.runtimes.view()); return m;
    }
    private String depsHash(File d, Inst i) {
        File f = new File(d, "nodejs".equals(i.type) ? "package.json" : "requirements.txt"); if (!f.isFile()) return null;
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(f.toPath()))); } catch (Exception e) { return null; }
    }
    private List<String> deps(Inst i, File d) {
        if (!i.deps || !custom(i.type) || "java".equals(i.type)) return null;
        String h = depsHash(d, i); if (h == null || h.equals(i.deps_hash)) return null;
        if ("nodejs".equals(i.type)) {
            String nd = nodeFor(i), cli = i.node_ver != null ? H.runtimes.npmCli(nd) : null;
            if (cli != null) return new ArrayList<>(List.of(nd, cli, "install", "--omit=dev", "--no-audit", "--no-fund"));
            String n = npm(); if (n == null) { line(i, "[MinesyncHost] npm não encontrado na host"); return null; } return new ArrayList<>(List.of(n, "install", "--omit=dev", "--no-audit", "--no-fund"));
        }
        String py = pythonFor(i); if (py == null) return null;
        return new ArrayList<>(List.of(py, "-m", "pip", "install", "-r", "requirements.txt", "--target", ".pydeps", "--upgrade", "--disable-pip-version-check"));
    }
    /** a linha de comando do servidor (sem o isolamento) */
    List<String> raw(Inst i, File d) {
        List<String> run = new ArrayList<>();
        String main = mainOf(i);
        List<String> flags = new ArrayList<>(); for (String f : split(vars(i, i.jvm_flags))) if (!f.startsWith("-Xmx") && !f.startsWith("-Xms")) flags.add(f);
        List<String> args = split(vars(i, i.args == null ? (mc(i.type) ? "nogui" : "") : i.args));
        if ("horizon".equals(i.type)) {   // 2.0: executável próprio (servidor Horizon/Bedrock enviado pelo dono), roda direto na caixa
            File ex = new File(d, main); if (!ex.isFile()) throw new Api.Err("main_not_found"); ex.setExecutable(true, true);
            run.add(isolatedLinux() ? "/home/server/" + main : ex.getAbsolutePath()); run.addAll(args); return run;
        }
        if ("nodejs".equals(i.type)) {
            String n = nodeFor(i); if (n == null) throw new Api.Err("node_not_found");
            run.add(n); run.add("--max-old-space-size=" + Math.max(64, i.ram_mb * 9 / 10)); run.addAll(flags); run.add(main); run.addAll(args); return run;
        }
        if ("python".equals(i.type)) {
            String py = pythonFor(i); if (py == null) throw new Api.Err("python_not_found");
            run.add(py); run.add("-u"); run.addAll(flags); run.add(main); run.addAll(args); return run;
        }
        List<String> jvm = new ArrayList<>();
        // 2.1: o LIMITE é do processo inteiro (heap + metaspace + código + memória direta + threads): o heap fica abaixo do
        // limite e o resto tem teto próprio → o servidor bate no limite e o GC segura, em vez de passar do que foi contratado
        long ram = Math.max(256, i.ram_mb), over = Math.max(160, Math.min(1024, ram * 15 / 100)), heap = Math.max(128, ram - over);
        long xms = Math.min(heap, Math.max(Math.min(256, heap), heap / 2));
        jvm.add("-Xms" + xms + "M"); jvm.add("-Xmx" + heap + "M");
        jvm.add("-XX:MaxMetaspaceSize=" + Math.max(96, Math.min(512, ram * 12 / 100)) + "M"); jvm.add("-XX:ReservedCodeCacheSize=" + (ram >= 1536 ? 64 : 32) + "M");
        jvm.add("-XX:MaxDirectMemorySize=" + Math.max(32, Math.min(512, ram * 5 / 100)) + "M"); jvm.add("-XX:+UseStringDeduplication");
        jvm.add("-XX:ActiveProcessorCount=" + i.cores);
        // GC automático (flags do Aikar, G1): pausas curtas, devolve memória quando sobra
        if (!Boolean.FALSE.equals(i.aikar) && !(i.aikar == null && custom(i.type))) for (String f : "-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M -XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 -XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1 -XX:G1PeriodicGCInterval=60000".split(" ")) jvm.add(f);
        jvm.add("-Dfile.encoding=UTF-8"); jvm.add("-Duser.home=" + (isolatedLinux() ? "/home/server" : d.getAbsolutePath()));
        jvm.addAll(flags);
        String java = javaFor(i);
        if ("forge".equals(i.type) && (i.main == null || i.main.isBlank())) {
            String fa = forgeArgs(d);
            if (fa == null) throw new Api.Err("not_installed");
            run.add(java); run.addAll(jvm); run.add("@" + fa); run.addAll(args);
        } else { run.add(java); run.addAll(jvm); run.add("-jar"); run.add(main); run.addAll(args); }
        return run;
    }
    private List<String> command(Inst i, File d) { List<String> c = wrap(i, d, raw(i, d), true); return c; }
    /** núcleos fixos (taskset) + caixa fechada no Linux (bubblewrap) */
    List<String> wrap(Inst i, File d, List<String> run, boolean pin) {
        if (pin) i.coreList = cores(i);
        List<String> out = new ArrayList<>();
        if (pin && Sys.linux() && Sys.which("taskset") != null && !i.coreList.isEmpty()) { out.add("taskset"); out.add("-c"); out.add(i.coreList); }
        if (isolatedLinux()) {
            String jh = Sys.javaHome();
            out.addAll(List.of("bwrap", "--die-with-parent", "--new-session", "--unshare-pid", "--unshare-ipc", "--unshare-uts", "--unshare-cgroup-try", "--hostname", "minesync-" + i.id,
                    "--ro-bind", "/usr", "/usr", "--ro-bind-try", "/lib", "/lib", "--ro-bind-try", "/lib64", "/lib64", "--ro-bind-try", "/bin", "/bin", "--ro-bind-try", "/sbin", "/sbin",
                    "--ro-bind-try", "/etc/ssl", "/etc/ssl", "--ro-bind-try", "/etc/ca-certificates", "/etc/ca-certificates", "--ro-bind-try", "/etc/pki", "/etc/pki", "--ro-bind-try", "/etc/resolv.conf", "/etc/resolv.conf",
                    "--ro-bind-try", "/etc/hosts", "/etc/hosts", "--ro-bind-try", "/etc/nsswitch.conf", "/etc/nsswitch.conf", "--ro-bind-try", "/etc/alternatives", "/etc/alternatives", "--ro-bind-try", "/etc/java-21-openjdk", "/etc/java-21-openjdk",
                    "--ro-bind", jh, jh));
            // runtimes fora do /usr (Java extra, Node em /opt…): só leitura
            if (!run.isEmpty()) { String home = runtimeHome(run.get(0)); if (home != null && !home.startsWith("/usr/") && !home.equals(jh)) { out.add("--ro-bind-try"); out.add(home); out.add(home); } }
            out.addAll(List.of("--bind", d.getAbsolutePath(), "/home/server", "--chdir", "/home/server", "--proc", "/proc", "--dev", "/dev", "--tmpfs", "/tmp", "--setenv", "HOME", "/home/server", "--"));
            for (String a : run) out.add(a.replace("-Duser.home=" + d.getAbsolutePath(), "-Duser.home=/home/server"));
        } else out.addAll(run);
        return out;
    }
    private static String runtimeHome(String exe) { try { Path p = Path.of(exe).toRealPath(); Path bin = p.getParent(); return bin != null && bin.getParent() != null ? bin.getParent().toString() : null; } catch (Exception e) { return null; } }
    private static String forgeArgs(File d) {
        try (var s = Files.walk(new File(d, "libraries").toPath())) {
            String want = Sys.win() ? "win_args.txt" : "unix_args.txt";
            return s.filter(p -> p.getFileName().toString().equals(want)).map(p -> d.toPath().relativize(p).toString().replace('\\', '/')).findFirst().orElse(null);
        } catch (Exception e) { return null; }
    }
    private static void winAffinity(long pid, String list) {
        long mask = 0; for (String c : list.split(",")) try { mask |= 1L << Integer.parseInt(c.trim()); } catch (Exception ignored) { }
        try { new ProcessBuilder("powershell", "-NoProfile", "-Command", "(Get-Process -Id " + pid + ").ProcessorAffinity=" + mask).redirectErrorStream(true).start(); } catch (Exception ignored) { }
    }
    private void pump(Inst i, Process p) {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String rd = i.ready == null || i.ready.isBlank() ? (mc(i.type) ? null : "") : i.ready;
            if ("".equals(rd)) i.state = "running";   // tipo próprio sem "pronto quando": já está rodando
            String l; while ((l = r.readLine()) != null) { line(i, l); if ("starting".equals(i.state) && (rd == null ? l.contains("Done (") || l.contains("For help, type") : l.contains(rd))) i.state = "running"; }
        } catch (Exception ignored) { }
        try { p.waitFor(); } catch (InterruptedException ignored) { }
        int code = p.isAlive() ? -1 : p.exitValue();
        line(i, "[MinesyncHost] stopped (exit " + code + ")");
        if (i.central) H.pool.execute(() -> { try { Thread.sleep(1500); } catch (InterruptedException ignored) { } H.integrity.snap(i); });   // 2.0: desligou — nova foto (o mundo mudou ligado)
        if (i.proc == p) {
            i.proc = null; i.stdin = null; i.state = "stopping".equals(i.state) || code == 0 || i.userStop ? "stopped" : "error"; if (!"stopped".equals(i.state) && i.note.isEmpty()) i.note = "exit " + code; i.rss = -1; i.cpu = 0;
            // 1.3: caiu sozinho → liga de novo (no máximo 3 vezes em 10 min)
            if (i.restart_on_crash && !i.userStop && "error".equals(i.state) && H.conf.bool("enabled", false)) {
                long now = System.currentTimeMillis(); synchronized (i.crashes) { i.crashes.removeIf(t -> now - t > 600_000L); i.crashes.add(now); }
                if (i.crashes.size() <= 3) { line(i, "[MinesyncHost] caiu (exit " + code + ") — ligando de novo em 5 s"); H.timer.schedule(() -> { try { if (i.proc == null && all.containsKey(i.id)) start(i); } catch (Exception e) { line(i, "[MinesyncHost] " + e.getMessage()); } }, 5, java.util.concurrent.TimeUnit.SECONDS); }
                else line(i, "[MinesyncHost] caiu 3 vezes em 10 min — não vou religar sozinho");
            }
        }
    }
    void line(Inst i, String l) {
        synchronized (i.log) { i.log.add(l.length() > 4000 ? l.substring(0, 4000) : l); if (i.log.size() > 2500) i.log.subList(0, i.log.size() - 2000).clear(); i.seq.incrementAndGet(); }
    }
    Map<String, Object> console(Inst i, long since) {
        Map<String, Object> m = new LinkedHashMap<>();
        synchronized (i.log) {
            long s = i.seq.get(); int n = i.log.size(); long first = s - n;
            int from = since <= 0 ? Math.max(0, n - 400) : (int) Math.max(0, Math.min(n, since - first));
            m.put("seq", s); m.put("lines", new ArrayList<>(i.log.subList(from, n)));
        }
        return m;
    }
    void command(Inst i, String cmd) {
        if (i.stdin == null || i.proc == null || !i.proc.isAlive()) throw new Api.Err("not_running");
        String c = cmd.replace("\r", "").replace("\n", " ").trim(); if (c.isEmpty() || c.length() > 1000) throw new Api.Err("bad_command");
        if (c.startsWith("/")) c = c.substring(1);
        try { i.stdin.write((c + "\n").getBytes(StandardCharsets.UTF_8)); i.stdin.flush(); line(i, "> " + c); } catch (Exception e) { throw new Api.Err("not_running"); }
    }
    void stop(Inst i) {
        if ("external".equals(i.type)) { i.state = "stopped"; line(i, "[MinesyncHost] bridge off"); return; }
        Process p = i.proc; if (p == null || !p.isAlive()) { i.state = "stopped"; return; }
        i.state = "stopping"; i.userStop = true;
        String sc = i.stop_cmd == null ? (mc(i.type) ? "stop" : "") : i.stop_cmd.trim();
        if (sc.isEmpty()) { try { p.descendants().forEach(ProcessHandle::destroy); } catch (Exception ignored) { } p.destroy(); }   // Node/Python: SIGTERM
        else try { if (sc.startsWith("^C")) p.destroy(); else { i.stdin.write((sc + "\n").getBytes(StandardCharsets.UTF_8)); i.stdin.flush(); } } catch (Exception ignored) { }
        H.pool.execute(() -> { try { if (!p.waitFor(45, java.util.concurrent.TimeUnit.SECONDS)) { line(i, "[MinesyncHost] did not stop in 45 s — forcing"); kill(i); } } catch (InterruptedException ignored) { } });
    }
    void kill(Inst i) {
        if ("external".equals(i.type)) { i.state = "stopped"; return; }
        Process p = i.proc; if (p == null) return;
        i.state = "stopping"; i.userStop = true;
        try { p.descendants().forEach(ProcessHandle::destroyForcibly); } catch (Exception ignored) { }
        p.destroyForcibly();
    }
    void stopAll(boolean wait) {
        for (Bridge b : bridges.values()) b.stop(); bridges.clear();
        for (Inst i : all.values()) if (i.proc != null && i.proc.isAlive()) stop(i);
        if (wait) { long until = System.currentTimeMillis() + 50_000; for (Inst i : all.values()) { Process p = i.proc; if (p == null) continue; try { p.waitFor(Math.max(1, until - System.currentTimeMillis()), java.util.concurrent.TimeUnit.MILLISECONDS); } catch (InterruptedException ignored) { } if (p.isAlive()) kill(i); } }
    }

    // ------------------------------------------------------------------ vigia (a cada 5 s)
    private long diskTick;
    // ------------------------------------------------------------------ 1.1 ponte da rede pela host
    final Map<String, Bridge> bridges = new ConcurrentHashMap<>();
    /** o servidor já tem o plugin MineSync? então a ponte é dele (a host não duplica o servidor na rede) */
    boolean hasMineSync(Inst i) {
        File pl = new File(dir(i), "plugins"); File[] f = pl.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).matches("minesync([-_ ]?v?\\d[\\w.\\-]*)?\\.jar"));
        return f != null && f.length > 0;
    }
    boolean wantsBridge(Inst i) {
        return !custom(i.type) && H.conf.bool("network.bridge", true) && !Boolean.FALSE.equals(i.bridge) && !hasMineSync(i)
            && ("running".equals(i.state) || "starting".equals(i.state));
    }
    void bridges() {
        for (Inst i : all.values()) {
            Bridge b = bridges.get(i.id);
            if (wantsBridge(i)) { if (b == null) { b = new Bridge(H, i); bridges.put(i.id, b); b.start(); } b.schedule(); }
            else if (b != null) { bridges.remove(i.id); b.stop(); }
        }
        bridges.keySet().removeIf(k -> { if (all.containsKey(k)) return false; Bridge b = bridges.get(k); if (b != null) b.stop(); return true; });
    }
    /** 1.1: endereço de servidor externo só deste PC ou da rede local (a host não vira ponte para a internet dos outros) */
    static boolean localAddr(String a) {
        String h = a.substring(0, a.lastIndexOf(':'));
        if (h.equals("localhost")) return true;
        try { java.net.InetAddress x = java.net.InetAddress.getByName(h); return x.isLoopbackAddress() || x.isSiteLocalAddress() || x.isLinkLocalAddress(); } catch (Exception e) { return false; }
    }
    // limite de banda da host (limits.bandwidth_mbps, somando todos os servidores; 0 = sem limite)
    private long bwWin = 0, bwUsed = 0;
    void band(long n) {
        long mbps = H.conf.num("limits.bandwidth_mbps", 0); if (mbps <= 0) return;
        long perSec = mbps * 125_000L; long wait = 0;
        synchronized (this) { long now = System.currentTimeMillis(); if (now - bwWin >= 1000) { bwWin = now; bwUsed = 0; } bwUsed += n; if (bwUsed > perSec) wait = 1000 - (now - bwWin); }
        if (wait > 0) try { Thread.sleep(Math.min(1000, wait)); } catch (InterruptedException ignored) { }
    }

    void monitor() {
        long now = System.currentTimeMillis(); boolean diskNow = now - diskTick > 60_000; if (diskNow) diskTick = now;
        for (Inst i : all.values()) {
            Process p = i.proc;
            if (p != null && p.isAlive()) {
                long pid = p.pid(); long rss = Sys.rssMb(pid);
                try { for (ProcessHandle c : p.descendants().toList()) { long r = Sys.rssMb(c.pid()); if (r > 0) rss = Math.max(0, rss) + r; } } catch (Exception ignored) { }
                i.rss = rss;
                synchronized (i.hist) { i.hist.addLast(new long[]{ now, Math.round(i.cpu * 10), rss, i.diskBytes < 0 ? -1 : i.diskBytes / 1048576 }); while (i.hist.size() > 120) i.hist.removeFirst(); }
                try {
                    long ns = p.toHandle().info().totalCpuDuration().map(Duration::toNanos).orElse(0L);
                    for (ProcessHandle c : p.descendants().toList()) ns += c.info().totalCpuDuration().map(Duration::toNanos).orElse(0L);
                    if (i.cpuLastNs > 0) { double dt = (now - i.cpuLastAt) * 1e6; if (dt > 0) i.cpu = Math.max(0, Math.min(100.0 * i.cores, (ns - i.cpuLastNs) / dt * 100.0)); }
                    i.cpuLastNs = ns; i.cpuLastAt = now;
                } catch (Exception ignored) { }
                // 2.1 VIGIA DE RAM: 99% = aviso (1% de folga para o GC); passou do limite por 20 s (ou 15% acima) = parada forçada
                if (rss > 0 && !i.unlimited) {
                    long lim = i.ram_mb;
                    if (rss >= lim * 99 / 100 && now - i.warnAt > 60_000) { i.warnAt = now; line(i, "[MinesyncHost] RAM " + rss + " / " + lim + " MB (99%) — no limite: o GC está segurando"); if (mc(i.type) && i.stdin != null) try { command(i, "save-all"); } catch (Exception ignored) { } }
                    if (rss > lim) { if (i.overSince == 0) i.overSince = now; } else i.overSince = 0;
                    if (rss > lim * 115 / 100 || (i.overSince > 0 && now - i.overSince > 20_000)) { line(i, "[MinesyncHost] RAM " + rss + " MB passou do limite (" + lim + " MB) — vigia parou o servidor"); i.note = "ram_limit"; i.overSince = 0; kill(i); }
                }
                // 2.1 VIGIA DE RESPOSTA (Minecraft): ligado há mais de 3 min e sem responder ao ping por 90 s → derruba (e reinicia se configurado)
                if (mc(i.type) && "running".equals(i.state) && i.startedAt > 0 && now - i.startedAt > 180_000 && now - i.lastPingOk > 30_000) {
                    final Inst fi = i; H.pool.execute(() -> watchPing(fi));
                }
            }
            if (diskNow) {
                i.diskBytes = Sys.dirSize(dir(i).toPath());
                if (i.diskBytes > i.disk_gb * 1073741824L && p != null && p.isAlive()) { line(i, "[MinesyncHost] disk " + (i.diskBytes / 1048576) + " MB over the limit (" + i.disk_gb + " GB) — stopping"); i.note = "disk_full"; stop(i); }
            }
        }
    }
    /** pergunta ao servidor (ping de lista) se ainda está respondendo */
    private void watchPing(Inst i) {
        long now = System.currentTimeMillis();
        boolean ok; try { ok = StatusPing.ping("127.0.0.1", (int) i.port, 5000) != null; } catch (Throwable t) { ok = false; }
        if (ok) { i.lastPingOk = now; i.pingFail = 0; return; }
        if (i.pingFail == 0) i.pingFail = now;
        if (now - i.pingFail > 90_000 && i.proc != null && i.proc.isAlive()) { line(i, "[MinesyncHost] o servidor não responde há 90 s — vigia derrubou"); i.note = "watchdog"; i.pingFail = 0; kill(i); }
    }
    /** 2.1: histórico curto para os gráficos [tempo, cpu×10, ram MB, disco MB] */
    List<long[]> history(Inst i) { synchronized (i.hist) { return new ArrayList<>(i.hist); } }
    Map<String, Object> view(Inst i) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", i.id); m.put("name", i.name); m.put("owner", i.owner); m.put("type", i.type); m.put("version", i.version);
        // 2.1.2: progresso (instalação/download) e a tarefa em andamento (backup, restauração, compactar, extrair) — para o painel de atividades
        String pw = i.progWhat;
        if (pw != null || "installing".equals(i.state)) { Map<String, Object> p = new LinkedHashMap<>(); p.put("what", pw == null ? (i.note == null || i.note.isEmpty() ? "install" : i.note) : pw); p.put("done", i.progDone); p.put("total", i.progTotal);
            Archive.Progress q = i.progPr; double pc = -1;
            if (q != null) pc = q.inTotal > 0 ? q.in * 100.0 / q.inTotal : q.total > 0 ? q.bytes * 100.0 / q.total : -1; else if (pw != null && i.progTotal > 0) pc = i.progDone * 100.0 / i.progTotal;
            if (q != null) { p.put("files", q.files); p.put("done", q.bytes); }
            p.put("pct", pc < 0 ? -1 : Math.min(99.0, Math.round(pc * 10) / 10.0)); m.put("progress", p); }
        Map<String, Object> jv = H.backups.jobView(i); if (jv != null) m.put("job", jv);
        m.put("ram_mb", i.ram_mb); m.put("cores", i.cores); m.put("disk_gb", i.disk_gb); m.put("port", i.port); m.put("created", i.created); m.put("autostart", i.autostart); m.put("minesync", i.minesync);
        m.put("state", i.state); m.put("note", i.note); m.put("rss_mb", i.rss); m.put("cpu", Math.round(i.cpu * 10) / 10.0); m.put("disk_mb", i.diskBytes < 0 ? -1 : i.diskBytes / 1048576); m.put("uptime_s", i.proc != null && i.proc.isAlive() ? (System.currentTimeMillis() - i.startedAt) / 1000 : 0);
        m.put("address", "external".equals(i.type) ? i.address : H.conf.bool("public_ip.enabled", false) && !H.conf.str("public_ip.address", "").isEmpty() ? H.conf.str("public_ip.address", "") + ":" + i.port : "127.0.0.1:" + i.port);
        Bridge br = bridges.get(i.id);
        Map<String, Object> bv = br != null ? br.view() : new LinkedHashMap<>();
        if (br == null) { bv.put("state", hasMineSync(i) ? "minesync" : Boolean.FALSE.equals(i.bridge) || !H.conf.bool("network.bridge", true) ? "disabled" : "off"); bv.put("name", i.bridge_name); }
        m.put("bridge_on", !Boolean.FALSE.equals(i.bridge)); m.put("bridge", bv);
        m.put("main", mainOf(i)); m.put("custom", custom(i.type)); m.put("restart_on_crash", i.restart_on_crash); m.put("subusers", i.subusers == null ? 0 : i.subusers.size()); m.put("central", i.central); m.put("free", i.free); m.put("unlimited", i.unlimited); m.put("suspended", i.suspended); m.put("java_ver", i.java_ver == null ? "" : i.java_ver); m.put("node_ver", i.node_ver == null ? "" : i.node_ver); m.put("py_ver", i.py_ver == null ? "" : i.py_ver);
        m.put("backup_every_h", i.backup_every_h); m.put("backup_keep", i.backup_keep <= 0 ? 3 : i.backup_keep); m.put("restoring", i.restoring);
        Map<String, Object> j = i.job; m.put("job", j == null ? null : H.backups.jobView(i));
        return m;
    }
    // ------------------------------------------------------------------ 2.0 servidores da central
    /** o que o nó empresta: total, reserva do sistema, reserva do dono, a parte da central e o máximo por instância */
    /** 2.1: o nó vem do Node (configurado pela central) */
    Map<String, Object> nodeInfo() { return H.node.info(); }
    private static Map<String, Object> res(long r, long c, long d) { Map<String, Object> m = new LinkedHashMap<>(); m.put("ram_mb", r); m.put("cores", c); m.put("disk_gb", d); return m; }
    /** no painel local o servidor da central aparece sem nome/endereço (só recursos e estado) */
    void anonymize(Map<String, Object> v) { v.put("name", "Servidor da central"); v.put("address", "-"); v.put("bridge", Map.of("state", "disabled")); v.put("owner", "central"); v.put("central", true); }
    /** a central manda criar (a central já conferiu dono/pagamento; aqui confere a capacidade do nó) */
    synchronized Inst createCentral(String id, JsonObject s) {
        if (!id.matches("[A-Za-z0-9_-]{3,40}")) throw new Api.Err("bad_id");
        if (all.containsKey(id)) return all.get(id);
        String type = Json.s(s, "type", "paper").toLowerCase(Locale.ROOT); if (!List.of(TYPES).contains(type) || "external".equals(type)) throw new Api.Err("bad_type");
        long ram = Json.n(s, "ram_mb", 2048), cores = Json.n(s, "cores", 1), disk = Json.n(s, "disk_gb", 10);
        boolean free = Json.b(s, "free", false), unl = Json.b(s, "unlimited", false);
        @SuppressWarnings("unchecked") Map<String, Object> nd = nodeInfo();
        @SuppressWarnings("unchecked") Map<String, Object> lim = (Map<String, Object>) (free ? (unl ? null : nd.get("owner")) : nd.get("central"));
        if (lim != null) {
            long ur = 0, uc = 0, ud = 0; for (Inst i : all.values()) if (i.central && i.free == free) { ur += i.ram_mb; uc += i.cores; ud += i.disk_gb; }
            if (ur + ram > ((Number) lim.get("ram_mb")).longValue() || uc + cores > ((Number) lim.get("cores")).longValue() || ud + disk > ((Number) lim.get("disk_gb")).longValue()) throw new Api.Err("not_enough_resources");
        }
        Inst i = new Inst(); fix(i);
        i.id = id; i.central = true; i.free = free; i.unlimited = unl; i.owner = Json.s(s, "owner_key", "central");
        i.name = Json.s(s, "name", "Servidor").replaceAll("[<>\"'\\\\]", "").trim(); if (i.name.isEmpty()) i.name = "Servidor";
        i.type = type; i.version = custom(type) ? "-" : Json.s(s, "version", "1.21.4"); i.ram_mb = Math.max(128, ram); i.cores = Math.max(1, cores); i.disk_gb = Math.max(1, disk);
        i.port = nextPort(); i.created = System.currentTimeMillis(); i.eula = true; i.autostart = false; i.minesync = bukkit(type) && Json.b(s, "minesync", true); i.bridge = true;
        if (custom(type)) { i.aikar = false; i.stop_cmd = ""; i.ready = ""; String mn = Json.s(s, "main", ""); if (!mn.isEmpty()) i.main = checkMain(mn); }
        dir(i).mkdirs(); all.put(i.id, i); save();
        if (Json.b(s, "no_install", false) || custom(type)) { i.state = "stopped"; if (custom(type)) seed(i); }
        else install(i);
        line(i, "[MinesyncHost] servidor da central criado (" + type + " · " + i.ram_mb + " MB · " + i.cores + " núcleos · " + i.disk_gb + " GB)");
        return i;
    }
    /** Node/Python: um exemplo que já sobe */
    private void seed(Inst i) {
        try {
            if ("nodejs".equals(i.type) && !new File(dir(i), mainOf(i)).exists()) Files.writeString(new File(dir(i), mainOf(i)).toPath(), "const http = require('http');\nconst port = Number(process.env.SERVER_PORT || 3000);\nhttp.createServer((q, r) => r.end('Olá do MinesyncHost!')).listen(port, process.env.SERVER_IP || '127.0.0.1', () => console.log('Rodando na porta ' + port));\n");
            if ("python".equals(i.type) && !new File(dir(i), mainOf(i)).exists()) Files.writeString(new File(dir(i), mainOf(i)).toPath(), "import os, http.server, socketserver\nport = int(os.environ.get('SERVER_PORT', '8000'))\nclass H(http.server.SimpleHTTPRequestHandler):\n    def do_GET(self):\n        self.send_response(200); self.end_headers(); self.wfile.write('Olá do MinesyncHost!'.encode())\nwith socketserver.TCPServer((os.environ.get('SERVER_IP', '127.0.0.1'), port), H) as s:\n    print('Rodando na porta', port, flush=True); s.serve_forever()\n");
        } catch (Exception ignored) { }
    }
    /** versões oferecidas no painel (das fontes oficiais, guardadas por 30 min) */
    private final Map<String, Object[]> verCache = new ConcurrentHashMap<>();
    List<String> versions(String type) {
        Object[] c = verCache.get(type); if (c != null && System.currentTimeMillis() - (long) c[0] < 30 * 60_000L) { @SuppressWarnings("unchecked") List<String> l = (List<String>) c[1]; return l; }
        List<String> out = new ArrayList<>();
        try {
            switch (type) {
                case "paper": case "folia": {
                    JsonArray v = getJson("https://api.papermc.io/v2/projects/" + type).getAsJsonObject().getAsJsonArray("versions");
                    for (int k = v.size() - 1; k >= 0 && out.size() < 40; k--) out.add(v.get(k).getAsString()); break; }
                case "purpur": { JsonArray v = getJson("https://api.purpurmc.org/v2/purpur").getAsJsonObject().getAsJsonArray("versions"); for (int k = v.size() - 1; k >= 0 && out.size() < 40; k--) out.add(v.get(k).getAsString()); break; }
                case "fabric": { for (JsonElement e : getJson("https://meta.fabricmc.net/v2/versions/game").getAsJsonArray()) { JsonObject o = e.getAsJsonObject(); if (Json.b(o, "stable", false) && out.size() < 40) out.add(Json.s(o, "version", "")); } break; }
                default: { for (JsonElement e : getJson("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json").getAsJsonObject().getAsJsonArray("versions")) { JsonObject o = e.getAsJsonObject(); if ("release".equals(Json.s(o, "type", "")) && out.size() < 40) out.add(Json.s(o, "id", "")); } }
            }
        } catch (Exception e) { MinesyncHost.log("versions " + type + ": " + e.getMessage()); }
        if (!out.isEmpty()) verCache.put(type, new Object[]{ System.currentTimeMillis(), out });
        return out;
    }
}
