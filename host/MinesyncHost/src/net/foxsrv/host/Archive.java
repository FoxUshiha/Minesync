package net.foxsrv.host;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 1.2 — COMPACTAR e DESCOMPACTAR (.zip, .tar, .tar.gz/.tgz) só com o Java, com segurança:
 * <ul>
 *   <li>nada sai da pasta de destino (caminhos "..", absolutos, "C:", links simbólicos e links "hard" são recusados/ignorados);</li>
 *   <li>limite de bytes descompactados e de quantidade de arquivos ("zip bomb" para no meio e o que foi escrito é apagado);</li>
 *   <li>arquivos protegidos da host não são sobrescritos;</li>
 *   <li>ao compactar, atalhos/links simbólicos NÃO são seguidos (não dá para "puxar" arquivos de fora da pasta).</li>
 * </ul>
 */
final class Archive {
    private Archive() {}

    /** progresso de uma tarefa (o painel mostra) */
    static final class Progress { volatile long bytes, files, total, in, inTotal; volatile boolean cancel; volatile String current = ""; }   // 2.1.2: total (bytes a gravar) e in/inTotal (bytes lidos do arquivo compactado) para a %

    static String kind(String name) {
        String n = name.toLowerCase(java.util.Locale.ROOT);
        if (n.endsWith(".zip")) return "zip";
        if (n.endsWith(".tar.gz") || n.endsWith(".tgz")) return "tgz";
        if (n.endsWith(".tar")) return "tar";
        return null;
    }

