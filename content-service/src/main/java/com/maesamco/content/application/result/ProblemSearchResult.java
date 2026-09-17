package com.maesamco.content.application.result;

import com.maesamco.content.domain.entity.ProgrammingLanguage;
import com.maesamco.content.domain.entity.problem.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ProblemSearchResult {

    private final UUID id;
    private final String title;
    private final ProgrammingLanguage language;
    private final ProblemDifficulty difficulty;
    private final ProblemType type;
    private final ProblemSource source;
    private final ProblemStatus problemStatus;

    public static ProblemSearchResult from(Problem problem) {
        return new ProblemSearchResult(
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