package net.foxsrv.host;

import java.io.File;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * MinesyncHost — uma "hospedagem" leve num .jar só. Na pasta onde ele está:
 * <pre>
 *   ./MinesyncHost.jar
 *   ./config.yml        (criado na 1ª vez · enabled: false = hospedagem desligada até ligar no painel)
 *   ./cloudflared/      (o cloudflared baixado sozinho quando a aba Cloudflare for ligada)
 *   ./servers/          (uma pasta por servidor · servers/.msh/ = usuários, servidores e a identidade da host)
 *   ./backups/          (1.2: backups dos servidores, fora da pasta deles · backups.json = índice)
 * </pre>
 * Painel: http://localhost:8765 (português/inglês, tema claro/escuro). java -jar MinesyncHost.jar [--port N] [--dir PASTA]
 */
public final class MinesyncHost {
    static final String VERSION = "2.1.2";
    final File base, servers, state;
    final Conf conf; final Users users; final Instances inst; final Backups backups; final Accounts accounts; final Central central; final Schedules schedules; final Activity activity; final Agent agent; final Integrity integrity; final Runtimes runtimes; final Cloud cloud; final Net net; final Panel panel; final Node node; final Bench bench;
    final ExecutorService pool = Executors.newCachedThreadPool(r -> { Thread t = new Thread(r, "msh-work"); t.setDaemon(true); return t; });
    final ScheduledExecutorService timer = Executors.newScheduledThreadPool(2, r -> { Thread t = new Thread(r, "msh-timer"); t.setDaemon(true); return t; });
    private static final DateTimeFormatter TF = DateTimeFormatter.ofPattern("HH:mm:ss");

    static void log(String m) { System.out.println("[" + LocalTime.now().format(TF) + "] [MinesyncHost] " + m); }

    MinesyncHost(File base, Integer port) {
        this.base = base; this.servers = new File(base, "servers"); this.state = new File(servers, ".msh");
        servers.mkdirs(); state.mkdirs(); new File(base, "cloudflared").mkdirs();
        conf = new Conf(new File(base, "config.yml")); conf.load();
        if (port != null) { conf.set("panel.port", (long) port); conf.save(); }
        users = new Users(new File(state, "users.json"));
        inst = new Instances(this, servers, new File(state, "instances.json"));
        backups = new Backups(this, new File(base, "backups"));   // 1.2: fora da pasta dos servidores
        accounts = new Accounts(this); central = new Central(this); schedules = new Schedules(this); activity = new Activity(new File(state, "activity.json"));   // 1.3
        runtimes = new Runtimes(this, new File(base, "runtimes")); agent = new Agent(this); integrity = new Integrity(this, new File(state, "integrity"));   // 2.0
        cloud = new Cloud(this, new File(base, "cloudflared"));
        net = new Net(this, new File(state, "host.json"));
        node = new Node(this); bench = new Bench(this, new File(state, "bench.json"));   // 2.1
        panel = new Panel(this);
    }

    void start() throws Exception {
        log("MinesyncHost " + VERSION + " · " + System.getProperty("os.name") + " " + Sys.arch() + " · Java " + System.getProperty("java.version") + " · pasta " + base.getAbsolutePath());
        if (conf.bool("isolated", true)) log(inst.isolatedLinux() ? "isolamento forte: bubblewrap" : Sys.linux() ? "isolamento básico (instale o bubblewrap para o forte: apt install bubblewrap)" : "isolamento básico (servidores só em 127.0.0.1, pasta própria, ambiente limpo)");
        panel.start();
        log(net.linked ? "vinculada à conta " + (net.owner == null ? "" : net.owner) + " — configure tudo pelo site (Hosts › Máquinas)" : "SEM CONTA: abra http://localhost:" + conf.num("panel.port", 8765) + " para vincular · sem conta, depois de 5 min o PC vira host grátis da rede");
        timer.schedule(() -> { if (bench.stale()) bench.runAsync(agent::statsSoon); }, 25, TimeUnit.SECONDS);
        timer.scheduleWithFixedDelay(() -> { if (bench.stale()) bench.runAsync(agent::statsSoon); }, 6, 6, TimeUnit.HOURS);
        timer.scheduleWithFixedDelay(() -> { try { inst.monitor(); } catch (Throwable t) { log("vigia: " + t); } }, 3, 3, TimeUnit.SECONDS);
        timer.scheduleWithFixedDelay(() -> { try { net.tick(); } catch (Throwable t) { log("rede: " + t); } }, 2, 60, TimeUnit.SECONDS);
        timer.scheduleWithFixedDelay(() -> { try { inst.bridges(); } catch (Throwable t) { log("ponte: " + t); } }, 4, 5, TimeUnit.SECONDS);   // 1.1 ponte da rede (sem IP fixo)
        timer.scheduleWithFixedDelay(() -> { try { agent.tick(); } catch (Throwable t) { log("central: " + t); } }, 3, 30, TimeUnit.SECONDS);   // 2.0 agente da central
        timer.scheduleWithFixedDelay(() -> { try { integrity.tick(); } catch (Throwable t) { log("integridade: " + t); } }, 60, 600, TimeUnit.SECONDS);
        timer.scheduleWithFixedDelay(() -> { try { schedules.tick(); } catch (Throwable t) { log("agendamentos: " + t); } }, 20, 30, TimeUnit.SECONDS);   // 1.3
        timer.scheduleWithFixedDelay(() -> { try { activity.flush(); } catch (Throwable ignored) { } }, 10, 10, TimeUnit.SECONDS);
        timer.scheduleWithFixedDelay(() -> { try { backups.tick(); } catch (Throwable t) { log("backups: " + t); } }, 60, 60, TimeUnit.SECONDS);   // 1.2 backup automático
        timer.scheduleWithFixedDelay(() -> { if (net.soon()) try { net.tick(); } catch (Throwable ignored) { } }, 3, 3, TimeUnit.SECONDS);
        if (conf.bool("cloudflare.enabled", false)) cloud.start();
        for (Instances.Inst i : inst.all.values()) if (i.autostart && !i.suspended) pool.execute(() -> { try { inst.start(i); } catch (Exception e) { log(i.name + ": " + e.getMessage()); } });
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { log("desligando: parando os servidores…"); activity.flush(); inst.stopAll(true); cloud.stop(); panel.stop(); }, "msh-shutdown"));
    }

    public static void main(String[] a) throws Exception {
        File dir = null; Integer port = null;
        for (int k = 0; k < a.length; k++) {
            if ("--dir".equals(a[k]) && k + 1 < a.length) dir = new File(a[++k]);
            else if ("--port".equals(a[k]) && k + 1 < a.length) port = Integer.parseInt(a[++k]);
            else if ("--version".equals(a[k])) { System.out.println(VERSION); return; }
        }
        if (dir == null) {
            try { File jar = new File(MinesyncHost.class.getProtectionDomain().getCodeSource().getLocation().toURI()); dir = jar.isFile() ? jar.getParentFile() : new File("."); } catch (Exception e) { dir = new File("."); }
        }
        if (Runtime.version().feature() < 21) { log("precisa do Java 21 ou mais novo (este é o " + Runtime.version() + ")"); System.exit(1); }
        new MinesyncHost(dir.getAbsoluteFile(), port).start();
        Thread.currentThread().join();
    }
}
