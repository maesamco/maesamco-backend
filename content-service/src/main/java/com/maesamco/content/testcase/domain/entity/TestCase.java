package com.maesamco.content.testcase.domain.entity;

import com.maesamco.content.global.common.BaseEntity;
import com.maesamco.content.testcase.domain.enums.TestCaseStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Entity
@Table(name = "p_testcases")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TestCase extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "test_case_id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "problem_id", nullable = false)
    private UUID problemId;

    @Column(name = "input", nullable = false, columnDefinition = "TEXT")
    private String input;

    @Column(name = "expected_output", nullable = false, columnDefinition = "TEXT")
    private String expectedOutput;

    @Column(name = "is_public", nullable = false)
    private Boolean isPublic;

    @Column(name = "test_case_order", nullable = false)
    private Integer testCaseOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "test_case_status", nullable = false, length = 20)
    private TestCaseStatus testCaseStatus;

    private TestCase(UUID problemId, String input, String expectedOutput, Boolean isPublic, Integer testCaseOrder, TestCaseStatus testCaseStatus) {
        this.problemId = problemId;
        this.input = input;
        this.expectedOutput = expectedOutput;
        this.isPublic = isPublic;
        this.testCaseOrder = testCaseOrder;
        this.testCaseStatus = testCaseStatus;
    }

    /** 사용자가 테스트케이스 생성 */
    public static TestCase createByUser(UUID problemId, String input, String expectedOutput, Boolean isPublic, Integer testCaseOrder) {
        return new TestCase(problemId, input, expectedOutput, isPublic, testCaseOrder, TestCaseStatus.PENDING);
    }

    /** 관리자가 테스트케이스 생성 */
    public static TestCase createByAdmin(UUID problemId, String input, String expectedOutput, Boolean isPublic, Integer testCaseOrder) {
        return new TestCase(problemId, input, expectedOutput, isPublic, testCaseOrder, TestCaseStatus.APPROVED);
    }

    /** 테스트케이스 수정 */
    public void changeInput(String input) { this.input = input; }
    public void changeExpectedOutput(String expectedOutput) { this.expectedOutput = expectedOutput; }
    public void changeIsPublic(Boolean isPublic) { this.isPublic = isPublic; }
    public void changeTestCaseOrder(Integer testCaseOrder) { this.testCaseOrder = testCaseOrder; }
    public void changeTestCaseStatus(TestCaseStatus testCaseStatus) { this.testCaseStatus = testCaseStatus; }
}