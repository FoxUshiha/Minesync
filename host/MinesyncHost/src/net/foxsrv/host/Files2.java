package net.foxsrv.host;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * EXPLORADOR DE ARQUIVOS de um servidor: tudo preso à pasta dele (caminho normalizado e conferido; atalhos/links
 * simbólicos não são seguidos nem criados). Ler texto até 2 MB, gravar, enviar (até 256 MB), baixar, criar pasta,
 * renomear e apagar. 2.1: o server.jar pode ser renomeado/trocado como qualquer arquivo (só o eula.txt e os argumentos do
 * Forge ficam protegidos nos servidores Minecraft).
 */
final class Files2 {
    private Files2() {}
    static Path safe(File root, String rel) {
        Path base = root.toPath().toAbsolutePath().normalize();
        String r = rel == null ? "" : rel.replace('\\', '/');
        while (r.startsWith("/")) r = r.substring(1);
        if (r.contains("\0")) throw new Api.Err("bad_path");
        Path p = base.resolve(r).normalize();
        if (!p.startsWith(base)) throw new Api.Err("bad_path");
        // nenhuma parte do caminho pode ser link simbólico (não deixa sair da pasta por um atalho)
        Path cur = base;
        for (Path part : base.relativize(p)) { cur = cur.resolve(part); if (Files.isSymbolicLink(cur)) throw new Api.Err("bad_path"); }
        return p;
    }
    /** 1.3: conta com "tipos próprios" (ou servidor Java/Node/Python): pode trocar o arquivo principal e o server.jar */
    static final ThreadLocal<Boolean> RELAX = ThreadLocal.withInitial(() -> false);
    static boolean protectedName(Path base, Path p) {
        if (RELAX.get()) return false;
        String rel = base.relativize(p).toString().replace('\\', '/');
        return rel.equals("eula.txt") || rel.startsWith("libraries/") && rel.endsWith("_args.txt");
    }
    static List<Map<String, Object>> list(File root, String rel) {
        Path p = safe(root, rel); if (!Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) throw new Api.Err("not_found");
        List<Map<String, Object>> out = new ArrayList<>();
        try (var s = Files.list(p)) {
            s.sorted((a, b) -> { boolean da = Files.isDirectory(a), db = Files.isDirectory(b); return da != db ? (da ? -1 : 1) : a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString()); })
             .limit(2000).forEach(f -> { try { Map<String, Object> m = new LinkedHashMap<>(); BasicFileAttributes a = Files.readAttributes(f, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS); m.put("name", f.getFileName().toString()); m.put("dir", a.isDirectory()); m.put("size", a.size()); m.put("mtime", a.lastModifiedTime().toMillis()); m.put("link", a.isSymbolicLink()); out.add(m); } catch (IOException ignored) { } });
        } catch (IOException e) { throw new Api.Err("io_error"); }
        return out;
    }
    static String read(File root, String rel) {
        Path p = safe(root, rel);
        try { if (!Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) throw new Api.Err("not_found"); if (Files.size(p) > 2 * 1048576) throw new Api.Err("too_big"); byte[] b = Files.readAllBytes(p); for (int k = 0; k < Math.min(b.length, 8000); k++) if (b[k] == 0) throw new Api.Err("binary_file"); return new String(b, StandardCharsets.UTF_8); }
        catch (IOException e) { throw new Api.Err("io_error"); }
    }
    static void write(File root, String rel, byte[] data) {
        Path base = root.toPath().toAbsolutePath().normalize(); Path p = safe(root, rel);
        if (protectedName(base, p)) throw new Api.Err("protected_file");
        if (Files.isDirectory(p)) throw new Api.Err("is_dir");
        try { Files.createDirectories(p.getParent()); Path t = p.resolveSibling(p.getFileName() + ".msh-tmp"); Files.write(t, data); Files.move(t, p, java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
        catch (IOException e) { throw new Api.Err("io_error"); }
    }
    static void mkdir(File root, String rel) { try { Files.createDirectories(safe(root, rel)); } catch (IOException e) { throw new Api.Err("io_error"); } }
    static void rename(File root, String from, String to) {
        Path base = root.toPath().toAbsolutePath().normalize(); Path a = safe(root, from), b = safe(root, to);
        if (a.equals(base) || protectedName(base, a) || protectedName(base, b)) throw new Api.Err("protected_file");
        try { Files.createDirectories(b.getParent()); Files.move(a, b); } catch (IOException e) { throw new Api.Err("io_error"); }
    }
    static void delete(File root, String rel) {
        Path base = root.toPath().toAbsolutePath().normalize(); Path p = safe(root, rel);
        if (p.equals(base) || protectedName(base, p)) throw new Api.Err("protected_file");
        deleteTree(p);
    }
    static void deleteTree(Path p) {
        try {
            if (!Files.exists(p, LinkOption.NOFOLLOW_LINKS)) return;
            Files.walkFileTree(p, new SimpleFileVisitor<>() {
                @Override public FileVisitResult visitFile(Path f, BasicFileAttributes a) throws IOException { Files.delete(f); return FileVisitResult.CONTINUE; }
                @Override public FileVisitResult postVisitDirectory(Path d, IOException e) throws IOException { Files.delete(d); return FileVisitResult.CONTINUE; }
            });
        } catch (IOException e) { MinesyncHost.log("apagar " + p + ": " + e.getMessage()); }
    }
}
