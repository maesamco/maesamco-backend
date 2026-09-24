package com.maesamco.content.presentation.response;

import com.maesamco.content.domain.entity.problem.ProblemVersion;
import com.maesamco.content.domain.entity.problem.ProblemVersionSnapshot;

import java.time.Instant;
import java.util.UUID;

/**
 * 관리자 문제 버전 조회 응답입니다.
 *
 * <p>스냅샷에는 발행 시점의 테스트케이스가 포함될 수 있으므로
 * 관리자 API에서만 반환합니다.</p>
 */
public record ProblemVersionResponse(
        UUID id,
        UUID problemId,
        Integer versionNo,
        Instant publishedAt,
        ProblemVersionSnapshot snapshot
) {

    public static ProblemVersionResponse from(
            ProblemVersion problemVersion
    ) {
        return new ProblemVersionResponse(
                problemVersion.getId(),
                problemVersion.getProblemId(),
                problemVersion.getVersionNo(),
                problemVersion.getPublishedAt(),
                problemVersion.toVersionSnapshot()
        );
    }
}
