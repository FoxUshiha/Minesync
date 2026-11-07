package net.foxsrv.host;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.util.Map;

final class Json {
    static final Gson G = new GsonBuilder().disableHtmlEscaping().create();
    static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private Json() {}
    static String str(Object o) { return G.toJson(o); }
    static JsonObject obj(String s) { try { JsonElement e = com.google.gson.JsonParser.parseString(s == null || s.isBlank() ? "{}" : s); return e.isJsonObject() ? e.getAsJsonObject() : new JsonObject(); } catch (Exception e) { return new JsonObject(); } }
    static String s(JsonObject o, String k, String d) { try { JsonElement e = o.get(k); return e == null || e.isJsonNull() ? d : e.getAsString(); } catch (Exception x) { return d; } }
    static long n(JsonObject o, String k, long d) { try { JsonElement e = o.get(k); return e == null || e.isJsonNull() ? d : e.getAsLong(); } catch (Exception x) { return d; } }
    static double dbl(JsonObject o, String k, double d) { try { JsonElement e = o.get(k); return e == null || e.isJsonNull() ? d : e.getAsDouble(); } catch (Exception x) { return d; } }
    static boolean b(JsonObject o, String k, boolean d) { try { JsonElement e = o.get(k); return e == null || e.isJsonNull() ? d : e.getAsBoolean(); } catch (Exception x) { return d; } }
    static Map<String, Object> toMap(JsonObject o) { return G.fromJson(o, new TypeToken<java.util.LinkedHashMap<String, Object>>() {}.getType()); }
    @SuppressWarnings("unchecked")
    static Map<String, Object> deepCopy(Map<String, Object> m) { return G.fromJson(G.toJson(m), new TypeToken<java.util.LinkedHashMap<String, Object>>() {}.getType()); }
}
