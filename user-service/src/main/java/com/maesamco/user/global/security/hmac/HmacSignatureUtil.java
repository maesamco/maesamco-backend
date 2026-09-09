package com.maesamco.user.global.security.hmac;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;

/**
 * HMAC-SHA256 서명 생성/검증 공통 유틸.
 * 서명 대상 문자열은 "serviceName:method:path:normalizedQuery:bodyHash:nonce:timestamp"
 * 형태로 고정한다(게이트웨이 및 인증 보안 설계 6절, 팀 컨벤션 15절).
 *
 * ⚠️ 예전엔 "serviceName:timestamp"만 서명해서, 유효한 서명 헤더 하나만 손에 넣으면
 * 완전히 다른 경로/메서드/바디로 요청을 바꿔치기해도 검증을 통과했다(리뷰로 발견).
 * method+path+bodyHash를 서명에 포함시켜 요청 내용 자체에 서명을 묶고, nonce로
 * 동일 서명의 재사용(재전송)까지 막는다. 쿼리스트링도 정규화(키 기준 정렬)해서
 * 포함시켜, "같은 method+path+body에 쿼리스트링만 바꾼 위조"까지 막는다(P2 리뷰 반영).
 */
/**
 * ⚠️ 배포 제약(P3 리뷰, 팀 논의 결과 — 지금은 문서화만 하고 코드 폴백은 추가하지 않기로
 * 결정): 이 서명 포맷은 이전 버전(serviceName:timestamp만 서명)과 하위 호환이 없다.
 * 4개 서비스(user/content/judge/coaching) 중 하나라도 배포 순서가 어긋나면(예:
 * judge-service만 먼저 새 버전으로 배포), 그 시간 동안 서비스 간 내부 호출이 전부
 * 401로 막힌다.
 *
 * 지금은 docker-compose로 4개 서비스가 한 번에 뜨는 단계라 리스크가 낮아, 구 포맷을
 * 허용하는 폴백 검증 로직은 일부러 추가하지 않았다 — "언젠가 지워야 할 구식 취약
 * 포맷 지원 코드"를 미리 넣으면, 나중에 지우는 걸 깜빡했을 때 오히려 취약점이 될
 * 수 있기 때문이다.
 *
 * 실제 순차/카나리 배포 절차를 도입할 때는 반드시 다음 중 하나를 지켜야 한다:
 * 1) 4개 서비스를 원자적으로(같은 배포 창 안에) 동시 배포하거나,
 * 2) 배포 파이프라인 자체에서 이 제약을 강제하거나,
 * 3) 그때 가서 이 클래스에 신/구 포맷을 둘 다 검증하는 임시 폴백을 추가하고,
 *    전체 롤아웃이 끝나면 반드시 그 폴백을 제거할 것(제거 시점을 이슈로 트래킹).
 */
public final class HmacSignatureUtil {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String DIGEST_ALGORITHM = "SHA-256";

    private HmacSignatureUtil() {
    }

    public static String sign(String serviceName, String method, String path, String normalizedQuery,
                              String bodyHash, String nonce, long timestampMillis, String secretKey) {
        String payload = serviceName + ":" + method + ":" + path + ":" + normalizedQuery + ":"
                + bodyHash + ":" + nonce + ":" + timestampMillis;
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

    /**
     * 쿼리스트링을 "키=값" 단위로 쪼개서 문자열 사전순 정렬 후 다시 합친다.
     * URL 디코딩은 하지 않는다 — 송신측(Feign)이 실제로 전송한 원문 그대로를
     * 수신측(HttpServletRequest.getQueryString())도 그대로 받으므로, 두 쪽이
     * 같은 원문 문자열을 기준으로 정렬하면 인코딩 방식 차이로 인한 불일치가 없다.
     */
    public static String normalizeQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return "";
        }
        String[] pairs = rawQuery.split("&");
        Arrays.sort(pairs);
        return String.join("&", pairs);
    }

    /** 상수시간 비교 — 타이밍 공격 방지. 반드시 이 메서드로만 서명을 비교할 것. */
    public static boolean verify(String serviceName, String method, String path, String normalizedQuery,
                                 String bodyHash, String nonce, long timestampMillis, String secretKey,
                                 String givenSignature) {
        String expected = sign(serviceName, method, path, normalizedQuery, bodyHash, nonce, timestampMillis, secretKey);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                givenSignature.getBytes(StandardCharsets.UTF_8)
        );
    }
}