package com.maesamco.coaching.infrastructure.feign;

import java.util.List;
import java.util.UUID;

/**
 * Content Service GET /internal/v1/problems/{problemId} 응답 바디(data 부분)를 그대로
 * 옮긴 DTO. Content Service의 실제 InternalProblemResponse(PR #121, 2026-09-08 코드로
 * 직접 확인)를 기준으로 필드명을 맞췄다 — 문제 식별자 필드명이 "problemId"가 아니라
 * "id"인 점에 주의(당초 요청 문서 초안은 problemId였으나 실제 구현은 id로 나왔다).
 * conceptTags는 태그가 없는 문제면 빈 배열로 온다(에러 아님, 준영님 확인).
 */
public record ProblemDetailResponse(
        UUID id,
        String description,
        List<String> conceptTags
) {
}
