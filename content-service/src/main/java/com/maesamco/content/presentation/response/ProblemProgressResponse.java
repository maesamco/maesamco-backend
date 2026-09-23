package com.maesamco.content.presentation.response;

import com.maesamco.content.domain.entity.problem.ProblemProgress;
import com.maesamco.content.domain.entity.problem.ProblemProgressStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * 사용자의 문제 풀이 진행 상태 응답입니다.
 */
@Getter
@Builder
public class ProblemProgressResponse {

    /** 문제의 고유 ID입니다. */
    private UUID problemId;

    /** 해당 풀이가 기준으로 하는 문제 버전 번호입니다. */
    private Integer versionNo;

    /** 현재 진행 상태에 반영된 제출 시도 번호입니다. */
    private Integer attemptNo;

    /** 문제 풀이 상태입니다. */
    private ProblemProgressStatus progressStatus;

    /** 문제를 정답 처리한 시각입니다. */
    private Instant solvedAt;

    /** 문제 풀이 진행 정보가 최초 생성된 시각입니다. */
    private Instant createdAt;

    public static ProblemProgressResponse from(ProblemProgress problemProgress) {
        return ProblemProgressResponse.builder()
                .problemId(problemProgress.getProblemId())
                .versionNo(problemProgress.getVersionNo())
                .attemptNo(problemProgress.getAttemptNo())
                .progressStatus(problemProgress.getProgressStatus())
                .solvedAt(problemProgress.getSolvedAt())
                .createdAt(problemProgress.getCreatedAt())
                .build();
    }
}