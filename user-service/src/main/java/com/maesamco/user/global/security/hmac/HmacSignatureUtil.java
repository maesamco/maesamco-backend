package com.maesamco.user.global.security.hmac;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * HMAC-SHA256 서명 생성/검증 공통 유틸.
 * 서명 대상 문자열은 항상 "serviceName:timestamp" 형태로 고정한다
 * (게이트웨이 및 인증 보안 설계 6절, 팀 컨벤션 15절).
 */
public final class HmacSignatureUtil {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private HmacSignatureUtil() {
    }

    public static String sign(String serviceName, long timestampMillis, String secretKey) {
        String payload = serviceName + ":" + timestampMillis;
        return signRaw(payload, secretKey);
    }

    private static String signRaw(String payload, String secretKey) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] rawHmac = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(rawHmac);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 서명 생성 실패", e);
        }
    }

    /** 상수시간 비교 — 타이밍 공격 방지. 반드시 이 메서드로만 서명을 비교할 것. */
    public static boolean verify(String serviceName, long timestampMillis, String secretKey, String givenSignature) {
        String expected = sign(serviceName, timestampMillis, secretKey);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                givenSignature.getBytes(StandardCharsets.UTF_8)
        );
    }
}
