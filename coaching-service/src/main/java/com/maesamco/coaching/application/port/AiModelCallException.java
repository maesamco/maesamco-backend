package com.maesamco.coaching.application.port;

/**
 * LLM 호출 실패·타임아웃 — AiModelPort 구현체(어댑터)가 던지고, Facade가 잡아서
 * AiCallHistory 실패 기록 후 BusinessException(AI_GENERATION_FAILED)으로 변환한다.
 */
public class AiModelCallException extends RuntimeException {

    /**
     * 외부 AI 리뷰 지적(PR #111 재검증) — 서킷브레이커가 OPEN이라 실제 LLM 호출 자체가
     * 차단된 경우(CallNotPermittedException)와, 서킷은 닫혀 있었지만 실제로 호출했다가
     * 실패한 경우를 구분한다. 힌트/역질문/피드백 세 기능이 서킷(ai-model)을 공유하다 보니,
     * 구분 없이 둘 다 "FAILED"로 기록하면 무관한 기능의 장애로 서킷이 열렸을 때 피드백
     * 재시도(AiFeedbackRetryFacade)가 실제 LLM 호출 없이도 3회 재시도 예산을 소모하게 된다.
     */
    private final boolean circuitOpen;

    public AiModelCallException(String message, Throwable cause) {
        this(message, cause, false);
    }

    public AiModelCallException(String message, Throwable cause, boolean circuitOpen) {
        super(message, cause);
        this.circuitOpen = circuitOpen;
    }

    public boolean isCircuitOpen() {
        return circuitOpen;
    }
}
