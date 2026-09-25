package com.maesamco.content.application.persistence_service;

import com.maesamco.content.application.finder.ProblemFinder;
import com.maesamco.content.application.finder.TestCaseFinder;
import com.maesamco.content.domain.entity.TestCase;
import com.maesamco.content.domain.repository.TestCaseRepository;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import com.maesamco.content.application.command.TestCaseCreateCommand;
import com.maesamco.content.application.command.TestCaseUpdateCommand;
import com.maesamco.content.application.result.TestCaseResult;
import com.maesamco.content.global.common.pagination.PageQuery;
import com.maesamco.content.global.common.pagination.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** 테스트케이스 생성, 조회, 수정, 삭제를 담당하는 서비스 */
@Service
@RequiredArgsConstructor
public class TestCaseService {

    private final TestCaseRepository testCaseRepository;
    private final TestCaseFinder testCaseFinder;
    private final ProblemFinder problemFinder;

    /** 테스트케이스 생성 */
    @Transactional(rollbackFor = Exception.class)
    public TestCaseResult createTestCase(UUID problemId, TestCaseCreateCommand command) {

        problemFinder.lockById(problemId);

        // 특정 문자에 대한 공개 또는 비공개 테스트케이스 중 하나의 분류에 대해서 그 중 가장 test_case_order가 큰 값에 + 1을 한다.
        int testCaseOrder =
                testCaseRepository.findMaxTestCaseOrderByProblemIdAndIsPublic(problemId, command.getIsPublic()) + 1;

        // 관리자가 테스트케이스를 생성하는 경우만 고려한다. TODO: 나중에 테스트케이스 생성 요청 API 만들면 createByUser 함수 사용한다.
        TestCase testCase = TestCase.createByAdmin(
                problemId,
                command.getInput(),
                command.getExpectedOutput(),
                command.getIsPublic(),
                testCaseOrder
        );

        TestCase savedTestCase = testCaseRepository.save(testCase);

        return TestCaseResult.from(savedTestCase);
    }

    /** 테스트케이스 단건 조회 */
    @Transactional(readOnly = true)
    public TestCaseResult getTestCase(UUID testCaseId) {

        TestCase testCase = testCaseFinder.getById(testCaseId);

        /*
         * 관리자 경로도 공개 경로(getPublicTestCase)와 목록 조회처럼 상위 Problem의 활성 상태를 확인한다.
         * 상위 Problem이 삭제된 뒤에는 하위 테스트케이스에 ID로 직접 접근할 수 없어야 한다(이슈 #336).
         */
        problemFinder.getById(
                testCase.getProblemId()
        );

        return TestCaseResult.from(testCase);
    }

    /** 공개 테스트케이스 단건 조회 */
    @Transactional(readOnly = true)
    public TestCaseResult getPublicTestCase(UUID testCaseId) {

        TestCase testCase = testCaseFinder.getById(testCaseId);

        /*
         * 공개 테스트케이스라도 상위 Problem이 삭제된 경우에는
         * 직접 접근할 수 없도록 부모의 활성 상태를 함께 검증한다.
         */
        problemFinder.getById(
                testCase.getProblemId()
        );

        if (!testCase.getIsPublic()) {
            throw new BusinessException(
                    ErrorCode.TEST_CASE_ACCESS_DENIED
            );
        }

        return TestCaseResult.from(testCase);
    }

    /** 특정 문제의 공개 테스트케이스 목록 조회 */
    @Transactional(readOnly = true)
    public PageResult<TestCaseResult> searchTestCasesPublic(UUID problemId, PageQuery pageQuery) {
        problemFinder.getById(problemId);

        PageResult<TestCase> testCases = testCaseRepository.searchTestCases(problemId, true, pageQuery);

        return testCases.map(TestCaseResult::from);
    }

    /** 특정 문제의 공개와 비공개 테스트케이스 목록 전체 조회 */
    @Transactional(readOnly = true)
    public PageResult<TestCaseResult> searchTestCasesAll(UUID problemId, PageQuery pageQuery) {
        problemFinder.getById(problemId);

        PageResult<TestCase> testCases = testCaseRepository.searchTestCasesAll(problemId, pageQuery);

        return testCases.map(TestCaseResult::from);
    }

    /** 테스트케이스 수정 */
    @Transactional(rollbackFor = Exception.class)
    public TestCaseResult updateTestCase(UUID testCaseId, TestCaseUpdateCommand command) {
        TestCase testCase = testCaseFinder.getById(testCaseId);

        // 조회와 같은 정책: 상위 Problem이 삭제된 뒤에는 ID로 직접 수정할 수 없다(이슈 #336).
        problemFinder.getById(testCase.getProblemId());

        // 입력값, 출력값 수정
        if (command.getInput() != null) { testCase.changeInput(command.getInput()); }
        if (command.getExpectedOutput() != null) { testCase.changeExpectedOutput(command.getExpectedOutput()); }

        // 공개 / 비공개 수정 정책
        if (command.getIsPublic() != null &&
                command.getIsPublic() != testCase.getIsPublic()) {

            problemFinder.lockById(
                    testCase.getProblemId()
            );

            // 삭제하지 않고, 공개 여부를 변경하면서 새 그룹의 마지막 순서를 부여한다.
            // 기존 그룹의 빈 order는 유지하며 별도로 재정렬하지 않는다.
            // 하나의 문제에 대해서 등록/수정이 많이 일어나지 않기 때문에 정리할 필요가 없을 것으로 예싱한다.
            int newTestCaseOrder =
                    testCaseRepository.findMaxTestCaseOrderByProblemIdAndIsPublic(
                            testCase.getProblemId(),
                            command.getIsPublic()
                    ) + 1;
            testCase.changeIsPublic(command.getIsPublic());
            testCase.changeTestCaseOrder(newTestCaseOrder);
        }

        // test_case_order 값 수정 정책 (우선순위가 제일 높기에 마지막에 실행)
        // 그리고 만약에 관리자가 수정한 order가 기존 order랑 중복된다고 해도 tie-breaker가 실행되었기에 페이징에는 문제가 없다.
        // 또한 테스트케이스를 보낼 JudgeService에서는 사실상 집합의 개념으로 테스트케이스를 이용하기에 Order가 중요하지 않게 된다.
        if (command.getTestCaseOrder() != null) {
            testCase.changeTestCaseOrder(command.getTestCaseOrder());
        }

        return TestCaseResult.from(testCase);
    }

    /** 테스트케이스 삭제 */
    @Transactional(rollbackFor = Exception.class)
    public void deleteTestCase(UUID testCaseId, UUID userId) {

        TestCase testCase = testCaseFinder.getById(testCaseId);

        // 조회·수정과 같은 정책: 상위 Problem이 삭제된 뒤에는 ID로 직접 삭제할 수 없다(이슈 #336).
        problemFinder.getById(testCase.getProblemId());

        testCase.softDelete(userId);
    }
}
