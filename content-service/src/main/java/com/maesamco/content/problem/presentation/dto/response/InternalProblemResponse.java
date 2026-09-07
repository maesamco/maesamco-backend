package com.maesamco.content.problem.presentation.dto.response;

import com.maesamco.content.problem.domain.entity.Problem;
import com.maesamco.content.problem.domain.enums.*;
import com.maesamco.content.tag.domain.entity.Tag;
import com.maesamco.content.tag.domain.enums.TagAttribute;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 내부 서비스용 문제 조회 응답 DTO */
@Getter
@AllArgsConstructor
public class InternalProblemResponse {

    // 필요한 부분 있으면 가져다가 쓰기
    private final UUID id;
    // private final String title;
    // private final ProgrammingLanguage language;
    // private final ProblemDifficulty difficulty;
    // private final ProblemType type;
    private final String description;
    // private final String starterCode;
    // private final Integer runningTimeLimit;
    // private final Integer runningMemoryLimit;
    // private final TimerPolicy timerPolicy;
    // private final ProblemSource source;
    // private final ProblemStatus problemStatus;
    // private final Integer currentVersionNo;

    private List<String> conceptTags;

    /** 문제와 태그 정보를 내부 서비스용 응답 DTO로 변환한다. */
    public static InternalProblemResponse from(Problem problem, List<Tag> tags) {
        List<String> conceptTags = new ArrayList<>();
        for (Tag t : tags) {
            if (t.getAttribute() == TagAttribute.CONCEPT)
                conceptTags.add(t.getName());
        }

        return new InternalProblemResponse(
                problem.getId(),
                // problem.getTitle(),
                // problem.getLanguage(),
                // problem.getDifficulty(),
                // problem.getType(),
                problem.getDescription(),
                // problem.getStarterCode(),
                // problem.getRunningTimeLimit(),
                // problem.getRunningMemoryLimit(),
                // problem.getTimerPolicy(),
                // problem.getSource(),
                // problem.getProblemStatus(),
                // problem.getCurrentVersionNo(),

                conceptTags
        );
    }
}