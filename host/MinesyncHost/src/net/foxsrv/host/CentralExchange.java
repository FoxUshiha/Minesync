package net.foxsrv.host;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;

/**
 * 2.0 — um pedido do PAINEL vindo da CENTRAL (pelo agente), executado pelas MESMAS rotas do painel local: arquivos,
 * backups, console, inicialização, compactar… Corpo de entrada e de saída podem ser fluxos (enviar/baixar arquivos
 * grandes passando pela central, sem guardar em memória).
 */
final class CentralExchange extends HttpExchange {
    private final String method; private final URI uri; private final Headers reqH = new Headers(), resH = new Headers();
    private InputStream in; private OutputStream out; final ByteArrayOutputStream buf = new ByteArrayOutputStream();
    int status = 200; boolean streamed;
    /** fábrica da saída em fluxo: chamada quando a rota responde (com os cabeçalhos já prontos) */
    interface OutFactory { OutputStream open(Headers h, long len) throws java.io.IOException; }
    private final OutFactory outFactory;

    CentralExchange(String method, URI uri, InputStream body, OutFactory outFactory) {
        this.method = method; this.uri = uri; this.in = body != null ? body : new ByteArrayInputStream(new byte[0]); this.outFactory = outFactory;
        reqH.add("X-MSH", "1");
    }
    @Override public Headers getRequestHeaders() { return reqH; }
    @Override public Headers getResponseHeaders() { return resH; }
    @Override public URI getRequestURI() { return uri; }
    @Override public String getRequestMethod() { return method; }
    @Override public HttpContext getHttpContext() { return null; }
    @Override public void close() { try { if (out != null) out.close(); } catch (Exception ignored) { } }
    @Override public InputStream getRequestBody() { return in; }
    @Override public OutputStream getResponseBody() { return out != null ? out : buf; }
    @Override public void sendResponseHeaders(int code, long len) throws java.io.IOException {
        status = code;
        String ct = resH.getFirst("Content-Type");
        // arquivo (não JSON) e com destino em fluxo: abre o envio para a central
        if (outFactory != null && (ct == null || !ct.startsWith("application/json"))) { out = outFactory.open(resH, len); streamed = true; }
    }
    @Override public InetSocketAddress getRemoteAddress() { return new InetSocketAddress("127.0.0.1", 0); }
    @Override public int getResponseCode() { return status; }
    @Override public InetSocketAddress getLocalAddress() { return new InetSocketAddress("127.0.0.1", 0); }
    @Override public String getProtocol() { return "HTTP/1.1"; }
    @Override public Object getAttribute(String name) { return null; }
    @Override public void setAttribute(String name, Object value) { }
    @Override public void setStreams(InputStream i, OutputStream o) { if (i != null) in = i; if (o != null) out = o; }
    @Override public HttpPrincipal getPrincipal() { return null; }
}
