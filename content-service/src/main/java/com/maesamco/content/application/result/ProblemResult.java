package com.maesamco.content.application.result;

import com.maesamco.content.domain.entity.ProgrammingLanguage;
import com.maesamco.content.domain.entity.problem.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ProblemResult {

    private final UUID id;
    private final String title;
    private final ProgrammingLanguage language;
    private final ProblemDifficulty difficulty;
    private final ProblemType type;
    private final String description;
    private final String starterCode;
    private final RunningTimeLimit runningTimeLimit;
    private final RunningMemoryLimit runningMemoryLimit;
    private final TimerPolicy timerPolicy;
    private final ProblemSource source;
    private final ProblemStatus problemStatus;
    private final Integer currentVersionNo;
    private final Long lockVersion;
    /** 연결된 레슨 ID(이슈 #291). 아직 레슨에 배정되지 않았으면 null. */
    private final UUID lessonId;

    public static ProblemResult from(Problem problem) {
        return new ProblemResult(
                problem.getId(),
                problem.getTitle(),
                problem.getLanguage(),
                problem.getDifficulty(),
                problem.getType(),
                problem.getDescription(),
                problem.getStarterCode(),
                problem.getRunningTimeLimit(),
                problem.getRunningMemoryLimit(),
                problem.getTimerPolicy(),
                problem.getSource(),
                problem.getProblemStatus(),
                problem.getCurrentVersionNo(),
                problem.getLockVersion(),
                problem.getLessonId()
        );
    }
}