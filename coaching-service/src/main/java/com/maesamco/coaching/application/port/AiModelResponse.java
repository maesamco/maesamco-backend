package com.maesamco.coaching.application.port;

/**
 * AiModelPort 호출 결과 — AiCallHistory에 기록할 modelName/tokenUsage를 함께 담는다
 * (매삼코 DB 테이블 명세 p_ai_call_histories 참고). tokenUsage는 벤더가 제공하지 않으면 null.
 */
public record AiModelResponse(String content, String modelName, Integer tokenUsage) {
}
