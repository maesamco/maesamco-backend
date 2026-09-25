
package com.maesamco.content.presentation.response;

import com.maesamco.content.application.result.ProblemResult;
import com.maesamco.content.domain.entity.ProgrammingLanguage;
import com.maesamco.content.domain.entity.problem.ProblemDifficulty;
import com.maesamco.content.domain.entity.problem.ProblemSource;
import com.maesamco.content.domain.entity.problem.ProblemType;
import com.maesamco.content.domain.entity.problem.RunningMemoryLimit;
import com.maesamco.content.domain.entity.problem.RunningTimeLimit;
import com.maesamco.content.domain.entity.problem.TimerPolicy;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

/**
 * 문제 단건 조회 응답 DTO
 * <p>[문제 클릭했을 때 사용자에게 상세 화면에 보여줄 정보]</p>
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ProblemShortResponse {

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

    public static ProblemShortResponse from(ProblemResult result) {
        return new ProblemShortResponse(
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
                result.getSource()
        );
    }
}