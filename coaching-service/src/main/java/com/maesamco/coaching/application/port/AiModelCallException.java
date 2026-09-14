package com.maesamco.coaching.application.port;

/**
 * LLM 호출 실패·타임아웃 — AiModelPort 구현체(어댑터)가 던지고, Facade가 잡아서
 * AiCallHistory 실패 기록 후 BusinessException(AI_GENERATION_FAILED)으로 변환한다.
 *
 * 이슈 #173 정리 — 예전엔 서킷브레이커가 OPEN이라 호출 자체가 차단된 경우
 * (CallNotPermittedException)만 circuitOpen=true로 구분해서 AiFeedbackRetryFacade의
 * 재시도 예산 계산에서 제외했는데, 재검토 결과 이 예외로 들어오는 모든 경우(quota
 * 소진·네트워크 오류 등)가 전부 "호출 자체가 실패해 토큰이 청구되지 않은 시도"라
 * 원인과 무관하게 똑같이 재시도 예산에서 제외해야 한다는 결론으로 바뀌었다 — 그래서
 * circuitOpen 구분 자체가 불필요해져 제거했다.
 */
public class AiModelCallException extends RuntimeException {

    public AiModelCallException(String message, Throwable cause) {
        super(message, cause);
    }
}
