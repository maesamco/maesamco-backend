package com.maesamco.content.problem.presentation.dto.response;

import com.maesamco.content.problem.domain.entity.Problem;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

/** 문제 생성 응답 DTO */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ProblemCreateResponse {

    private final UUID id;
    private final String title;

    public static ProblemCreateResponse from(Problem problem) {
        return new ProblemCreateResponse(
                problem.getId(),
                problem.getTitle()
        );
    }
}