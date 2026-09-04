package com.maesamco.content.problem.presentation.dto.request;

import com.maesamco.content.problem.domain.enums.ProblemDifficulty;
import com.maesamco.content.problem.domain.enums.ProblemSource;
import com.maesamco.content.problem.domain.enums.ProblemType;
import com.maesamco.content.problem.domain.enums.ProgrammingLanguage;
import com.maesamco.content.problem.domain.enums.RunningMemoryLimit;
import com.maesamco.content.problem.domain.enums.RunningTimeLimit;
import com.maesamco.content.problem.domain.enums.TimerPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 문제 생성 요청 정보를 전달합니다.
 *
 * <p>문제 상태와 현재 버전 번호는 클라이언트로부터 입력받지 않고,
 * 문제 생성 시 서버에서 기본값을 설정합니다.</p>
 */
@Getter
@NoArgsConstructor
public class ProblemCreateRequest {

    /** 문제 제목 */
    @NotBlank
    @Size(max = 100)
    private String title;

    /** 문제 언어 */
    @NotNull
    private ProgrammingLanguage language;

    /** 문제 난이도 */
    @NotNull
    private ProblemDifficulty difficulty;

    /** 문제 유형 */
    @NotNull
    private ProblemType type;

    /** 문제 설명 */
    @NotBlank
    private String description;

    /** 문제 풀이 시작 코드 */
    private String starterCode;

    /** 코드 실행 시간 제한 */
    @NotNull
    private RunningTimeLimit runningTimeLimit;

    /** 코드 실행 메모리 제한 */
    @NotNull
    private RunningMemoryLimit runningMemoryLimit;

    /** 문제 타이머 정책 */
    @NotNull
    private TimerPolicy timerPolicy;

    /** 문제 출처 */
    @NotNull
    private ProblemSource source;
}