    // ------------------------------------------------------------------ juntar arquivos (sem seguir links)
    static final class Item { final Path file; final String name; final boolean dir; Item(Path f, String n, boolean d) { file = f; name = n; dir = d; } }
    /** lista o que entra no pacote: cada caminho de srcs, com o nome relativo a relTo; links simbólicos ficam de fora */
    static List<Item> collect(Path relTo, List<Path> srcs, Predicate<Path> skip) throws IOException {
        List<Item> out = new ArrayList<>();
        for (Path s : srcs) {
            if (!Files.exists(s, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(s)) continue;
            Files.walkFileTree(s, java.util.EnumSet.noneOf(java.nio.file.FileVisitOption.class), Integer.MAX_VALUE, new SimpleFileVisitor<>() {
                @Override public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes a) {
                    if (a.isSymbolicLink() || skip != null && skip.test(d)) return FileVisitResult.SKIP_SUBTREE;
                    if (!d.equals(relTo)) out.add(new Item(d, rel(relTo, d) + "/", true));
                    return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFile(Path f, BasicFileAttributes a) {
                    if (a.isRegularFile() && !a.isSymbolicLink() && (skip == null || !skip.test(f))) out.add(new Item(f, rel(relTo, f), false));
                    return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFileFailed(Path f, IOException e) { return FileVisitResult.CONTINUE; }
            });
        }
        return out;
    }
    private static String rel(Path base, Path p) { return base.relativize(p).toString().replace('\\', '/'); }

    // ------------------------------------------------------------------ compactar
    /** escreve o pacote (zip, tar ou tgz) em out; arquivo que não dá para ler (em uso) é pulado e contado em skipped */
    static int write(String kind, List<Item> items, OutputStream out, Progress pr, int level) throws IOException {
        int skipped = 0;
        if (pr != null && pr.total <= 0) { long t = 0; for (Item it : items) if (!it.dir) try { t += Files.size(it.file); } catch (IOException ignored) { } pr.total = t; }
        if ("zip".equals(kind)) {
            ZipOutputStream z = new ZipOutputStream(out, StandardCharsets.UTF_8); z.setLevel(level < 0 ? Deflater.DEFAULT_COMPRESSION : level);
            byte[] buf = new byte[1 << 16];
            for (Item it : items) {
                if (pr != null && pr.cancel) throw new IOException("canceled");
                if (it.dir) { ZipEntry e = new ZipEntry(it.name); z.putNextEntry(e); z.closeEntry(); continue; }
                InputStream in;
                try { in = Files.newInputStream(it.file, LinkOption.NOFOLLOW_LINKS); } catch (IOException e) { skipped++; continue; }
                try (in) {
                    ZipEntry e = new ZipEntry(it.name);
                    try { e.setLastModifiedTime(Files.getLastModifiedTime(it.file, LinkOption.NOFOLLOW_LINKS)); } catch (IOException ignored) { }
                    z.putNextEntry(e);
                    if (pr != null) pr.current = it.name;
                    int n; while ((n = in.read(buf)) > 0) { z.write(buf, 0, n); if (pr != null) pr.bytes += n; }
                    z.closeEntry(); if (pr != null) pr.files++;
                } catch (IOException e) {
                    if (e.getMessage() != null && (e.getMessage().startsWith("quota") || e.getMessage().equals("canceled"))) throw e;
                    skipped++;    // arquivo em uso/travado (ex.: session.lock no Windows): a entrada fica vazia
                    try { z.closeEntry(); } catch (IOException ignored) { }
                }
            }
            z.finish(); z.flush();
        } else {
            OutputStream o = "tgz".equals(kind) ? new GZIPOutputStream(new NoClose(out), 1 << 16) { { def.setLevel(level < 0 ? Deflater.DEFAULT_COMPRESSION : level); } } : new NoClose(out);
            TarOut t = new TarOut(o);
            for (Item it : items) {
                if (pr != null && pr.cancel) throw new IOException("canceled");
                long mt = 0; try { mt = Files.getLastModifiedTime(it.file, LinkOption.NOFOLLOW_LINKS).toMillis() / 1000; } catch (IOException ignored) { }
                if (it.dir) { t.entry(it.name, 0, mt, true, null, pr); continue; }
                long size; InputStream in;
                try { size = Files.size(it.file); in = Files.newInputStream(it.file, LinkOption.NOFOLLOW_LINKS); } catch (IOException e) { skipped++; continue; }
                try (in) { if (pr != null) pr.current = it.name; t.entry(it.name, size, mt, false, in, pr); if (pr != null) pr.files++; }
            }
            t.finish(); o.close();
        }
        return skipped;
    }
    private static final class NoClose extends FilterOutputStream {
        NoClose(OutputStream o) { super(o); }
        @Override public void write(byte[] b, int off, int len) throws IOException { out.write(b, off, len); }
        @Override public void close() throws IOException { out.flush(); }
    }
    /** conta o que é escrito e para no limite (cota de disco/backup) */
    static final class Counting extends FilterOutputStream {
        long n; final long max;
        Counting(OutputStream o, long max) { super(o); this.max = max; }
        @Override public void write(int b) throws IOException { if (++n > max) throw new IOException("quota"); out.write(b); }
        @Override public void write(byte[] b, int off, int len) throws IOException { n += len; if (n > max) throw new IOException("quota"); out.write(b, off, len); }
    }

    /** TAR (ustar + nome longo do GNU) */
    private static final class TarOut {
        private final OutputStream o; private final byte[] buf = new byte[1 << 16];
        TarOut(OutputStream o) { this.o = o; }
        void entry(String name, long size, long mtime, boolean dir, InputStream in, Progress pr) throws IOException {
            byte[] nb = name.getBytes(StandardCharsets.UTF_8);
            if (nb.length > 100) { header("././@LongLink", nb.length + 1, 0, 'L'); o.write(nb); o.write(0); pad(nb.length + 1); nb = java.util.Arrays.copyOf(nb, 100); }
            header(new String(nb, StandardCharsets.UTF_8), dir ? 0 : size, mtime, dir ? '5' : '0', nb);
            if (dir) return;
            long left = size;
            while (left > 0) {
                int n = in.read(buf, 0, (int) Math.min(buf.length, left));
                if (n < 0) { java.util.Arrays.fill(buf, (byte) 0); n = (int) Math.min(buf.length, left); }   // o arquivo diminuiu enquanto lia: completa com zeros
                o.write(buf, 0, n); left -= n; if (pr != null) pr.bytes += n;
            }
            pad(size);
        }
        private void header(String name, long size, long mtime, char type) throws IOException { header(name, size, mtime, type, name.getBytes(StandardCharsets.UTF_8)); }
        private void header(String name, long size, long mtime, char type, byte[] nb) throws IOException {
            byte[] h = new byte[512];
            System.arraycopy(nb, 0, h, 0, Math.min(100, nb.length));
            oct(h, 100, 8, type == '5' ? 0755 : 0644); oct(h, 108, 8, 0); oct(h, 116, 8, 0);
            if (size < 077777777777L) oct(h, 124, 12, size); else { h[124] = (byte) 0x80; for (int k = 0; k < 8; k++) h[135 - k] = (byte) (size >>> (8 * k)); }
            oct(h, 136, 12, Math.max(0, mtime));
            for (int k = 148; k < 156; k++) h[k] = ' ';
            h[156] = (byte) type;
            byte[] mg = "ustar  \0".getBytes(StandardCharsets.US_ASCII); System.arraycopy(mg, 0, h, 257, 8);
            long sum = 0; for (byte b : h) sum += (b & 0xff);
            String cs = String.format("%06o", sum); for (int k = 0; k < 6; k++) h[148 + k] = (byte) cs.charAt(k); h[154] = 0; h[155] = ' ';
            o.write(h);
        }
        private static void oct(byte[] h, int off, int len, long v) { String s = Long.toOctalString(v); while (s.length() < len - 1) s = "0" + s; for (int k = 0; k < len - 1; k++) h[off + k] = (byte) s.charAt(k); h[off + len - 1] = 0; }
        private void pad(long n) throws IOException { int r = (int) (n % 512); if (r != 0) o.write(new byte[512 - r]); }
        void finish() throws IOException { o.write(new byte[1024]); o.flush(); }
    }

    // ------------------------------------------------------------------ descompactar
    static final class Limits { long maxBytes, maxEntries; Limits(long b, long e) { maxBytes = b; maxEntries = e; } }
    /** resultado: arquivos escritos, bytes, entradas puladas (links, protegidos, caminhos ruins) */
    static long[] extract(Path archive, String kind, Path base, Path dest, Limits lim, Predicate<Path> protectedFn, Progress pr) throws IOException {
        List<Path> created = new ArrayList<>(); long[] r = new long[3];
        if (pr != null) { try { pr.inTotal = Files.size(archive); } catch (IOException ignored) { } pr.in = 0; }
        InputStream src0 = Files.newInputStream(archive, LinkOption.NOFOLLOW_LINKS);
        InputStream counted = pr == null ? src0 : new java.io.FilterInputStream(src0) {   // conta o que já foi lido do arquivo (a % da extração)
            @Override public int read() throws IOException { int c = super.read(); if (c >= 0) pr.in++; return c; }
            @Override public int read(byte[] b, int o, int l) throws IOException { int n = super.read(b, o, l); if (n > 0) pr.in += n; return n; }
            @Override public long skip(long n) throws IOException { long k = super.skip(n); if (k > 0) pr.in += k; return k; }
        };
        try (InputStream raw = new BufferedInputStream(counted, 1 << 16)) {
            if ("zip".equals(kind)) {
                try (ZipInputStream z = new ZipInputStream(raw, StandardCharsets.UTF_8)) {
                    ZipEntry e;
                    while ((e = nextZip(z)) != null) {
                        if (pr != null && pr.cancel) throw new IOException("canceled");
                        if (++r[0] + r[2] > lim.maxEntries) throw new Api.Err("too_many_files");
                        one(e.getName(), e.isDirectory(), z, e.getLastModifiedTime(), base, dest, lim, protectedFn, pr, r, created);
                    }
                }
            } else {
                InputStream in = "tgz".equals(kind) ? new GZIPInputStream(raw, 1 << 16) : raw;
                TarIn t = new TarIn(in); TarIn.Entry e;
                while ((e = t.next()) != null) {
                    if (pr != null && pr.cancel) throw new IOException("canceled");
                    if (++r[0] + r[2] > lim.maxEntries) throw new Api.Err("too_many_files");
                    if (e.type != '0' && e.type != 0 && e.type != '7' && e.type != '5') { r[2]++; r[0]--; continue; }   // links e especiais: ignorados
                    one(e.name, e.type == '5' || e.name.endsWith("/"), t.data(), e.mtime > 0 ? FileTime.fromMillis(e.mtime * 1000) : null, base, dest, lim, protectedFn, pr, r, created);
                }
            }
        } catch (IOException | RuntimeException x) {
            // falhou no meio (limite, arquivo corrompido): apaga o que esta extração criou
            for (int k = created.size() - 1; k >= 0; k--) try { Files.deleteIfExists(created.get(k)); } catch (IOException ignored) { }
            if (x instanceof java.util.zip.ZipException || x instanceof EOFException) throw new Api.Err("bad_archive");
            throw x;
        }
        return r;
    }
    private static ZipEntry nextZip(ZipInputStream z) throws IOException { try { return z.getNextEntry(); } catch (IllegalArgumentException e) { throw new Api.Err("bad_archive"); } }
    private static void one(String name, boolean dir, InputStream in, FileTime mt, Path base, Path dest, Limits lim, Predicate<Path> protectedFn, Progress pr, long[] r, List<Path> created) throws IOException {
        String n = name.replace('\\', '/');
        while (n.startsWith("./")) n = n.substring(2);
        if (n.isEmpty() || n.equals("/")) { r[0]--; return; }
        if (n.startsWith("/") || n.matches("^[A-Za-z]:.*") || n.contains("\0") || ("/" + n + "/").contains("/../")) { r[2]++; r[0]--; return; }
        Path p = dest.resolve(n).normalize();
        if (!p.startsWith(dest) || !p.startsWith(base) || p.equals(dest)) { r[2]++; r[0]--; return; }
        // nenhuma pasta do caminho pode ser um link simbólico (não escreve fora por um atalho que já exista)
        Path cur = base; for (Path part : base.relativize(p)) { cur = cur.resolve(part); if (Files.isSymbolicLink(cur)) { r[2]++; r[0]--; return; } }
        if (dir) { mk(p, base, created); return; }
        if (protectedFn != null && protectedFn.test(p)) { r[2]++; r[0]--; return; }
        if (Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) { r[2]++; r[0]--; return; }
        mk(p.getParent(), base, created);
        Path tmp = p.resolveSibling(p.getFileName() + ".msh-x" + Long.toHexString(System.nanoTime()));
        boolean existed = Files.exists(p, LinkOption.NOFOLLOW_LINKS);
        try (OutputStream o = Files.newOutputStream(tmp)) {
            byte[] buf = new byte[1 << 16]; int k;
            while ((k = in.read(buf)) > 0) {
                r[1] += k; if (pr != null) pr.bytes += k;
                if (r[1] > lim.maxBytes) throw new Api.Err("archive_too_big");
                o.write(buf, 0, k);
            }
        } catch (IOException | RuntimeException e) { try { Files.deleteIfExists(tmp); } catch (IOException ignored) { } throw e; }
        Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING);
        if (!existed) created.add(p);
        if (mt != null) try { Files.setLastModifiedTime(p, mt); } catch (IOException ignored) { }
        if (pr != null) { pr.files++; pr.current = n; }
    }
    private static void mk(Path d, Path base, List<Path> created) throws IOException {
        if (d == null || Files.isDirectory(d)) return;
        List<Path> chain = new ArrayList<>(); Path c = d; while (c != null && c.startsWith(base) && !Files.exists(c, LinkOption.NOFOLLOW_LINKS)) { chain.add(0, c); c = c.getParent(); }
        for (Path x : chain) { Files.createDirectory(x); created.add(x); }
        if (!Files.isDirectory(d, LinkOption.NOFOLLOW_LINKS)) throw new Api.Err("bad_path");
    }

    /** leitor de TAR (ustar, GNU nome longo "L", pax "x" path, tamanho base-256) */
    private static final class TarIn {
        static final class Entry { String name; char type; long size, mtime; }
        private final InputStream in; private long left, pad; private final byte[] h = new byte[512];
        TarIn(InputStream in) { this.in = in; }
        Entry next() throws IOException {
            skip(left + pad); left = 0; pad = 0;
            String longName = null;
            while (true) {
                if (!full(h)) return null;
                boolean zero = true; for (byte b : h) if (b != 0) { zero = false; break; }
                if (zero) return null;
                long sum = 0; for (int k = 0; k < 512; k++) sum += (k >= 148 && k < 156) ? ' ' : (h[k] & 0xff);
                long cs = num(h, 148, 8); if (cs != sum) throw new Api.Err("bad_archive");
                Entry e = new Entry(); e.type = (char) (h[156] & 0xff); e.size = num(h, 124, 12); e.mtime = num(h, 136, 12);
                if (e.size < 0) throw new Api.Err("bad_archive");
                String nm = str(h, 0, 100);
                if (new String(h, 257, 5, StandardCharsets.US_ASCII).equals("ustar")) { String pre = str(h, 345, 155); if (!pre.isEmpty() && h[156] != 'L') nm = pre + "/" + nm; }
                if (e.type == 'L' || e.type == 'x' || e.type == 'g') {
                    if (e.size > 1 << 20) throw new Api.Err("bad_archive");
                    byte[] d = new byte[(int) e.size]; if (!full(d)) throw new EOFException(); skip(padOf(e.size));
                    if (e.type == 'L') longName = str(d, 0, d.length);
                    else if (e.type == 'x') { String p = pax(d); if (p != null) longName = p; }
                    continue;
                }
                e.name = longName != null ? longName : nm;
                left = e.size; pad = padOf(e.size);
                if (e.type == '5' || e.type == '1' || e.type == '2' || e.type == '3' || e.type == '4' || e.type == '6') { skip(left + pad); left = 0; pad = 0; }
                return e;
            }
        }
        InputStream data() {
            return new InputStream() {
                @Override public int read() throws IOException { byte[] b = new byte[1]; int n = read(b, 0, 1); return n < 0 ? -1 : b[0] & 0xff; }
                @Override public int read(byte[] b, int off, int len) throws IOException { if (left <= 0) return -1; int n = in.read(b, off, (int) Math.min(len, left)); if (n < 0) throw new EOFException(); left -= n; return n; }
            };
        }
        private static long padOf(long s) { long r = s % 512; return r == 0 ? 0 : 512 - r; }
        private boolean full(byte[] b) throws IOException { int o = 0; while (o < b.length) { int n = in.read(b, o, b.length - o); if (n < 0) { if (o == 0) return false; throw new EOFException(); } o += n; } return true; }
        private void skip(long n) throws IOException { byte[] b = new byte[8192]; while (n > 0) { int k = in.read(b, 0, (int) Math.min(b.length, n)); if (k < 0) throw new EOFException(); n -= k; } }
        private static String str(byte[] b, int off, int len) { int e = off; while (e < off + len && b[e] != 0) e++; return new String(b, off, e - off, StandardCharsets.UTF_8); }
        private static long num(byte[] b, int off, int len) {
            if ((b[off] & 0x80) != 0) { long v = 0; for (int k = off + 1; k < off + len; k++) v = (v << 8) | (b[k] & 0xff); return v; }
            String s = str(b, off, len).trim(); if (s.isEmpty()) return 0;
            try { return Long.parseLong(s, 8); } catch (NumberFormatException x) { throw new Api.Err("bad_archive"); }
        }
        private static String pax(byte[] d) {
            int i = 0; String path = null;
            while (i < d.length) {
                int sp = i; while (sp < d.length && d[sp] != ' ') sp++;
                if (sp >= d.length) break;
                int len; try { len = Integer.parseInt(new String(d, i, sp - i, StandardCharsets.US_ASCII)); } catch (NumberFormatException e) { break; }
                if (len <= sp - i || i + len > d.length) break;
                int end = i + len; if (d[end - 1] == '\n') end--;
                String rec = new String(d, sp + 1, end - sp - 1, StandardCharsets.UTF_8);
                if (rec.startsWith("path=")) path = rec.substring(5);
                i += len;
            }
            return path;
        }
    }
}
