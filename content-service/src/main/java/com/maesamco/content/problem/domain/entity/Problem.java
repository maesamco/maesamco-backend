package com.maesamco.content.problem.domain.entity;

import com.maesamco.content.global.common.BaseEntity;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import com.maesamco.content.problem.domain.enums.*;
import jakarta.persistence.*;
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

    // default = 1이고, problem이 수정될 때 currentVersionNo++;
    @Column(name = "current_version_no", nullable = false)
    private Integer currentVersionNo = 1;

    public static Problem create(
            String title,
            ProgrammingLanguage language, ProblemDifficulty difficulty, ProblemType type,
            String description, String starterCode,
            RunningTimeLimit runningTimeLimit, RunningMemoryLimit runningMemoryLimit, TimerPolicy timerPolicy,
            ProblemSource source, ProblemStatus problemStatus
    ) {
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
    public void changeTitle(String newTitle) { this.title = newTitle; }
    public void changeLanguage(ProgrammingLanguage newLanguage) { this.language = newLanguage; }
    public void changeDifficulty(ProblemDifficulty newDifficulty) { this.difficulty = newDifficulty; }
    public void changeType(ProblemType newType) { this.type = newType; }
    public void changeDescription(String newDescription) { this.description = newDescription; }
    public void changeStarterCode(String newStarterCode) { this.starterCode = newStarterCode; }
    public void changeRunningTimeLimit(RunningTimeLimit newRunningTimeLimit) { this.runningTimeLimit = newRunningTimeLimit; }
    public void changeRunningMemoryLimit(RunningMemoryLimit newRunningMemoryLimit) { this.runningMemoryLimit = newRunningMemoryLimit; }
    public void changeTimerPolicy(TimerPolicy newTimerPolicy) { this.timerPolicy = newTimerPolicy; }
    public void changeSource(ProblemSource newSource) { this.source = newSource; }

    public void setProblemStatusDraft() {
        this.problemStatus = ProblemStatus.DRAFT;
    }
    public void setProblemStatusReviewPending() {
        this.problemStatus = ProblemStatus.REVIEW_PENDING;
    }

    // TODO: 추후에 작성
    // problem status 전환 과정은 DDD 적용
    // 1. REVIEW_PENDING -> PUBLISHED
    public void approvePublication() {
        if (this.problemStatus != ProblemStatus.REVIEW_PENDING) {
            throw new BusinessException(ErrorCode.INVALID_PROBLEM_STATUS_TRANSITION);
        }

        this.problemStatus = ProblemStatus.PUBLISHED;
    }


    public void increaseVersion() { this.currentVersionNo++; }
}