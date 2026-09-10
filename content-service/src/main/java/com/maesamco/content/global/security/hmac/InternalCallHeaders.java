package com.maesamco.content.global.security.hmac;

public final class InternalCallHeaders {
    public static final String SERVICE = "X-Internal-Service";
    public static final String TIMESTAMP = "X-Internal-Timestamp";
    public static final String NONCE = "X-Internal-Nonce";
    public static final String SIGNATURE = "X-Internal-Signature";

    private InternalCallHeaders() {
    }
}