package com.maesamco.content.presentation.response;

import com.maesamco.content.application.result.ProblemResult;
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

    public static ProblemCreateResponse from(ProblemResult result) {
        return new ProblemCreateResponse(
                result.getId(),
                result.getTitle()
        );
    }
}