package com.maesamco.coaching.application.port;

import java.util.UUID;

/**
 * Content Service 조회를 추상화하는 포트 — 힌트·설명·피드백 생성 Facade가 Feign/HMAC
 * 서명 같은 통신 방식을 몰라도 되게 한다(JudgeServicePort와 동일한 이유, 팀 컨벤션 2절
 * "포트-어댑터 구조"). 이슈 #62.
 */
public interface ContentServicePort {

    /**
     * 제출 시점 문제 버전 기준으로 지문을 조회한다(이슈 #172, #178) — 문제가 수정된 뒤
     * 과거 제출로 힌트·역질문·피드백을 요청해도 항상 제출 당시 버전의 지문이 프롬프트에
     * 들어가도록, problemId가 아니라 problemVersionId로 조회한다. 개념 태그는 버전
     * 스냅샷에 없어 problemId 기준 현재 태그가 그대로 온다(Content Service 쪽 한계).
     *
     * @throws com.maesamco.coaching.global.exception.BusinessException
     *         존재하지 않는 문제 버전이면 PROBLEM_NOT_FOUND, 그 외 통신 실패면
     *         FEIGN_CLIENT_ERROR로 던진다.
     */
    ProblemSnapshot getProblemVersion(UUID problemVersionId);
}
