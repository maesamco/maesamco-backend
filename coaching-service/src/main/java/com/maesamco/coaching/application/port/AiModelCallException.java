package com.maesamco.coaching.application.port;

/**
 * LLM 호출 실패·타임아웃 — AiModelPort 구현체(어댑터)가 던지고, Facade가 잡아서
 * AiCallHistory 실패 기록 후 BusinessException(AI_GENERATION_FAILED)으로 변환한다.
 */
public class AiModelCallException extends RuntimeException {

    public AiModelCallException(String message, Throwable cause) {
        super(message, cause);
    }
}
