package com.maesamco.content.domain.entity.problem;

import com.maesamco.content.domain.entity.ProgrammingLanguage;
import com.maesamco.content.global.common.BaseEntity;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** 문제 객체 */

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "p_problems")
public class Problem extends BaseEntity {

    /** {@code title} 컬럼 길이(V1 스키마)와 같습니다. */
    private static final int TITLE_MAX_LENGTH = 100;


    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "language", nullable = false, length = 20)
    private ProgrammingLanguage language;

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty", nullable = false, length = 20)
    private ProblemDifficulty difficulty;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private ProblemType type;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "starter_code", columnDefinition = "TEXT")
    private String starterCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "running_time_limit", nullable = false, length = 20)
    private RunningTimeLimit runningTimeLimit;

    @Enumerated(EnumType.STRING)
    @Column(name = "running_memory_limit", nullable = false, length = 20)
    private RunningMemoryLimit runningMemoryLimit;

    @Enumerated(EnumType.STRING)
    @Column(name = "timer_policy", nullable = false, length = 20)
    private TimerPolicy timerPolicy;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 100)
    private ProblemSource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "problem_status", nullable = false, length = 20)
    private ProblemStatus problemStatus;

    @Column(name = "lesson_id")
    private UUID lessonId;

    // default = 1이고, problem이 수정될 때 currentVersionNo++;
    @Column(name = "current_version_no", nullable = false)
    private Integer currentVersionNo = 1;

    // JPA 낙관적 락 버전입니다.
    // 문제 콘텐츠 버전(currentVersionNo)과는 별도로 관리합니다.
    @Version
    @Column(name = "lock_version", nullable = false)
    private Long lockVersion;

    public static Problem create(
            String title,
            ProgrammingLanguage language, ProblemDifficulty difficulty, ProblemType type,
            String description, String starterCode,
            RunningTimeLimit runningTimeLimit, RunningMemoryLimit runningMemoryLimit, TimerPolicy timerPolicy,
            ProblemSource source, ProblemStatus problemStatus
    ) {
        validateSupportedType(type);
        validateTitle(title);
        requireNotNull(language, "언어");
        requireNotNull(difficulty, "난이도");
        validateDescription(description);
        requireNotNull(runningTimeLimit, "실행 시간 제한");
        requireNotNull(runningMemoryLimit, "실행 메모리 제한");
        requireNotNull(timerPolicy, "타이머 정책");
        requireNotNull(source, "출처");
        requireNotNull(problemStatus, "문제 상태");

        Problem problem = new Problem();

        problem.title = title;
        problem.language = language;
        problem.difficulty = difficulty;
        problem.type = type;
        problem.description = description;
        problem.starterCode = starterCode;
        problem.runningTimeLimit = runningTimeLimit;
        problem.runningMemoryLimit = runningMemoryLimit;
        problem.timerPolicy = timerPolicy;
        problem.source = source;
        problem.problemStatus = problemStatus;
        problem.currentVersionNo = 1;

        return problem;
    }

    /* problem 값 변경 */
    public void changeTitle(String newTitle) {
        validateTitle(newTitle);
        this.title = newTitle;
    }

    public void changeLanguage(ProgrammingLanguage newLanguage) {
        requireNotNull(newLanguage, "언어");
        this.language = newLanguage;
    }

    public void changeDifficulty(ProblemDifficulty newDifficulty) {
        requireNotNull(newDifficulty, "난이도");
        this.difficulty = newDifficulty;
    }

    public void changeType(ProblemType newType) {
        validateSupportedType(newType);
        this.type = newType;
    }

    public void changeDescription(String newDescription) {
        validateDescription(newDescription);
        this.description = newDescription;
    }

    /** 스타터 코드는 선택 값이므로 null(제거)을 허용합니다. */
    public void changeStarterCode(String newStarterCode) { this.starterCode = newStarterCode; }

    public void changeRunningTimeLimit(RunningTimeLimit newRunningTimeLimit) {
        requireNotNull(newRunningTimeLimit, "실행 시간 제한");
        this.runningTimeLimit = newRunningTimeLimit;
    }

    public void changeRunningMemoryLimit(RunningMemoryLimit newRunningMemoryLimit) {
        requireNotNull(newRunningMemoryLimit, "실행 메모리 제한");
        this.runningMemoryLimit = newRunningMemoryLimit;
    }

    public void changeTimerPolicy(TimerPolicy newTimerPolicy) {
        requireNotNull(newTimerPolicy, "타이머 정책");
        this.timerPolicy = newTimerPolicy;
    }

    public void changeSource(ProblemSource newSource) {
        requireNotNull(newSource, "출처");
        this.source = newSource;
    }

    /**
     * 문제를 특정 레슨에 연결합니다(이슈 #291).
     *
     * <p>레슨 연결을 해제하려면 {@code null}을 전달합니다. 문제 하나는
     * 최대 하나의 레슨에만 연결됩니다(1:N) — 여러 레슨에서 재사용되는
     * 요구사항은 없는 것으로 확인되어 이렇게 설계했습니다.</p>
     */
    public void changeLessonId(UUID newLessonId) { this.lessonId = newLessonId; }

    /**
     * 발행된 문제를 재발행하기 위해 관리자 승인 대기 상태로 되돌립니다.
     *
     * <p>PUBLISHED 상태의 문제만 되돌릴 수 있습니다. 이 메서드는
     * "발행 자체를 실패로 되돌린다"는 의미가 아니라, 잘못된 데이터로
     * 발행된 문제(예: 이벤트 발행 버그로 다른 서비스에 정상 반영되지
     * 못한 경우)를 별도 삭제·재생성 없이 기존 문제 그대로 재발행할 수
     * 있도록, {@link #approvePublication()}을 다시 호출 가능한 상태로
     * 되돌리기 위해 존재합니다.</p>
     *
     * <p>REVIEW_PENDING으로 직접 되돌리는 이유: DRAFT로 되돌리면
     * {@link #requestPublicationReview()}를 다시 호출해야 REVIEW_PENDING로
     * 갈 수 있는데, 이 메서드를 외부에서 호출할 수 있는 API가 현재
     * create() 흐름 밖에는 없습니다. 재발행은 콘텐츠 수정 없이 그대로
     * 다시 승인만 받으면 되는 시나리오이므로, DRAFT를 거치지 않고 바로
     * REVIEW_PENDING으로 되돌려 기존 승인 절차({@link #approvePublication()})를
     * 그대로 재사용합니다.</p>
     */
    public void revertToReviewPendingForRepublish() {
        if (this.problemStatus != ProblemStatus.PUBLISHED) {
            throw new BusinessException(ErrorCode.INVALID_PROBLEM_STATUS_TRANSITION);
        }

        this.problemStatus = ProblemStatus.REVIEW_PENDING;
    }

    /**
     * 문제 공개 심사를 요청합니다.
     *
     * <p>DRAFT 상태의 문제만 REVIEW_PENDING 상태로
     * 전환할 수 있습니다.</p>
     */
    public void requestPublicationReview() {
        if (this.problemStatus != ProblemStatus.DRAFT) {
            throw new BusinessException(
                    ErrorCode.INVALID_PROBLEM_STATUS_TRANSITION
            );
        }

        this.problemStatus = ProblemStatus.REVIEW_PENDING;
    }

    // TODO: 추후에 작성 -> Content 다른 도메인을 import 할 수 있어야 해결이 가능합니다.
    // problem status 전환 과정은 DDD 적용
    // 1. REVIEW_PENDING -> PUBLISHED
    public void approvePublication() {
        if (this.problemStatus != ProblemStatus.REVIEW_PENDING) {
            throw new BusinessException(ErrorCode.INVALID_PROBLEM_STATUS_TRANSITION);
        }

        this.problemStatus = ProblemStatus.PUBLISHED;
        this.currentVersionNo++;
    }

    /** DB 제약(NOT NULL, length 100)과 같은 기준으로 제목을 검증합니다. */
    private static void validateTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "문제 제목은 비어 있을 수 없습니다.");
        }
        if (title.length() > TITLE_MAX_LENGTH) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "문제 제목은 " + TITLE_MAX_LENGTH + "자 이하여야 합니다."
            );
        }
    }

    /** DB 제약(NOT NULL)과 같은 기준으로 문제 설명을 검증합니다. 길이 상한은 요청 DTO의 정책입니다. */
    private static void validateDescription(String description) {
        if (description == null || description.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "문제 설명은 비어 있을 수 없습니다.");
        }
    }

    private static void requireNotNull(Object value, String fieldName) {
        if (value == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, fieldName + "은(는) 필수입니다.");
        }
    }

    private static void validateSupportedType(ProblemType type) {
        if (type != ProblemType.CODE) {
            throw new BusinessException(
                    ErrorCode.INVALID_PROBLEM_TYPE
            );
        }
    }

    public void increaseVersion() { this.currentVersionNo++; }
}
