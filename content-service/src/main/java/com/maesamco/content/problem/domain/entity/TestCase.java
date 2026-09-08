package com.maesamco.content.problem.domain.entity;

import com.maesamco.content.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 문제 채점에 사용되는 테스트케이스입니다.
 *
 * <p>공개 테스트케이스는 학습자에게 노출될 수 있지만,
 * 비공개 테스트케이스의 input과 expectedOutput은
 * 외부 API나 로그에 노출하지 않습니다.</p>
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "p_test_cases")
public class TestCase extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(
            name = "id",
            nullable = false,
            updatable = false
    )
    private UUID id;

    @Column(
            name = "problem_id",
            nullable = false,
            updatable = false
    )
    private UUID problemId;

    @Column(
            name = "is_public",
            nullable = false
    )
    private boolean isPublic;

    @Column(
            name = "input",
            nullable = false,
            columnDefinition = "TEXT"
    )
    private String input;

    @Column(
            name = "expected_output",
            nullable = false,
            columnDefinition = "TEXT"
    )
    private String expectedOutput;

    @Column(
            name = "display_order",
            nullable = false
    )
    private int displayOrder;

    /**
     * 테스트케이스를 생성합니다.
     *
     * @param problemId 문제 식별자
     * @param isPublic 공개 여부
     * @param input 테스트 입력값
     * @param expectedOutput 기대 출력값
     * @param displayOrder 표시 및 실행 순서
     * @return 생성된 테스트케이스
     */
    public static TestCase create(
            UUID problemId,
            boolean isPublic,
            String input,
            String expectedOutput,
            int displayOrder
    ) {
        TestCase testCase = new TestCase();

        testCase.problemId = problemId;
        testCase.isPublic = isPublic;
        testCase.input = input;
        testCase.expectedOutput = expectedOutput;
        testCase.displayOrder = displayOrder;

        return testCase;
    }
}
