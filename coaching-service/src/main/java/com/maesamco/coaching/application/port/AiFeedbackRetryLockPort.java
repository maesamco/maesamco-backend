package com.maesamco.coaching.application.port;

import java.util.UUID;

/**
 * 코칭 세션 단위로 AI 피드백 재시도(카운트 체크~LLM 호출~이력 저장)를 상호 배제하는 락을
 * 추상화하는 포트 — HintGenerationLockPort와 동일한 이유(팀 컨벤션 "포트-어댑터 구조").
 *
 * 재검증(PR #111, 외부 AI 리뷰) — AiFeedbackRetryFacade.retryFeedback()은 재시도 횟수
 * 체크부터 LLM 호출·이력 저장까지 락 없는 check-then-act였다. p_ai_call_histories엔
 * (coaching_session_id, purpose) 유니크 제약이 없고(세션당 여러 AI 호출이 의도적으로
 * 허용됨), Judge Service Feign 호출엔 타임아웃 설정이 없으며, 게이트웨이 RateLimitFilter는
 * 고정 윈도 카운터라 동시성 제한이 아니다 — 이 조합 때문에 같은 60초 창 안에서 동시에
 * 여러 요청이 들어오면 전부 같은(스테일) 카운트를 읽고 통과해서, "최대 3회"라는 설계
 * 의도를 반복적으로 넘는 LLM 호출을 유발할 수 있었다. 이 락으로 그 구간 전체를 세션
 * 단위로 직렬화한다.
 */
public interface AiFeedbackRetryLockPort {

    /**
     * @param coachingSessionId 락을 걸 대상 세션
     * @param lockToken 이 요청이 건 락임을 식별하는 토큰(unlock 시 본인 락인지 확인용)
     * @return 락을 획득했으면(또는 락 메커니즘 장애로 락 없이 진행하기로 했으면) true,
     *         다른 요청이 이미 이 세션의 피드백 재시도를 처리 중이면 false
     */
    boolean tryLock(UUID coachingSessionId, String lockToken);

    /**
     * @param coachingSessionId 락을 해제할 대상 세션
     * @param lockToken tryLock()에 넘겼던 것과 동일한 토큰 — 다른 요청이 이미 잡은 락을
     *                   실수로 해제하지 않기 위한 안전장치
     */
    void unlock(UUID coachingSessionId, String lockToken);
}
