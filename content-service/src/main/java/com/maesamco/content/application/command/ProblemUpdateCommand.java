package com.maesamco.content.application.command;

import com.maesamco.content.domain.entity.ProgrammingLanguage;
import com.maesamco.content.domain.entity.problem.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.openapitools.jackson.nullable.JsonNullable;

@Getter
@AllArgsConstructor
public class ProblemUpdateCommand {

    private String title;

    private Long lockVersion;

    private ProgrammingLanguage language;

    private ProblemDifficulty difficulty;

    private ProblemType type;

    private String description;

    private JsonNullable<String> starterCode;

    private RunningTimeLimit runningTimeLimit;

    private RunningMemoryLimit runningMemoryLimit;

    private TimerPolicy timerPolicy;

    private ProblemSource source;
}