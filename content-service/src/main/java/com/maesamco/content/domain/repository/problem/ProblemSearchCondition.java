package com.maesamco.content.domain.repository.problem;

import com.maesamco.content.domain.entity.ProgrammingLanguage;
import com.maesamco.content.domain.entity.problem.ProblemDifficulty;
import com.maesamco.content.domain.entity.problem.ProblemSource;
import com.maesamco.content.domain.entity.problem.ProblemStatus;
import com.maesamco.content.domain.entity.problem.ProblemType;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 문제 Repository 검색 조건 */
@Getter
@AllArgsConstructor
public class ProblemSearchCondition {

    private final ProgrammingLanguage language;
    private final ProblemDifficulty difficulty;
    private final ProblemType type;
    private final ProblemSource source;
    private final ProblemStatus problemStatus;
}