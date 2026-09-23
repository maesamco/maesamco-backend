package com.maesamco.content.presentation.response;

import com.maesamco.content.application.result.ProblemResult;
import com.maesamco.content.domain.entity.ProgrammingLanguage;
import com.maesamco.content.domain.entity.problem.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

/**
 * 문제 단건 조회 응답 DTO
 * <p>[문제 클릭했을 때 관리자가 상세 화면에 보여줄 정보]</p>
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ProblemResponse {

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

    public static ProblemResponse from(ProblemResult result) {
        return new ProblemResponse(
                result.getId(),
                result.getTitle(),
                result.getLanguage(),
                result.getDifficulty(),
                result.getType(),
                result.getDescription(),
                result.getStarterCode(),
                result.getRunningTimeLimit(),
                result.getRunningMemoryLimit(),
                result.getTimerPolicy(),
                result.getSource(),
                result.getProblemStatus(),
                result.getCurrentVersionNo(),
                result.getLockVersion(),
                result.getLessonId()
        );
    }
}
