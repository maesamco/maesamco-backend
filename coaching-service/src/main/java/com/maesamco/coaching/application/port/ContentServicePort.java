package com.maesamco.coaching.application.port;

import java.util.UUID;

/**
 * Content Service 조회를 추상화하는 포트 — 힌트·설명·피드백 생성 Facade가 Feign/HMAC
 * 서명 같은 통신 방식을 몰라도 되게 한다(JudgeServicePort와 동일한 이유, 팀 컨벤션 2절
 * "포트-어댑터 구조"). 이슈 #62.
 */
public interface ContentServicePort {

    /**
     * @throws com.maesamco.coaching.global.exception.BusinessException
     *         존재하지 않는 문제면 PROBLEM_NOT_FOUND, 그 외 통신 실패면
     *         FEIGN_CLIENT_ERROR로 던진다.
     */
    ProblemSnapshot getProblem(UUID problemId);
}
