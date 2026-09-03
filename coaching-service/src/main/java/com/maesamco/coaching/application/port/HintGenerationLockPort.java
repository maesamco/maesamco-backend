package com.maesamco.coaching.application.port;

import java.util.UUID;

/**
 * 코칭 세션 단위로 힌트 생성(LLM 호출)을 상호 배제하는 락을 추상화하는 포트 — Facade가
 * 구체 기술(Redis 등)을 몰라도 되게 한다(팀 컨벤션 "포트-어댑터 구조"). 동시 요청 두 개가
 * 같은 stage를 계산해 LLM을 각각 호출하면 실제 과금이 두 번 발생하는 문제(PR #70 리뷰,
 * 비용/어뷰징 관점)를 막기 위해, LLM 호출 전에 이 락으로 먼저 직렬화한다.
 */
public interface HintGenerationLockPort {

    /**
     * @param coachingSessionId 락을 걸 대상 세션
     * @param lockToken 이 요청이 건 락임을 식별하는 토큰(unlock 시 본인 락인지 확인용)
     * @return 락을 획득했으면(또는 락 메커니즘 장애로 락 없이 진행하기로 했으면) true,
     *         다른 요청이 이미 이 세션의 힌트를 생성 중이면 false
     */
    boolean tryLock(UUID coachingSessionId, String lockToken);

    /**
     * @param coachingSessionId 락을 해제할 대상 세션
     * @param lockToken tryLock()에 넘겼던 것과 동일한 토큰 — 다른 요청이 이미 잡은 락을
     *                   실수로 해제하지 않기 위한 안전장치
     */
    void unlock(UUID coachingSessionId, String lockToken);
}
