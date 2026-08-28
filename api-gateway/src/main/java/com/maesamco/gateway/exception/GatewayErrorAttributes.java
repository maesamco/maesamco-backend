package com.maesamco.gateway.exception;

import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.reactive.error.DefaultErrorAttributes;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Gateway에서 발생하는 모든 에러(401/403/429/503 등)를 서비스 내부의
 * ErrorResponse 포맷과 동일하게 맞춘다(게이트웨이 및 인증 보안 설계 9절, 팀 컨벤션 11절).
 *
 * { "success": false, "error": {"code": "...", "message": "..."}, "timestamp": "..." }
 */
@Component
public class GatewayErrorAttributes extends DefaultErrorAttributes {

    @Override
    public Map<String, Object> getErrorAttributes(ServerRequest request, ErrorAttributeOptions options) {
        Map<String, Object> original = super.getErrorAttributes(request, options);
        int status = (int) original.getOrDefault("status", 500);

        Map<String, Object> errorDetail = new LinkedHashMap<>();
        errorDetail.put("code", codeFor(status));
        errorDetail.put("message", messageFor(status, original));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("error", errorDetail);
        body.put("timestamp", LocalDateTime.now().toString());
        return body;
    }

    private String codeFor(int status) {
        return switch (HttpStatus.valueOf(status)) {
            case UNAUTHORIZED -> "AUTH_UNAUTHORIZED";
            case FORBIDDEN -> "AUTH_ACCESS_DENIED";
            case TOO_MANY_REQUESTS -> "RATE_LIMIT_EXCEEDED";
            case SERVICE_UNAVAILABLE -> "SERVICE_UNAVAILABLE";
            case NOT_FOUND -> "ENTITY_NOT_FOUND";
            default -> "INTERNAL_SERVER_ERROR";
        };
    }

    private String messageFor(int status, Map<String, Object> original) {
        return switch (HttpStatus.valueOf(status)) {
            case UNAUTHORIZED -> "인증이 필요합니다.";
            case FORBIDDEN -> "접근 권한이 없습니다.";
            case TOO_MANY_REQUESTS -> "요청이 너무 많습니다. 잠시 후 다시 시도해주세요.";
            case SERVICE_UNAVAILABLE -> "일시적으로 서비스를 이용할 수 없습니다.";
            case NOT_FOUND -> "요청한 리소스를 찾을 수 없습니다.";
            default -> "서버 내부 오류가 발생했습니다.";
        };
    }
}
