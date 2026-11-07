package net.foxsrv.host;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 1.1 — PONTE DA REDE (FoxBridge) feita pela HOST: o servidor entra na lista global e qualquer um entra pelo "Jogar" do
 * launcher MineSync, sem IP fixo e sem abrir portas — também Vanilla, Fabric, Forge e servidores externos (Horizon,
 * outro programa…), que não aceitam o plugin MineSync. Quando o servidor tem o MineSync, quem faz a ponte é o plugin
 * (a host não faz de novo, nada de servidor duplicado na rede).
 * <ol>
 *  <li>Cadastro: o servidor ganha um nome na rede (o nome dele, ou MineServ### se já existir) e uma senha aleatória;</li>
 *  <li>Sessão: handshake + heartbeat na central com MOTD, versão e jogadores lidos pelo ping de status do Minecraft;</li>
 *  <li>Ponte: uma conexão de SAÍDA (wss) para a central; cada jogador que entra pelo relay vira um "stream" ligado a
 *      127.0.0.1:&lt;porta&gt; (o mesmo protocolo do MineSync: [tipo u8][stream u32][dados], crédito por stream + ACK).
 *      Opcional (network.bridge_lan): caminho direto pela rede local, numa porta própria que encaminha para o servidor.</li>
 * </ol>
 */
final class Bridge {
    static final byte T_JSON = 0, T_OPEN = 1, T_DATA = 2, T_CLOSE = 3, T_ACK = 4, T_OPENED = 5;
    private final MinesyncHost H; final Instances.Inst inst;
    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(10)).build();
    private volatile WebSocket ws; private volatile boolean connecting, stopped;
    private volatile long backoff = 3000, nextTry, sessionUntil, lastBeat, lastInfoAt, lastPing;
    volatile String session, state = "off", error = "";
    private volatile int window = 262144;
    private final Map<Integer, Stream> streams = new ConcurrentHashMap<>();
    private final LinkedBlockingQueue<ByteBuffer> txq = new LinkedBlockingQueue<>(8192);
    private final ByteArrayOutputStream acc = new ByteArrayOutputStream();
    private Thread txThread; private volatile String lastInfo = "";
    volatile StatusPing.Result status = new StatusPing.Result();
    private volatile ServerSocket lan; private volatile int lanPort;
    volatile long bytesIn, bytesOut, opened;
    private final long started = System.currentTimeMillis();

    Bridge(MinesyncHost h, Instances.Inst i) { this.H = h; this.inst = i; }

    String targetHost() { if ("external".equals(inst.type)) { String a = inst.address == null ? "" : inst.address; int c = a.lastIndexOf(':'); return c > 0 ? a.substring(0, c) : (a.isEmpty() ? "127.0.0.1" : a); } return "127.0.0.1"; }
    int targetPort() { if ("external".equals(inst.type)) { String a = inst.address == null ? "" : inst.address; int c = a.lastIndexOf(':'); try { return c > 0 ? Integer.parseInt(a.substring(c + 1)) : 25565; } catch (Exception e) { return 25565; } } return (int) inst.port; }

    void start() {
        stopped = false; state = "starting";
        txThread = new Thread(this::txLoop, "msh-bridge-tx-" + inst.id); txThread.setDaemon(true); txThread.start();
        if (H.conf.bool("network.bridge_lan", false)) openLan();
    }
    void stop() {
        stopped = true; state = "off";
        for (Stream s : streams.values()) s.close(); streams.clear();
        try { WebSocket w = ws; ws = null; if (w != null) w.sendClose(WebSocket.NORMAL_CLOSURE, "bye"); } catch (Throwable ignored) { }
        try { if (txThread != null) txThread.interrupt(); } catch (Throwable ignored) { }
        try { ServerSocket l = lan; lan = null; if (l != null) l.close(); } catch (Throwable ignored) { }
    }
    boolean connected() { return ws != null; }

    private final java.util.concurrent.atomic.AtomicBoolean busy = new java.util.concurrent.atomic.AtomicBoolean();
    /** chamado a cada 5 s: roda numa tarefa própria (uma central lenta não segura as outras pontes nem a vigia) */
    void schedule() { if (!stopped && busy.compareAndSet(false, true)) H.pool.execute(() -> { try { tick(); } finally { busy.set(false); } }); }
    /** sessão, heartbeat, conexão e o "info" para o launcher */
    void tick() {
        if (stopped) return;
        long now = System.currentTimeMillis();
        try {
            if (now - lastPing > 15000) { lastPing = now; StatusPing.Result r = StatusPing.ping(targetHost(), targetPort(), 3000); status = r; }
            if (session == null || now > sessionUntil - 20000) { if (!link()) return; }
            if (now - lastBeat > 30000) beat();
            if (ws == null && !connecting && now >= nextTry) connect();
            if (ws != null && now - lastInfoAt > 20000) sendInfo(false);
        } catch (Throwable t) { error = String.valueOf(t.getMessage()); }
    }

    // ------------------------------------------------------------------ central: cadastro + sessão
    private String api(String p) { return H.conf.central() + "/minecraft" + p; }
    private JsonObject post(String path, Map<String, Object> body, Map<String, String> headers) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(api(path))).timeout(Duration.ofSeconds(20)).header("Content-Type", "application/json").header("User-Agent", "MinesyncHost/" + MinesyncHost.VERSION);
        if (headers != null) headers.forEach(b::header);
        HttpResponse<String> r = http.send(b.POST(HttpRequest.BodyPublishers.ofString(Json.str(body))).build(), HttpResponse.BodyHandlers.ofString());
        JsonObject j = Json.obj(r.body()); if (!j.has("ok") && r.statusCode() != 200) j.addProperty("error", "HTTP " + r.statusCode());
        return j;
    }
    static String sha256(String s) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
    private static String netName(String name, int attempt) {
        String base = name == null ? "" : name.replaceAll("[^A-Za-z0-9_-]", "").replaceAll("^[-_]+", "");
        if (attempt == 0 && base.length() >= 3) return base.length() > 24 ? base.substring(0, 24) : base;
        Random r = new Random();
        return attempt < 8 ? String.format("MineServ%03d", 1 + r.nextInt(999)) : String.format("MineServ%04d", 1000 + r.nextInt(9000));
    }
    private synchronized boolean register() throws Exception {
        if (inst.bridge_name != null && inst.bridge_secret != null) return true;
        String secret = Users.rnd(24); String last = "";
        for (int a = 0; a < 12; a++) {
            String nm = netName(inst.name, a);
            JsonObject r = post("/register", Map.of("name", nm, "password", secret, "registeredBy", "minesynchost"), null);
            if (Json.b(r, "ok", false)) {
                inst.bridge_name = nm; inst.bridge_secret = secret;
                String id = Json.s(r, "serverId", null); if (id == null && r.has("server") && r.get("server").isJsonObject()) id = Json.s(r.getAsJsonObject("server"), "id", null);
                inst.bridge_id = id; H.inst.save();
                MinesyncHost.log(inst.name + ": cadastrado na rede como " + nm);
                return true;
            }
            last = Json.s(r, "error", "no_response");
            if (!"name_already_registered".equals(last) && !"name_reserved".equals(last) && !"invalid_name".equals(last)) break;
        }
        error = "register: " + last; state = "error"; return false;
    }
    private boolean link() throws Exception {
        if (!register()) return false;
        Map<String, Object> b = new java.util.LinkedHashMap<>();
        StatusPing.Result st = status;
        b.put("name", inst.bridge_name); b.put("players", st.players); b.put("maxPlayers", st.max > 0 ? st.max : 20);
        b.put("version", st.version.isEmpty() ? inst.type + " " + inst.version : st.version); b.put("pluginVersion", "MinesyncHost " + MinesyncHost.VERSION);
        b.put("motd", st.motd.isEmpty() ? inst.name : st.motd); b.put("uptimeSeconds", (System.currentTimeMillis() - started) / 1000);
        b.put("javaIp", "127.0.0.1"); b.put("javaPort", targetPort());
        Map<String, String> h = new java.util.LinkedHashMap<>(); h.put("X-Minecraft-Secret", sha256(inst.bridge_secret)); h.put("X-Minecraft-Server", inst.bridge_name);
        if (inst.bridge_id != null && !inst.bridge_id.isEmpty()) h.put("X-Minecraft-Server-Id", inst.bridge_id);
        JsonObject r = post("/handshake", b, h);
        if (!Json.b(r, "ok", false)) {
            error = "handshake: " + Json.s(r, "error", "no_response"); state = "error";
            if ("invalid_minecraft_secret".equals(Json.s(r, "error", ""))) { inst.bridge_name = null; inst.bridge_secret = null; inst.bridge_id = null; H.inst.save(); }   // apagado na central: cadastra de novo
            nextTry = System.currentTimeMillis() + 30000; return false;
        }
        session = Json.s(r, "sessionToken", null); sessionUntil = System.currentTimeMillis() + Json.n(r, "expiresInMs", 300000);
        String id = Json.s(r, "serverId", null); if (id != null && !id.equals(inst.bridge_id)) { inst.bridge_id = id; H.inst.save(); }
        lastBeat = System.currentTimeMillis(); error = "";
        if (ws == null) state = "linked";
        return session != null;
    }
    private void beat() {
        try {
            StatusPing.Result st = status;
            Map<String, Object> b = new java.util.LinkedHashMap<>(); b.put("players", st.players); b.put("uptimeSeconds", (System.currentTimeMillis() - started) / 1000);
            JsonObject r = post("/heartbeat", b, Map.of("X-Minecraft-Session", session));
            String e = Json.s(r, "error", "");
            if (List.of("session_expired", "invalid_session_token", "missing_session_token", "server_not_found").contains(e)) { session = null; return; }
            lastBeat = System.currentTimeMillis(); sessionUntil = Math.max(sessionUntil, System.currentTimeMillis() + 240000);
        } catch (Exception e) { error = "heartbeat: " + e.getMessage(); }
    }

    // ------------------------------------------------------------------ ponte (o mesmo protocolo do MineSync)
    private String wsUrl() { return H.conf.central().replaceFirst("^http", "ws") + "/botcraft/fbridge/agent"; }
    private void connect() {
        connecting = true;
        try {
            http.newWebSocketBuilder().header("X-Minecraft-Session", session).connectTimeout(Duration.ofSeconds(10))
                .buildAsync(URI.create(wsUrl()), new Listener())
                .whenComplete((w, e) -> {
                    connecting = false;
                    if (e != null || w == null) { backoff = Math.min(60000, backoff * 2); nextTry = System.currentTimeMillis() + backoff; error = "bridge: " + (e == null ? "?" : e.getMessage()); return; }
                    ws = w; backoff = 3000; lastInfo = ""; state = "on"; error = "";
                    MinesyncHost.log(inst.name + ": na rede sem IP fixo (" + inst.bridge_name + ")");
                    sendInfo(true);
                });
        } catch (Throwable t) { connecting = false; }
    }
    private void drop(String why) {
        WebSocket w = ws; ws = null; if (w != null) { try { w.abort(); } catch (Throwable ignored) { } }
        for (Stream s : streams.values()) s.close(); streams.clear();
        nextTry = System.currentTimeMillis() + 2000; if (!stopped) state = "linked";
        if (why != null) error = "bridge: " + why;
    }
    private final class Listener implements WebSocket.Listener {
        @Override public void onOpen(WebSocket w) { w.request(1); }
        @Override public CompletionStage<?> onText(WebSocket w, CharSequence d, boolean last) { w.request(1); return null; }
        @Override public CompletionStage<?> onBinary(WebSocket w, ByteBuffer data, boolean last) {
            try {
                byte[] b = new byte[data.remaining()]; data.get(b);
                if (!last) { synchronized (acc) { if (acc.size() + b.length > 4 << 20) acc.reset(); else acc.write(b, 0, b.length); } }
                else { byte[] all; synchronized (acc) { if (acc.size() > 0) { acc.write(b, 0, b.length); all = acc.toByteArray(); acc.reset(); } else all = b; } onFrame(all); }
            } catch (Throwable ignored) { }
            w.request(1); return null;
        }
        @Override public CompletionStage<?> onClose(WebSocket w, int code, String reason) { if (ws == w) drop("closed " + code); return null; }
        @Override public void onError(WebSocket w, Throwable error) { if (ws == w) drop(String.valueOf(error)); }
    }
    static ByteBuffer frame(byte t, int id, byte[] d, int off, int len) { ByteBuffer bb = ByteBuffer.allocate(5 + len); bb.put(t); bb.putInt(id); if (len > 0) bb.put(d, off, len); bb.flip(); return bb; }
    private void tx(ByteBuffer b) { if (ws == null) return; try { if (!txq.offer(b, 10, TimeUnit.SECONDS)) drop("queue full"); } catch (InterruptedException ignored) { } }
    private void txLoop() {
        while (!stopped) {
            try { ByteBuffer b = txq.poll(2, TimeUnit.SECONDS); if (b == null) continue; WebSocket w = ws; if (w == null) { txq.clear(); continue; } w.sendBinary(b, true).get(30, TimeUnit.SECONDS); }
            catch (InterruptedException ie) { return; }
            catch (Throwable t) { drop("send: " + t.getMessage()); }
        }
    }
    private void json(JsonObject o) { byte[] b = o.toString().getBytes(StandardCharsets.UTF_8); tx(frame(T_JSON, 0, b, 0, b.length)); }
    private void onFrame(byte[] f) {
        if (f.length < 5) return;
        byte t = f[0]; int id = ((f[1] & 255) << 24) | ((f[2] & 255) << 16) | ((f[3] & 255) << 8) | (f[4] & 255);
        switch (t) {
            case T_JSON: try { JsonObject m = JsonParser.parseString(new String(f, 5, f.length - 5, StandardCharsets.UTF_8)).getAsJsonObject(); if ("hello".equals(Json.s(m, "t", ""))) { if (m.has("janela")) window = Math.max(32768, Math.min(4 << 20, m.get("janela").getAsInt())); sendInfo(true); } } catch (Throwable ignored) { } return;
            case T_OPEN: open(id, new String(f, 5, f.length - 5, StandardCharsets.UTF_8)); return;
            case T_DATA: { Stream s = streams.get(id); if (s == null) { tx(frame(T_CLOSE, id, null, 0, 0)); return; } s.write(f, 5, f.length - 5); return; }
            case T_ACK: { Stream s = streams.get(id); if (s != null && f.length >= 9) s.credit(((f[5] & 255L) << 24) | ((f[6] & 255) << 16) | ((f[7] & 255) << 8) | (f[8] & 255)); return; }
            case T_CLOSE: { Stream s = streams.remove(id); if (s != null) s.close(); return; }
            default:
        }
    }
    /** o que o launcher precisa para escolher o caminho: relay sempre; rede local só com network.bridge_lan */
    void sendInfo(boolean force) {
        if (ws == null) return;
        try {
            lastInfoAt = System.currentTimeMillis();
            StatusPing.Result st = status;
            JsonObject o = new JsonObject(); o.addProperty("t", "info"); o.addProperty("relay", true);
            JsonArray l = new JsonArray(); ServerSocket ls = lan;
            if (ls != null && !ls.isClosed()) { for (String ip : lanIps()) l.add(ip); o.addProperty("port", lanPort); } else o.addProperty("port", targetPort());
            o.add("lan", l);
            o.addProperty("version", st.version); o.addProperty("plugin", "MinesyncHost " + MinesyncHost.VERSION); o.addProperty("motd", st.motd.isEmpty() ? inst.name : st.motd);
            o.addProperty("players", st.players); o.addProperty("max", st.max); o.addProperty("online_mode", onlineMode());
            String s = o.toString(); if (!force && s.equals(lastInfo)) return; lastInfo = s; json(o);
        } catch (Throwable ignored) { }
    }
    private boolean onlineMode() {
        if ("external".equals(inst.type)) return true;
        try { Properties p = new Properties(); File f = new File(H.inst.dir(inst), "server.properties"); if (f.isFile()) try (var in = Files.newBufferedReader(f.toPath(), StandardCharsets.ISO_8859_1)) { p.load(in); } return !"false".equalsIgnoreCase(p.getProperty("online-mode", "true").trim()); } catch (Exception e) { return true; }
    }
    static List<String> lanIps() {
        List<String> out = new ArrayList<>();
        try {
            for (NetworkInterface n : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!n.isUp() || n.isLoopback() || n.isVirtual()) continue;
                String nm = (n.getName() + " " + n.getDisplayName()).toLowerCase(Locale.ROOT);
                if (nm.contains("docker") || nm.contains("vmnet") || nm.contains("vbox") || nm.contains("hyper-v") || nm.contains("wsl")) continue;
                for (InetAddress a : Collections.list(n.getInetAddresses())) if (a instanceof Inet4Address && a.isSiteLocalAddress()) out.add(a.getHostAddress());
            }
        } catch (Throwable ignored) { }
        return out.size() > 6 ? out.subList(0, 6) : out;
    }

    // ------------------------------------------------------------------ jogadores
    private void open(int id, String meta) {
        int max = (int) Math.max(4, H.conf.num("network.bridge_max_streams", 256));
        if (streams.size() >= max || streams.containsKey(id)) { tx(frame(T_CLOSE, id, null, 0, 0)); return; }
        try {
            Socket so = new Socket(); so.setTcpNoDelay(true);
            so.connect(new InetSocketAddress(targetHost(), targetPort()), 4000);
            Stream s = new Stream(id, so); streams.put(id, s); opened++;
            tx(frame(T_OPENED, id, null, 0, 0));
            Thread th = new Thread(s::pump, "msh-bridge-" + id); th.setDaemon(true); th.start();
        } catch (Throwable e) { tx(frame(T_CLOSE, id, null, 0, 0)); }
    }
    private final class Stream {
        final int id; final Socket so; final OutputStream out; final Object lock = new Object(); long credit; volatile boolean closed;
        Stream(int id, Socket so) throws java.io.IOException { this.id = id; this.so = so; this.out = so.getOutputStream(); this.credit = window; }
        void pump() {
            byte[] buf = new byte[32768];
            try (InputStream in = so.getInputStream()) {
                for (;;) {
                    synchronized (lock) { while (credit <= 0 && !closed) lock.wait(15000); }
                    if (closed) break;
                    int n = in.read(buf); if (n < 0) break;
                    synchronized (lock) { credit -= n; }
                    bytesOut += n; H.inst.band(n);
                    tx(frame(T_DATA, id, buf, 0, n));
                }
            } catch (Throwable ignored) { }
            if (streams.remove(id) != null) tx(frame(T_CLOSE, id, null, 0, 0));
            close();
        }
        void write(byte[] f, int off, int len) {
            if (closed) return;
            try { out.write(f, off, len); out.flush(); bytesIn += len; ByteBuffer a = ByteBuffer.allocate(4); a.putInt(len); tx(frame(T_ACK, id, a.array(), 0, 4)); }
            catch (Throwable e) { if (streams.remove(id) != null) tx(frame(T_CLOSE, id, null, 0, 0)); close(); }
        }
        void credit(long n) { synchronized (lock) { credit += n; lock.notifyAll(); } }
        void close() { if (closed) return; closed = true; synchronized (lock) { lock.notifyAll(); } try { so.close(); } catch (Throwable ignored) { } }
    }

    // ------------------------------------------------------------------ rede local (opcional): porta própria → servidor
    private void openLan() {
        try {
            int p = targetPort() + 10000; if (p > 65535) p = targetPort() - 10000;
            ServerSocket ss = new ServerSocket(); ss.setReuseAddress(true); ss.bind(new InetSocketAddress("0.0.0.0", p)); lan = ss; lanPort = p;
            Thread t = new Thread(() -> {
                while (!ss.isClosed()) {
                    try {
                        Socket c = ss.accept();
                        InetAddress ra = c.getInetAddress();
                        if (!(ra.isSiteLocalAddress() || ra.isLoopbackAddress() || ra.isLinkLocalAddress())) { c.close(); continue; }   // só a rede local
                        Socket up = new Socket(); up.setTcpNoDelay(true); c.setTcpNoDelay(true);
                        try { up.connect(new InetSocketAddress(targetHost(), targetPort()), 4000); } catch (Exception e) { c.close(); continue; }
                        pipe(c, up); pipe(up, c);
                    } catch (Throwable ignored) { }
                }
            }, "msh-lan-" + inst.id);
            t.setDaemon(true); t.start();
        } catch (Exception e) { error = "lan: " + e.getMessage(); lan = null; }
    }
    private void pipe(Socket a, Socket b) {
        Thread t = new Thread(() -> { byte[] buf = new byte[32768]; try (InputStream in = a.getInputStream(); OutputStream out = b.getOutputStream()) { int n; while ((n = in.read(buf)) > 0) { out.write(buf, 0, n); H.inst.band(n); } } catch (Throwable ignored) { } try { a.close(); } catch (Throwable ignored) { } try { b.close(); } catch (Throwable ignored) { } }, "msh-lan-pipe");
        t.setDaemon(true); t.start();
    }

    Map<String, Object> view() {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("state", state); m.put("error", error); m.put("name", inst.bridge_name); m.put("streams", streams.size()); m.put("mb", (bytesIn + bytesOut) / 1048576);
        m.put("lan_port", lan != null ? lanPort : 0); m.put("online", status.online); m.put("players", status.players); m.put("max", status.max); m.put("version", status.version);
        return m;
    }
}
