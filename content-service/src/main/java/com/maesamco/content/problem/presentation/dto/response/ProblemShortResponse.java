
package com.maesamco.content.problem.presentation.dto.response;

import com.maesamco.content.problem.domain.entity.Problem;
import com.maesamco.content.problem.domain.enums.*;
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

    public static ProblemShortResponse from(Problem problem) {
        return new ProblemShortResponse(
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
                problem.getSource()
        );
    }
}