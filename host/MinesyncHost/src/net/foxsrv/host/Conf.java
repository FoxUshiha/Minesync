package net.foxsrv.host;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** config.yml do MinesyncHost: padrões + o que a pessoa mudou; o painel muda e grava pelo mesmo caminho */
final class Conf {
    final File file;
    private Map<String, Object> root = new LinkedHashMap<>();
    private static final Map<String, String> COMMENTS = new LinkedHashMap<>();

    Conf(File f) { this.file = f; }

    private static Map<String, Object> m() { return new LinkedHashMap<>(); }
    static Map<String, Object> defaults() {
        long ramMb = Sys.totalRamMb(); int cores = Runtime.getRuntime().availableProcessors();
        Map<String, Object> d = m();
        d.put("enabled", true);   // 2.0: contribuir com a rede vem ligado (desliga no painel)
        d.put("language", "auto");
        Map<String, Object> panel = m(); panel.put("host", "127.0.0.1"); panel.put("port", 8765L); d.put("panel", panel);
        d.put("central", "https://www.foxsrv.net/");
        d.put("isolated", true);
        Map<String, Object> lim = m();
        lim.put("max_ram_mb", Math.max(1024L, ramMb)); lim.put("max_cores", (long) Math.max(1, cores)); lim.put("max_disk_gb", 100L);
        lim.put("bandwidth_mbps", 0L); lim.put("kill_over_ram_percent", 160L);
        d.put("limits", lim);
        Map<String, Object> net = m();
        net.put("share_with_network", true); net.put("agent", true); net.put("bandwidth_mbps", 100L); net.put("name", "MinesyncHost"); net.put("install_minesync", true); net.put("bridge", true); net.put("bridge_lan", false); net.put("bridge_max_streams", 256L);
        net.put("kinds", new ArrayList<>(List.of("java")));
        Map<String, Object> pr = m(); pr.put("free", true); pr.put("per_core", 0.0); pr.put("per_gb_ram", 0.0); pr.put("per_gb_disk", 0.0); pr.put("currencies", new ArrayList<>()); pr.put("bot_credits", false); net.put("prices", pr);
        Map<String, Object> tr = m(); tr.put("on", false); tr.put("ram_mb", 1024L); tr.put("cores", 1L); tr.put("days", 3L); net.put("trial", tr);
        d.put("network", net);
        // 2.0: o NÓ — total (limits), reserva do sistema, reserva do dono (o servidor grátis dele), o resto é da central, e o máximo por servidor de cliente
        Map<String, Object> nd = m();
        Map<String, Object> os = m(); os.put("ram_mb", 2048L); os.put("cores", 1L); os.put("disk_gb", 20L); nd.put("os_reserve", os);
        Map<String, Object> ow = m(); ow.put("ram_mb", 0L); ow.put("cores", 0L); ow.put("disk_gb", 0L); nd.put("owner_reserve", ow);
        Map<String, Object> im = m(); im.put("ram_mb", 8192L); im.put("cores", 4L); im.put("disk_gb", 50L); nd.put("instance_max", im);
        d.put("node", nd);
        // 1.2: backups (limites POR USUÁRIO, somando os servidores dele; dá para mudar por usuário no painel)
        Map<String, Object> bk = m(); bk.put("enabled", true); bk.put("max_count", 5L); bk.put("max_mb", 10240L); bk.put("max_total_gb", 0L); bk.put("min_every_h", 1L); d.put("backups", bk);
        Map<String, Object> ar = m(); ar.put("max_extract_mb", 4096L); ar.put("max_entries", 200000L); d.put("archive", ar);
        // 1.3: contas da host (padrão das contas novas; cada conta pode ter os seus no painel) e runtimes
        Map<String, Object> ac = m(); ac.put("signup", false); ac.put("signup_code", ""); ac.put("network_login", true); ac.put("network_signup", false);
        Map<String, Object> ad = m(); ad.put("perms", new ArrayList<>(List.of("create", "delete", "startup", "schedules", "subusers"))); ad.put("types", new ArrayList<>()); ad.put("max_servers", 1L); ad.put("max_ram_mb", 2048L); ad.put("max_cores", 1L); ad.put("max_disk_gb", 10L); ac.put("default", ad);
        d.put("accounts", ac);
        Map<String, Object> rt = m(); rt.put("java", new ArrayList<>()); rt.put("node", ""); rt.put("python", ""); d.put("runtimes", rt);
        Map<String, Object> pub = m(); pub.put("enabled", false); pub.put("address", ""); pub.put("ports", "25565-25600"); d.put("public_ip", pub);
        Map<String, Object> cf = m(); cf.put("enabled", false); cf.put("mode", "quick"); cf.put("token", ""); cf.put("hostname", ""); cf.put("zone", ""); cf.put("tunnel_id", ""); d.put("cloudflare", cf);
        return d;
    }
    static {
        COMMENTS.put("enabled", "MinesyncHost 2.0 - host da rede MineSync (painel local: http://localhost:8765)\ntrue = contribuindo com a rede (os servidores são controlados pela central, no site) · false = desligada");
        COMMENTS.put("language", "auto | pt | en");
        COMMENTS.put("panel", "painel no navegador: 127.0.0.1 = só neste PC (pela internet: aba Cloudflare)");
        COMMENTS.put("central", "a central da rede (pode trocar e voltar para a padrão pelo painel)");
        COMMENTS.put("isolated", "isolamento forte dos servidores (Linux: bubblewrap; todos: só 127.0.0.1, sem acesso a outras pastas)");
        COMMENTS.put("limits", "o máximo que esta host empresta para os servidores (somando todos)");
        COMMENTS.put("network", "rede MineSync: aparecer na lista de hosts, preços, teste grátis");
        COMMENTS.put("network.bridge", "ponte da rede pela host: servidores SEM o plugin MineSync (Vanilla, Fabric, Forge, externos) entram na rede sem IP fixo\nbridge_lan: também um caminho direto pela rede local (abre uma porta 4xxxx só para IPs da rede local)");
        COMMENTS.put("network.prices", "free: true = de graça · moedas aceitas: COIN, BIPO, USD, BTC, BRL (Pix)");
        COMMENTS.put("backups", "backups dos servidores (pasta ./backups)\nmax_count / max_mb: padrão POR USUÁRIO (somando os servidores dele; muda por usuário na aba Usuários)\nmax_total_gb: teto de todos os backups da host (0 = sem teto) · min_every_h: menor intervalo do backup automático");
        COMMENTS.put("archive", "explorador de arquivos: descompactar no máximo max_extract_mb e max_entries arquivos por vez (proteção contra \"zip bomb\")");
        COMMENTS.put("accounts", "contas da host: signup = qualquer um cria conta pela tela de entrada (signup_code = código de convite, vazio = sem código)\nnetwork_login = entrar com a conta da rede (pelo site ou usuário/senha da central) · network_signup = cria a conta da host na 1ª entrada pela rede\ndefault = permissões/limites das contas que não têm os seus (perms: create, delete, custom, startup, schedules, subusers · types: vazio = todos)");
        COMMENTS.put("runtimes", "java: Javas extras para escolher por servidor, ex.: [\"17=/usr/lib/jvm/java-17-openjdk/bin/java\"] · node / python: caminho (vazio = o do PATH)");
        COMMENTS.put("node", "2.0 o NÓ desta host: limits = o total que ela empresta · os_reserve = fica para o sistema · owner_reserve = para o servidor grátis do dono\n(o resto é a parte da central, vendida no site) · instance_max = o máximo de cada servidor de cliente");
        COMMENTS.put("public_ip", "só para quem tem IP fixo: libera endereço/DNS e portas para os servidores (senão: rede sem IP fixo)");
        COMMENTS.put("cloudflare", "abrir o painel pela internet: quick (endereço trycloudflare.com que muda) ou token (túnel permanente com o seu domínio)");
    }

