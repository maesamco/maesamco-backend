package com.maesamco.content.problem.presentation.dto.request;

import org.openapitools.jackson.nullable.JsonNullable;
import com.maesamco.content.problem.domain.enums.ProblemDifficulty;
import com.maesamco.content.problem.domain.enums.ProblemSource;
import com.maesamco.content.problem.domain.enums.ProblemStatus;
import com.maesamco.content.problem.domain.enums.ProblemType;
import com.maesamco.content.problem.domain.enums.ProgrammingLanguage;
import com.maesamco.content.problem.domain.enums.RunningMemoryLimit;
import com.maesamco.content.problem.domain.enums.RunningTimeLimit;
import com.maesamco.content.problem.domain.enums.TimerPolicy;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 문제 수정 요청 정보를 전달합니다.
 *
 * <p>PATCH 요청이므로 전달되지 않은 필드는 기존 값을 유지하고,
 * 전달된 필드만 수정합니다.</p>
 *
 * <p>최신 문제 버전 번호는 문제 버전 관리 로직에서 처리하므로
 * 수정 요청으로 직접 전달받지 않습니다.</p>
 */
@Getter
@NoArgsConstructor
public class ProblemUpdateRequest {

    /** 수정할 문제 제목입니다. */
    @Size(max = 100)
    private String title;

    /** 수정할 문제 언어입니다. */
    private ProgrammingLanguage language;

    /** 수정할 문제 난이도입니다. */
    private ProblemDifficulty difficulty;

    /** 수정할 문제 유형입니다. */
    private ProblemType type;

    /** 수정할 문제 설명입니다. */
    private String description;

    /** 수정할 문제 풀이 시작 코드입니다. */
    private JsonNullable<@Size(max = 10_000, message = "스타터 코드는 최대 10,000자까지 입력할 수 있습니다.")String>
            starterCode = JsonNullable.undefined();

    /** 수정할 코드 실행 시간 제한입니다. */
    private RunningTimeLimit runningTimeLimit;

    /** 수정할 코드 실행 메모리 제한입니다. */
    private RunningMemoryLimit runningMemoryLimit;

    /** 수정할 문제 풀이 타이머 정책입니다. */
    private TimerPolicy timerPolicy;

    /** 수정할 문제 출처입니다. */
    private ProblemSource source;
}