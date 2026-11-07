package net.foxsrv.host;

/** erro de API com código (vira {ok:false,error}) */
final class Api {
    private Api() {}
    static final class Err extends RuntimeException {
        final int status;
        Err(String code) { this(code, 400); }
        Err(String code, int status) { super(code, null, false, false); this.status = status; }
    }
}
