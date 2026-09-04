package com.maesamco.content.problem.domain.entity;

import com.maesamco.content.global.common.BaseEntity;
import com.maesamco.content.problem.domain.enums.ProblemDifficulty;
import com.maesamco.content.problem.domain.enums.ProblemSource;
import com.maesamco.content.problem.domain.enums.ProblemStatus;
import com.maesamco.content.problem.domain.enums.ProblemType;
import com.maesamco.content.problem.domain.enums.ProgrammingLanguage;
import com.maesamco.content.problem.domain.enums.TimerPolicy;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

/** 문제 객체 */

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "p_problems")
public class Problem extends BaseEntity {

    @Id
    @UuidGenerator
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

    @Column(name = "running_time_limit", nullable = false)
    private Integer runningTimeLimit;

    @Column(name = "running_memory_limit", nullable = false)
    private Integer runningMemoryLimit;

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
}