package net.foxsrv.host;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** ping de status do Minecraft Java (o mesmo da lista de servidores do jogo): MOTD, versão, jogadores, ícone — qualquer servidor (vanilla, Fabric, Forge…) */
final class StatusPing {
    private StatusPing() {}
    static final class Result { boolean online; String motd = "", version = ""; int players, max, protocol; String favicon; }
    static void varint(DataOutputStream o, int v) throws IOException { while ((v & ~0x7F) != 0) { o.writeByte((v & 0x7F) | 0x80); v >>>= 7; } o.writeByte(v); }
    static int varint(DataInputStream in) throws IOException { int n = 0, s = 0; byte b; do { b = in.readByte(); n |= (b & 0x7F) << s; s += 7; if (s > 35) throw new IOException("varint"); } while ((b & 0x80) != 0); return n; }
    static Result ping(String host, int port, int timeoutMs) {
        Result r = new Result();
        try (Socket so = new Socket()) {
            so.connect(new InetSocketAddress(host, port), timeoutMs); so.setSoTimeout(timeoutMs);
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(so.getOutputStream())); DataInputStream in = new DataInputStream(new BufferedInputStream(so.getInputStream()));
            ByteArrayOutputStream hb = new ByteArrayOutputStream(); DataOutputStream h = new DataOutputStream(hb);
            h.writeByte(0); varint(h, 767); byte[] hs = host.getBytes(StandardCharsets.UTF_8); varint(h, hs.length); h.write(hs); h.writeShort(port); varint(h, 1);
            varint(out, hb.size()); out.write(hb.toByteArray()); out.writeByte(1); out.writeByte(0); out.flush();
            int len = varint(in); if (len <= 0 || len > 2_000_000) return r;
            if (varint(in) != 0) return r;
            int sl = varint(in); if (sl <= 0 || sl > 2_000_000) return r;
            byte[] b = new byte[sl]; in.readFully(b);
            JsonObject j = Json.obj(new String(b, StandardCharsets.UTF_8));
            r.online = true;
            r.motd = flat(j.get("description")).replaceAll("§.", "").trim();
            JsonObject v = j.has("version") && j.get("version").isJsonObject() ? j.getAsJsonObject("version") : new JsonObject(); r.version = Json.s(v, "name", ""); r.protocol = (int) Json.n(v, "protocol", 0);
            JsonObject p = j.has("players") && j.get("players").isJsonObject() ? j.getAsJsonObject("players") : new JsonObject(); r.players = (int) Json.n(p, "online", 0); r.max = (int) Json.n(p, "max", 0);
            String fav = Json.s(j, "favicon", null); if (fav != null && fav.startsWith("data:image/png;base64,") && fav.length() < 200_000) r.favicon = fav;
        } catch (Exception ignored) { }
        return r;
    }
    static String flat(JsonElement e) {
        if (e == null || e.isJsonNull()) return "";
        if (e.isJsonPrimitive()) return e.getAsString();
        StringBuilder b = new StringBuilder();
        if (e.isJsonObject()) { JsonObject o = e.getAsJsonObject(); if (o.has("text")) b.append(flat(o.get("text"))); if (o.has("extra")) b.append(flat(o.get("extra"))); }
        else if (e.isJsonArray()) for (JsonElement x : e.getAsJsonArray()) b.append(flat(x));
        return b.toString();
    }
}
