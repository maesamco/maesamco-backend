package com.maesamco.content.presentation.request;

import com.maesamco.content.application.command.ProblemUpdateCommand;
import com.maesamco.content.application.command.UpdateField;
import com.maesamco.content.domain.entity.ProgrammingLanguage;
import com.maesamco.content.domain.entity.problem.ProblemDifficulty;
import com.maesamco.content.domain.entity.problem.ProblemSource;
import com.maesamco.content.domain.entity.problem.ProblemType;
import com.maesamco.content.domain.entity.problem.RunningMemoryLimit;
import com.maesamco.content.domain.entity.problem.RunningTimeLimit;
import com.maesamco.content.domain.entity.problem.TimerPolicy;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.openapitools.jackson.nullable.JsonNullable;

import java.util.UUID;

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
    @Pattern(
            regexp = "(?s).*\\S.*",
            message = "문제 제목은 공백일 수 없습니다."
    )
    @Size(
            max = 100,
            message = "문제 제목은 100자 이하여야 합니다."
    )
    private String title;

    /** 관리자가 조회했을 당시의 JPA 낙관적 락 버전입니다. */
    @NotNull(message = "lockVersion은 필수입니다.")
    private Long lockVersion;

    /** 수정할 문제 언어입니다. */
    private ProgrammingLanguage language;

    /** 수정할 문제 난이도입니다. */
    private ProblemDifficulty difficulty;

    /** 수정할 문제 유형입니다. */
    private ProblemType type;

    /** 수정할 문제 설명입니다. */
    @Size(max = 10_000, message = "문제 설명은 최대 10,000자까지 입력할 수 있습니다.")
    private String description;

    /** 수정할 문제 풀이 시작 코드입니다. */
    private JsonNullable<@Size(max = 10_000, message = "스타터 코드는 최대 10,000자까지 입력할 수 있습니다.") String>
            starterCode = JsonNullable.undefined();

    /** 수정할 코드 실행 시간 제한입니다. */
    private RunningTimeLimit runningTimeLimit;

    /** 수정할 코드 실행 메모리 제한입니다. */
    private RunningMemoryLimit runningMemoryLimit;

    /** 수정할 문제 풀이 타이머 정책입니다. */
    private TimerPolicy timerPolicy;

    /** 수정할 문제 출처입니다. */
    private ProblemSource source;

    /**
     * 연결할 레슨 ID입니다(이슈 #291). 필드 자체가 요청에 없으면 기존 연결을
     * 유지하고, 명시적으로 null을 보내면 레슨 연결을 해제합니다.
     */
    private JsonNullable<UUID> lessonId = JsonNullable.undefined();

    public ProblemUpdateCommand toCommand() {
        return new ProblemUpdateCommand(
                title,
                lockVersion,
                language,
                difficulty,
                type,
                description,
                toUpdateField(starterCode),
                runningTimeLimit,
                runningMemoryLimit,
                timerPolicy,
                source,
                toUpdateField(lessonId)
        );
    }

    private <T> UpdateField<T> toUpdateField(JsonNullable<T> field) {

        if (field == null || !field.isPresent()) {
            return UpdateField.undefined();
        }

        return UpdateField.of(
                field.orElse(null)
        );
    }
}
