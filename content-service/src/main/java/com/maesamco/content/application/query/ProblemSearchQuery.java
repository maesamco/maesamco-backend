package com.maesamco.content.application.query;

import com.maesamco.content.domain.entity.ProgrammingLanguage;
import com.maesamco.content.domain.entity.problem.*;
import com.maesamco.content.domain.repository.problem.ProblemSearchCondition;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class ProblemSearchQuery {

    private ProgrammingLanguage language;
    private ProblemDifficulty difficulty;
    private ProblemType type;
    private ProblemStatus problemStatus;
    private ProblemSource source;
    private UUID lessonId;

    public void forcePublished() {
        this.problemStatus = ProblemStatus.PUBLISHED;
    }

    public ProblemSearchCondition toCondition() {
        return new ProblemSearchCondition(
                language,
                difficulty,
                type,
                source,
                problemStatus,
                lessonId
        );
    }
}