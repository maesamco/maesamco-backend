package com.maesamco.content.presentation.response;

import com.maesamco.content.application.result.ProblemSearchResult;
import com.maesamco.content.domain.entity.ProgrammingLanguage;
import com.maesamco.content.domain.entity.problem.ProblemDifficulty;
import com.maesamco.content.domain.entity.problem.ProblemSource;
import com.maesamco.content.domain.entity.problem.ProblemStatus;
import com.maesamco.content.domain.entity.problem.ProblemType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

/**
 * 관리자 문제 검색 결과 항목입니다.
 *
 * <p>공개 문제 검색과 달리 관리자는 문제의 현재 상태를
 * 함께 확인할 수 있습니다.</p>
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AdminProblemSearchItemResponse {

    private final UUID id;
    private final String title;
    private final ProgrammingLanguage language;
    private final ProblemDifficulty difficulty;
    private final ProblemType type;
    private final ProblemSource source;
    private final ProblemStatus problemStatus;

    public static AdminProblemSearchItemResponse from(
            ProblemSearchResult problem
    ) {
        return new AdminProblemSearchItemResponse(
                problem.getId(),
                problem.getTitle(),
                problem.getLanguage(),
                problem.getDifficulty(),
                problem.getType(),
                problem.getSource(),
                problem.getProblemStatus()
        );
    }
}
