package com.maesamco.coaching.application.port;

/**
 * LLM 호출 실패·타임아웃 — AiModelPort 구현체(어댑터)가 던지고, Facade가 잡아서
 * AiCallHistory 실패 기록 후 BusinessException(AI_GENERATION_FAILED)으로 변환한다.
 *
 * 이슈 #173 정리 — 재시도 예산 계산(AiFeedbackRetryFacade)은 이 예외로 들어오는 모든
 * 경우(quota 소진·네트워크 오류·서킷오픈 등)를 원인과 무관하게 똑같이 재시도 예산에서
 * 제외한다 — 토큰이 청구되지 않은 시도이기 때문이다.
 *
 * PR #182 리뷰(용현님 P2) — 다만 예산 정책과 별개로, AiCallHistory.requestStatus(운영
 * 이력 조회용)까지 이 두 경우를 똑같이 "SKIPPED"로 뭉개면 "실제 외부 호출 자체가 없었던
 * 시도"와 "호출은 시도했지만 인프라에서 실패한 시도"를 사후에 구분할 수 없게 된다.
 * neverCalled()로 이 둘을 구분해서, Facade가 전자는 "SKIPPED", 후자는 "INFRA_FAILED"로
 * 기록한다 — 재시도 예산 제외 대상(둘 다 제외)에는 영향 없다. 기본값은 false(실제
 * 시도가 있었다고 가정) — 서킷오픈으로 chatModel.call() 자체가 실행되지 않은 경우에만
 * 어댑터가 명시적으로 true를 넘긴다.
 */
public class AiModelCallException extends RuntimeException {

    private final boolean neverCalled;

    public AiModelCallException(String message, Throwable cause) {
        this(message, cause, false);
    }

    public AiModelCallException(String message, Throwable cause, boolean neverCalled) {
        super(message, cause);
        this.neverCalled = neverCalled;
    }

    public boolean neverCalled() {
        return neverCalled;
    }
}
