package com.maesamco.content.application.result;

import com.maesamco.content.domain.entity.ProgrammingLanguage;
import com.maesamco.content.domain.entity.problem.*;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class ProblemResult {

    private UUID id;
    private String title;
    private ProgrammingLanguage language;
    private ProblemDifficulty difficulty;
    private ProblemType type;
    private String description;
    private String starterCode;
    private RunningTimeLimit runningTimeLimit;
    private RunningMemoryLimit runningMemoryLimit;
    private TimerPolicy timerPolicy;
    private ProblemSource source;
    private ProblemStatus status;
    private Integer currentVersionNo;
    private Long lockVersion;

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
                problem.getLockVersion()
        );
    }
}