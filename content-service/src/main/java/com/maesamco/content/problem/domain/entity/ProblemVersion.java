package com.maesamco.content.problem.domain.entity;

import com.maesamco.content.problem.domain.enums.ProblemDifficulty;
import com.maesamco.content.problem.domain.enums.ProblemSource;
import com.maesamco.content.problem.domain.enums.ProblemType;
import com.maesamco.content.problem.domain.enums.ProgrammingLanguage;
import com.maesamco.content.problem.domain.enums.TimerPolicy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 발행된 문제의 특정 버전 스냅샷입니다.
 *
 * <p>문제가 발행되는 시점의 문제 내용과 테스트케이스를
 * JSONB 스냅샷으로 고정하여 이후 원본 문제가 변경되어도
 * 해당 버전의 채점 기준을 보존합니다.</p>
 */
@Entity
@Table(
        name = "p_problem_versions",
        uniqueConstraints = {
                @UniqueConstraint(
                        columnNames = {
                                "problem_id",
                                "version_no"
                        }
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class ProblemVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(
            name = "id",
            nullable = false,
            updatable = false
    )
    private UUID id;

    /**
     * 원본 문제 식별자입니다.
     */
    @Column(
            name = "problem_id",
            nullable = false,
            updatable = false
    )
    private UUID problemId;

    /**
     * 발행된 문제 버전 번호입니다.
     */
    @Column(
            name = "version_no",
            nullable = false,
            updatable = false
    )
    private Integer versionNo;

    /**
     * 발행 시점의 문제 내용과 테스트케이스 스냅샷입니다.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
            name = "content_snapshot",
            nullable = false,
            updatable = false,
            columnDefinition = "jsonb"
    )
    private ProblemVersionSnapshot contentSnapshot;

    /**
     * 해당 문제 버전이 발행된 시각입니다.
     */
    @Column(
            name = "published_at",
            nullable = false,
            updatable = false
    )
    private Instant publishedAt;

    /**
     * 문제 발행을 승인한 관리자 식별자입니다.
     *
     * <p>JPA Auditing의 AuditorAware를 통해 자동으로 저장됩니다.</p>
     */
    @CreatedBy
    @Column(
            name = "created_by",
            nullable = false,
            updatable = false
    )
    private UUID createdBy;

    private ProblemVersion(
            UUID problemId,
            Integer versionNo,
            ProblemVersionSnapshot contentSnapshot,
            Instant publishedAt
    ) {
        this.problemId = problemId;
        this.versionNo = versionNo;
        this.contentSnapshot = contentSnapshot;
        this.publishedAt = publishedAt;
    }

    /**
     * 발행 시점의 문제 버전을 생성합니다.
     *
     * @param problemId   영속화된 원본 문제 식별자
     * @param problem     발행할 현재 문제
     * @param testCases   발행 시점 테스트케이스
     * @param publishedAt 발행 시각
     * @return 발행 버전 스냅샷
     */
    public static ProblemVersion createPublished(
            UUID problemId,
            Problem problem,
            List<TestCaseItem> testCases,
            Instant publishedAt
    ) {
        ProblemVersionSnapshot snapshot =
                ProblemVersionSnapshot.from(
                        problem,
                        testCases
                );

        return new ProblemVersion(
                problemId,
                problem.getCurrentVersionNo(),
                snapshot,
                publishedAt
        );
    }

    /**
     * 발행 시점의 문제 전체 내용을 표현하는 JSON 스냅샷입니다.
     */
    public record ProblemVersionSnapshot(
            String title,
            ProgrammingLanguage language,
            ProblemDifficulty difficulty,
            ProblemType type,
            String description,
            String starterCode,
            Integer runningTimeLimit,
            Integer runningMemoryLimit,
            TimerPolicy timerPolicy,
            ProblemSource source,
            List<TestCaseItem> testCases
    ) {

        /**
         * 현재 문제와 테스트케이스를 이용해
         * 발행 스냅샷을 생성합니다.
         */
        private static ProblemVersionSnapshot from(
                Problem problem,
                List<TestCaseItem> testCases
        ) {
            List<TestCaseItem> immutableTestCases =
                    testCases == null
                            ? List.of()
                            : List.copyOf(testCases);

            return new ProblemVersionSnapshot(
                    problem.getTitle(),
                    problem.getLanguage(),
                    problem.getDifficulty(),
                    problem.getType(),
                    problem.getDescription(),
                    problem.getStarterCode(),
                    problem.getRunningTimeLimit(),
                    problem.getRunningMemoryLimit(),
                    problem.getTimerPolicy(),
                    problem.getSource(),
                    immutableTestCases
            );
        }
    }

    /**
     * 발행 버전에 포함되는 테스트케이스 한 건입니다.
     *
     * @param testCaseId    테스트케이스 식별자
     * @param isPublic      학습자에게 공개되는 테스트케이스인지 여부
     * @param input         테스트 입력값
     * @param expectedOutput 기대 출력값
     * @param displayOrder  테스트케이스 표시 순서
     */
    public record TestCaseItem(
            UUID testCaseId,
            boolean isPublic,
            String input,
            String expectedOutput,
            int displayOrder
    ) {
    }
}
