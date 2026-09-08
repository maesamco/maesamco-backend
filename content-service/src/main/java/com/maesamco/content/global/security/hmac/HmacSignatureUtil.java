package com.maesamco.content.global.security.hmac;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HMAC-SHA256 서명 생성/검증 공통 유틸.
 * 서명 대상 문자열은 "serviceName:method:path:bodyHash:nonce:timestamp" 형태로 고정한다
 * (게이트웨이 및 인증 보안 설계 6절, 팀 컨벤션 15절).
 *
 * ⚠️ 예전엔 "serviceName:timestamp"만 서명해서, 유효한 서명 헤더 하나만 손에 넣으면
 * 완전히 다른 경로/메서드/바디로 요청을 바꿔치기해도 검증을 통과했다(리뷰로 발견).
 * method+path+bodyHash를 서명에 포함시켜 요청 내용 자체에 서명을 묶고, nonce로
 * 동일 서명의 재사용(재전송)까지 막는다.
 */
public final class HmacSignatureUtil {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String DIGEST_ALGORITHM = "SHA-256";

    private HmacSignatureUtil() {
    }

    public static String sign(String serviceName, String method, String path, String bodyHash,
                              String nonce, long timestampMillis, String secretKey) {
        String payload = serviceName + ":" + method + ":" + path + ":" + bodyHash + ":" + nonce + ":" + timestampMillis;
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

    /** 요청 바디의 SHA-256 해시(Base64) — 바디가 없으면(GET 등) 빈 바이트 배열의 해시를 사용한다. */
    public static String hashBody(byte[] body) {
        try {
            MessageDigest digest = MessageDigest.getInstance(DIGEST_ALGORITHM);
            byte[] hash = digest.digest(body == null ? new byte[0] : body);
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("바디 해시 생성 실패", e);
        }
    }

    /** 상수시간 비교 — 타이밍 공격 방지. 반드시 이 메서드로만 서명을 비교할 것. */
    public static boolean verify(String serviceName, String method, String path, String bodyHash,
                                 String nonce, long timestampMillis, String secretKey, String givenSignature) {
        String expected = sign(serviceName, method, path, bodyHash, nonce, timestampMillis, secretKey);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                givenSignature.getBytes(StandardCharsets.UTF_8)
        );
    }
}