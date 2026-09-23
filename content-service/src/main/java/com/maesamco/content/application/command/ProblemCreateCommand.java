package com.maesamco.content.application.command;

import com.maesamco.content.domain.entity.ProgrammingLanguage;
import com.maesamco.content.domain.entity.problem.*;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class ProblemCreateCommand {

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
    /** 연결할 레슨 ID(이슈 #291). 아직 레슨에 배정하지 않으려면 null. */
    private UUID lessonId;
}