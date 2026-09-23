package com.maesamco.content.application.command;

import com.maesamco.content.domain.entity.ProgrammingLanguage;
import com.maesamco.content.domain.entity.problem.*;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class ProblemUpdateCommand {

    private String title;

    private Long lockVersion;

    private ProgrammingLanguage language;

    private ProblemDifficulty difficulty;

    private ProblemType type;

    private String description;

    private UpdateField<String> starterCode; // private JsonNullable<String> starterCode;

    private RunningTimeLimit runningTimeLimit;

    private RunningMemoryLimit runningMemoryLimit;

    private TimerPolicy timerPolicy;

    private ProblemSource source;

    /**
     * 연결할 레슨 ID(이슈 #291). starterCode와 동일한 3단계 PATCH 규약:
     * 필드 자체가 요청에 없으면(undefined) 기존 연결 유지, 명시적으로
     * null이 오면 레슨 연결 해제, 값이 오면 그 레슨으로 연결(변경).
     */
    private UpdateField<UUID> lessonId;
}