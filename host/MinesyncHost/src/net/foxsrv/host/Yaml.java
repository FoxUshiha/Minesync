package net.foxsrv.host;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * YAML mínimo (o que o config.yml usa): mapas por indentação de 2 espaços, escalares (texto, número, true/false),
 * listas em linha [a, b] e comentários (#). Escreve de volta mantendo a ordem e os comentários de cabeçalho por chave.
 */
final class Yaml {
    private Yaml() {}

    static Map<String, Object> parse(String text) {
        Map<String, Object> root = new LinkedHashMap<>();
        List<Object[]> stack = new ArrayList<>();   // [indent, map]
        stack.add(new Object[]{ -1, root });
        for (String raw : text.replace("\r", "").split("\n")) {
            String line = stripComment(raw);
            if (line.trim().isEmpty()) continue;
            int ind = 0; while (ind < line.length() && line.charAt(ind) == ' ') ind++;
            String t = line.trim();
            int c = colon(t); if (c < 0) continue;
            String key = unquote(t.substring(0, c).trim()); String val = t.substring(c + 1).trim();
            while (stack.size() > 1 && (int) stack.get(stack.size() - 1)[0] >= ind) stack.remove(stack.size() - 1);
            @SuppressWarnings("unchecked") Map<String, Object> cur = (Map<String, Object>) stack.get(stack.size() - 1)[1];
            if (val.isEmpty()) { Map<String, Object> m = new LinkedHashMap<>(); cur.put(key, m); stack.add(new Object[]{ ind, m }); }
            else cur.put(key, scalar(val));
        }
        return root;
    }
    private static int colon(String t) {
        boolean q = false; char qc = 0;
        for (int i = 0; i < t.length(); i++) { char ch = t.charAt(i); if (q) { if (ch == qc) q = false; } else if (ch == '"' || ch == '\'') { q = true; qc = ch; } else if (ch == ':' && (i + 1 == t.length() || t.charAt(i + 1) == ' ')) return i; }
        return -1;
    }
    private static String stripComment(String s) {
        boolean q = false; char qc = 0;
        for (int i = 0; i < s.length(); i++) { char ch = s.charAt(i); if (q) { if (ch == qc) q = false; } else if (ch == '"' || ch == '\'') { q = true; qc = ch; } else if (ch == '#' && (i == 0 || s.charAt(i - 1) == ' ')) return s.substring(0, i); }
        return s;
    }
    private static String unquote(String s) {
        if (s.length() >= 2 && ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'")))) {
            String in = s.substring(1, s.length() - 1);
            return s.startsWith("\"") ? in.replace("\\\"", "\"").replace("\\\\", "\\") : in.replace("''", "'");
        }
        return s;
    }
    static Object scalar(String v) {
        if (v.startsWith("[") && v.endsWith("]")) {
            List<Object> l = new ArrayList<>(); String in = v.substring(1, v.length() - 1).trim();
            if (!in.isEmpty()) for (String p : in.split(",")) l.add(scalar(p.trim()));
            return l;
        }
        if (v.startsWith("\"") || v.startsWith("'")) return unquote(v);
        if (v.equals("true")) return Boolean.TRUE; if (v.equals("false")) return Boolean.FALSE;
        if (v.equals("null") || v.equals("~")) return null;
        try { if (v.matches("-?\\d{1,18}")) return Long.parseLong(v); if (v.matches("-?\\d+\\.\\d+")) return Double.parseDouble(v); } catch (NumberFormatException ignored) { }
        return v;
    }

    /** escreve o mapa; comments: "a.b" → comentário em cima da chave */
    static String dump(Map<String, Object> m, Map<String, String> comments) { StringBuilder b = new StringBuilder(); dump(b, m, "", 0, comments); return b.toString(); }
    @SuppressWarnings("unchecked")
    private static void dump(StringBuilder b, Map<String, Object> m, String path, int ind, Map<String, String> comments) {
        String pad = " ".repeat(ind);
        for (Map.Entry<String, Object> e : m.entrySet()) {
            String p = path.isEmpty() ? e.getKey() : path + "." + e.getKey();
            String cm = comments == null ? null : comments.get(p);
            if (cm != null) for (String l : cm.split("\n")) b.append(pad).append("# ").append(l).append('\n');
            Object v = e.getValue();
            if (v instanceof Map) { b.append(pad).append(e.getKey()).append(":\n"); dump(b, (Map<String, Object>) v, p, ind + 2, comments); }
            else b.append(pad).append(e.getKey()).append(": ").append(fmt(v)).append('\n');
        }
    }
    static String fmt(Object v) {
        if (v == null) return "\"\"";
        if (v instanceof List) { StringBuilder b = new StringBuilder("["); boolean f = true; for (Object o : (List<?>) v) { if (!f) b.append(", "); b.append(fmt(o)); f = false; } return b.append(']').toString(); }
        if (v instanceof Boolean || v instanceof Number) return String.valueOf(v);
        String s = String.valueOf(v);
        if (s.isEmpty() || !s.matches("[A-Za-z0-9_./-]+") || s.equals("true") || s.equals("false") || s.equals("null") || s.matches("-?\\d+(\\.\\d+)?")) return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        return s;
    }
}