    synchronized void load() {
        Map<String, Object> d = defaults();
        try { if (file.isFile()) merge(d, Yaml.parse(Files.readString(file.toPath(), StandardCharsets.UTF_8))); } catch (Exception e) { MinesyncHost.log("config.yml: " + e.getMessage() + " (usando os padrões)"); }
        root = d;
        save();
    }
    @SuppressWarnings("unchecked")
    private static void merge(Map<String, Object> into, Map<String, Object> from) {
        for (Map.Entry<String, Object> e : from.entrySet()) {
            Object cur = into.get(e.getKey());
            if (cur instanceof Map && e.getValue() instanceof Map) merge((Map<String, Object>) cur, (Map<String, Object>) e.getValue());
            else if (cur == null || !(cur instanceof Map)) into.put(e.getKey(), e.getValue());
        }
    }
    synchronized void save() {
        try {
            File tmp = new File(file.getPath() + ".tmp");
            Files.writeString(tmp.toPath(), Yaml.dump(root, COMMENTS), StandardCharsets.UTF_8);
            Files.move(tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) { MinesyncHost.log("config.yml: " + e.getMessage()); }
    }
    @SuppressWarnings("unchecked")
    synchronized Object get(String path) {
        Object cur = root;
        for (String k : path.split("\\.")) { if (!(cur instanceof Map)) return null; cur = ((Map<String, Object>) cur).get(k); }
        return cur;
    }
    @SuppressWarnings("unchecked")
    synchronized void set(String path, Object v) {
        String[] p = path.split("\\."); Map<String, Object> cur = root;
        for (int i = 0; i < p.length - 1; i++) { Object n = cur.get(p[i]); if (!(n instanceof Map)) { n = new LinkedHashMap<String, Object>(); cur.put(p[i], n); } cur = (Map<String, Object>) n; }
        cur.put(p[p.length - 1], v);
    }
    String str(String p, String d) { Object v = get(p); return v == null ? d : String.valueOf(v); }
    long num(String p, long d) { Object v = get(p); if (v instanceof Number) return ((Number) v).longValue(); try { return Long.parseLong(String.valueOf(v).trim()); } catch (Exception e) { return d; } }
    double dbl(String p, double d) { Object v = get(p); if (v instanceof Number) return ((Number) v).doubleValue(); try { return Double.parseDouble(String.valueOf(v).trim()); } catch (Exception e) { return d; } }
    boolean bool(String p, boolean d) { Object v = get(p); return v instanceof Boolean ? (Boolean) v : v == null ? d : "true".equalsIgnoreCase(String.valueOf(v)); }
    @SuppressWarnings("unchecked") List<Object> list(String p) { Object v = get(p); return v instanceof List ? (List<Object>) v : new ArrayList<>(); }
    synchronized Map<String, Object> snapshot() { return Json.deepCopy(root); }
    String central() { String c = str("central", "https://www.foxsrv.net/").trim(); if (!c.matches("^https?://[A-Za-z0-9.:-]+(/.*)?$")) c = "https://www.foxsrv.net/"; while (c.endsWith("/")) c = c.substring(0, c.length() - 1); return c; }
}
