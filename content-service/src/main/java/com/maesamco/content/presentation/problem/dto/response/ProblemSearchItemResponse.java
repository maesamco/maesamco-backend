package com.maesamco.content.problem.presentation.dto.response;

import com.maesamco.content.problem.domain.entity.Problem;
import com.maesamco.content.problem.domain.enums.ProblemDifficulty;
import com.maesamco.content.problem.domain.enums.ProblemSource;
import com.maesamco.content.problem.domain.enums.ProblemStatus;
import com.maesamco.content.problem.domain.enums.ProblemType;
import com.maesamco.content.problem.domain.enums.ProgrammingLanguage;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

/**
 * 문제 검색 결과 항목 응답 DTO
 * <p>[문제 카드에 보여줄 정보]</p>
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ProblemSearchItemResponse {

    private final UUID id;
    private final String title;
    private final ProgrammingLanguage language;
    private final ProblemDifficulty difficulty;
    private final ProblemType type;
    private final ProblemSource source;

    public static ProblemSearchItemResponse from(Problem problem) {
        return new ProblemSearchItemResponse(
                problem.getId(),
                problem.getTitle(),
                problem.getLanguage(),
                problem.getDifficulty(),
                problem.getType(),
                problem.getSource()
        );
    }
}