package com.maesamco.coaching.presentation.api_controller;

import com.maesamco.coaching.domain.entity.FollowUpQuestion;

import java.util.UUID;

/**
 * 코칭 서비스 API 명세 3·4번 API 응답에 중첩되는 역질문 정보 — AI 역질문 생성이 실패한
 * 경우 null(등록 API 응답 설명 참고).
 */
public record FollowUpQuestionResponse(UUID followUpQuestionId, String questionText, String category) {

    public static FollowUpQuestionResponse from(FollowUpQuestion followUpQuestion) {
        return new FollowUpQuestionResponse(
                followUpQuestion.getId(), followUpQuestion.getQuestionText(), followUpQuestion.getCategory()
        );
    }
}
