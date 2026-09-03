package com.maesamco.coaching.application.port;

import java.util.UUID;

/**
 * Judge Service 조회를 추상화하는 포트 — 힌트·피드백 생성 Facade가 Feign/HMAC 서명 같은
 * 통신 방식을 몰라도 되게 한다(팀 컨벤션 2절 "포트-어댑터 구조").
 */
public interface JudgeServicePort {

    /**
     * @throws com.maesamco.coaching.global.exception.BusinessException
     *         존재하지 않는 제출이면 SUBMISSION_NOT_FOUND, 그 외 통신 실패면
     *         FEIGN_CLIENT_ERROR로 던진다.
     */
    SubmissionSnapshot getSubmission(UUID submissionId);
}
