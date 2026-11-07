package net.foxsrv.host;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 1.3 — AGENDAMENTOS por servidor (como numa host): de X em X minutos, todo dia às HH:MM ou em dias da semana às HH:MM,
 * fazer: comando no console (vários, um por linha), reiniciar, ligar, desligar ou backup. "Só se estiver ligado" para
 * comandos/reinício. Conferidos a cada 30 s.
 */
final class Schedules {
    static final class Sched { String id, name, kind = "interval", at = "04:00", action = "restart", payload = ""; long every_min = 60; List<Integer> days; boolean on = true, only_running = true; long last; }
    static final List<String> KINDS = List.of("interval", "daily", "weekly"), ACTIONS = List.of("command", "restart", "start", "stop", "backup");
    private final MinesyncHost H;
    Schedules(MinesyncHost h) { this.H = h; }

    Sched parse(JsonObject b, Sched into) {
        Sched s = into != null ? into : new Sched();
        if (s.id == null) s.id = Long.toString(System.currentTimeMillis(), 36) + Users.rnd(2).replaceAll("[^A-Za-z0-9]", "x");
        String n = Json.s(b, "name", s.name == null ? "" : s.name).replaceAll("[<>\"]", "").trim(); s.name = n.length() > 40 ? n.substring(0, 40) : n;
        String k = Json.s(b, "kind", s.kind); if (!KINDS.contains(k)) throw new Api.Err("bad_schedule"); s.kind = k;
        String a = Json.s(b, "action", s.action); if (!ACTIONS.contains(a)) throw new Api.Err("bad_schedule"); s.action = a;
        s.every_min = Json.n(b, "every_min", s.every_min); if (s.every_min < 5 || s.every_min > 60L * 24 * 31) throw new Api.Err("bad_interval");
        String at = Json.s(b, "at", s.at).trim(); if (!at.matches("([01]\\d|2[0-3]):[0-5]\\d")) throw new Api.Err("bad_time"); s.at = at;
        if (b.has("days") && b.get("days").isJsonArray()) { List<Integer> d = new ArrayList<>(); for (JsonElement e : b.getAsJsonArray("days")) { int x = e.getAsInt(); if (x >= 1 && x <= 7 && !d.contains(x)) d.add(x); } s.days = d; }
        if ("weekly".equals(s.kind) && (s.days == null || s.days.isEmpty())) throw new Api.Err("bad_schedule");
        String p = Json.s(b, "payload", s.payload).replace("\r", ""); if (p.length() > 2000) throw new Api.Err("bad_command"); s.payload = p;
        if ("command".equals(s.action) && p.isBlank()) throw new Api.Err("bad_command");
        s.on = Json.b(b, "on", s.on); s.only_running = Json.b(b, "only_running", s.only_running);
        if (into == null) s.last = System.currentTimeMillis();
        return s;
    }

    void tick() {
        if (!H.conf.bool("enabled", false)) return;
        long now = System.currentTimeMillis(); LocalDateTime t = LocalDateTime.now();
        String hm = String.format("%02d:%02d", t.getHour(), t.getMinute()); int dow = t.getDayOfWeek().getValue();
        for (Instances.Inst i : H.inst.all.values()) {
            if (i.schedules == null || i.schedules.isEmpty()) continue;
            Users.User own = H.users.get(i.owner); if (own != null && own.suspended) continue;
            for (Sched s : new ArrayList<>(i.schedules)) {
                if (!s.on) continue;
                boolean due = switch (s.kind) {
                    case "interval" -> now - s.last >= s.every_min * 60_000L;
                    case "daily" -> hm.equals(s.at) && now - s.last > 90_000L;
                    default -> s.days != null && s.days.contains(dow) && hm.equals(s.at) && now - s.last > 90_000L;
                };
                if (!due) continue;
                s.last = now; H.inst.save();
                run(i, s);
            }
        }
    }
    void run(Instances.Inst i, Sched s) {
        boolean running = i.proc != null && i.proc.isAlive();
        try {
            switch (s.action) {
                case "command": if (!running) { if (s.only_running) return; throw new Api.Err("not_running"); } for (String c : s.payload.split("\n")) if (!c.isBlank()) H.inst.command(i, c); break;
                case "restart": if (!running && s.only_running) return; if (running) { H.inst.stop(i); H.pool.execute(() -> { for (int k = 0; k < 120 && i.proc != null && i.proc.isAlive(); k++) try { Thread.sleep(500); } catch (InterruptedException ignored) { } try { H.inst.start(i); } catch (Exception e) { H.inst.line(i, "[MinesyncHost] agendamento: " + e.getMessage()); } }); } else H.inst.start(i); break;
                case "start": if (!running) H.inst.start(i); break;
                case "stop": if (running) H.inst.stop(i); break;
                case "backup": H.backups.autoBackup(i, "agendado"); break;
            }
            H.inst.line(i, "[MinesyncHost] agendamento \"" + (s.name == null || s.name.isEmpty() ? s.action : s.name) + "\": " + s.action);
            H.activity.log(i, "agendamento", "schedule.run", s.name == null || s.name.isEmpty() ? s.action : s.name);
        } catch (Exception e) { H.inst.line(i, "[MinesyncHost] agendamento: " + e.getMessage()); }
    }
}
