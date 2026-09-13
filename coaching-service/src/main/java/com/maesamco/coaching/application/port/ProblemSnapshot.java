package com.maesamco.coaching.application.port;

import java.util.List;
import java.util.UUID;

/**
 * Content Service GET /internal/v1/problems/{problemId} 조회 결과를 도메인 계층에서
 * 쓰기 좋은 형태로 옮겨온 값 — SubmissionSnapshot과 동일한 이유(Feign 원본 응답 DTO인
 * infrastructure/feign/ProblemDetailResponse와 필드가 겹치지만, 이 계층은 Content
 * Service의 JSON 응답 구조가 바뀌어도 영향받지 않는다).
 *
 * Content Service의 실제 응답 필드는 id/description/conceptTags 세 개뿐이다(2026-09-08
 * 기준 PR #121 코드로 직접 확인 — 나머지 필드는 전부 주석 처리돼 있어 필요해지면 그때
 * 추가된다). problemId로 이름을 바꾼 이유는 SubmissionSnapshot과의 일관성 때문이다.
 */
public record ProblemSnapshot(
        UUID problemId,
        String description,
        List<String> conceptTags
) {
}
