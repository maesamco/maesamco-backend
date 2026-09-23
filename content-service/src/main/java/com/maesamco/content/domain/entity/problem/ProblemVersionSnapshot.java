package com.maesamco.content.domain.entity.problem;

import com.maesamco.content.domain.entity.ProgrammingLanguage;

import java.util.List;

/** 문제 버전의 타입이 지정된 스냅샷입니다. */
public record ProblemVersionSnapshot(
        String title,
        ProgrammingLanguage language,
        ProblemDifficulty difficulty,
        ProblemType type,
        String description,
        String starterCode,
        Integer runningTimeLimit,
        Integer runningMemoryLimit,
        TimerPolicy timerPolicy,
        ProblemSource source,
        List<ProblemVersionTestCaseItem> testCases
) {

    public ProblemVersionSnapshot {
        testCases = (testCases == null) ? List.of() : List.copyOf(testCases);
    }

    public static ProblemVersionSnapshot from(Problem problem, List<ProblemVersionTestCaseItem> testCases) {
        return new ProblemVersionSnapshot(
                problem.getTitle(),
                problem.getLanguage(),
                problem.getDifficulty(),
                problem.getType(),
                problem.getDescription(),
                problem.getStarterCode(),
                problem.getRunningTimeLimit().getSeconds(),
                problem.getRunningMemoryLimit().getMegabytes(),
                problem.getTimerPolicy(),
                problem.getSource(),
                testCases
        );
    }
}