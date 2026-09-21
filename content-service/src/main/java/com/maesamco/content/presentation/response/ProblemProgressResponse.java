package com.maesamco.content.presentation.response;

import com.maesamco.content.domain.entity.problem.ProblemProgress;
import com.maesamco.content.domain.entity.problem.ProblemProgressStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class ProblemProgressResponse {

    private UUID problemId;

    private Integer versionNo;

    private ProblemProgressStatus progressStatus;

    private Instant solvedAt;

    private Instant createdAt;

    public static ProblemProgressResponse from(ProblemProgress problemProgress) {
        return ProblemProgressResponse.builder()
                .problemId(problemProgress.getProblemId())
                .versionNo(problemProgress.getVersionNo())
                .progressStatus(problemProgress.getProgressStatus())
                .solvedAt(problemProgress.getSolvedAt())
                .createdAt(problemProgress.getCreatedAt())
                .build();
    }
}