package com.maesamco.content.testcase.application.service;

import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import com.maesamco.content.global.response.PageResponse;
import com.maesamco.content.problem.application.port.ProblemFinder;
import com.maesamco.content.testcase.application.port.TestCaseFinder;
import com.maesamco.content.testcase.domain.entity.TestCase;
import com.maesamco.content.testcase.domain.repository.TestCaseRepository;
import com.maesamco.content.testcase.presentation.dto.request.TestCaseCreateRequest;
import com.maesamco.content.testcase.presentation.dto.request.TestCaseUpdateRequest;
import com.maesamco.content.testcase.presentation.dto.response.TestCaseCreateResponse;
import com.maesamco.content.testcase.presentation.dto.response.TestCaseResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    public TestCaseCreateResponse createTestCase(UUID problemId, TestCaseCreateRequest request) {

        problemFinder.getProblemForUpdate(problemId);

        // 특정 문자에 대한 공개 또는 비공개 테스트케이스 중 하나의 분류에 대해서 그 중 가장 test_case_order가 큰 값에 + 1을 한다.
        int testCaseOrder =
                testCaseRepository.findMaxTestCaseOrderByProblemIdAndIsPublic(problemId, request.getIsPublic()) + 1;

        // 관리자가 테스트케이스를 생성하는 경우만 고려한다. TODO: 나중에 테스트케이스 생성 요청 API 만들면 createByUser 함수 사용한다.
        TestCase testCase = TestCase.createByAdmin(
                problemId,
                request.getInput(),
                request.getExpectedOutput(),
                request.getIsPublic(),
                testCaseOrder
        );

        TestCase savedTestCase = testCaseRepository.save(testCase);

        return TestCaseCreateResponse.from(savedTestCase);
    }

    /** 테스트케이스 단건 조회 */
    @Transactional(readOnly = true)
    public TestCaseResponse getTestCase(UUID testCaseId) {

        TestCase testCase = testCaseFinder.getTestCase(testCaseId);

        return TestCaseResponse.from(testCase);
    }

    /** 공개 테스트케이스 단건 조회 */
    @Transactional(readOnly = true)
    public TestCaseResponse getPublicTestCase(UUID testCaseId) {

        TestCase testCase = testCaseFinder.getTestCase(testCaseId);

        /*
         * 공개 테스트케이스라도 상위 Problem이 삭제된 경우에는
         * 직접 접근할 수 없도록 부모의 활성 상태를 함께 검증한다.
         */
        problemFinder.getProblem(
                testCase.getProblemId()
        );

        if (!testCase.getIsPublic()) {
            throw new BusinessException(
                    ErrorCode.TEST_CASE_ACCESS_DENIED
            );
        }

        return TestCaseResponse.from(testCase);
    }

    /** 특정 문제의 공개 테스트케이스 목록 조회 */
    @Transactional(readOnly = true)
    public PageResponse<TestCaseResponse> searchTestCasesPublic(UUID problemId, Pageable pageable) {
        problemFinder.getProblem(problemId);

        Page<TestCase> testCases = testCaseRepository.searchTestCases(problemId, true, pageable);

        return PageResponse.from(testCases, TestCaseResponse::from);
    }

    /** 특정 문제의 공개와 비공개 테스트케이스 목록 전체 조회 */
    @Transactional(readOnly = true)
    public PageResponse<TestCaseResponse> searchTestCasesAll(UUID problemId, Pageable pageable) {
        problemFinder.getProblem(problemId);

        Page<TestCase> testCases = testCaseRepository.searchTestCasesAll(problemId, pageable);

        return PageResponse.from(testCases, TestCaseResponse::from);
    }

    /** 테스트케이스 수정 */
    @Transactional(rollbackFor = Exception.class)
    public TestCaseResponse updateTestCase(UUID testCaseId, TestCaseUpdateRequest request) {
        TestCase testCase = testCaseFinder.getTestCase(testCaseId);

        // 입력값, 출력값 수정
        if (request.getInput() != null) { testCase.changeInput(request.getInput()); }
        if (request.getExpectedOutput() != null) { testCase.changeExpectedOutput(request.getExpectedOutput()); }

        // 공개 / 비공개 수정 정책
        if (request.getIsPublic() != null &&
                request.getIsPublic() != testCase.getIsPublic()) {

            problemFinder.getProblemForUpdate(
                    testCase.getProblemId()
            );

            // 삭제하지 않고, 공개 여부를 변경하면서 새 그룹의 마지막 순서를 부여한다.
            // 기존 그룹의 빈 order는 유지하며 별도로 재정렬하지 않는다.
            // 하나의 문제에 대해서 등록/수정이 많이 일어나지 않기 때문에 정리할 필요가 없을 것으로 예싱한다.
            int newTestCaseOrder =
                    testCaseRepository.findMaxTestCaseOrderByProblemIdAndIsPublic(
                            testCase.getProblemId(),
                            request.getIsPublic()
                    ) + 1;
            testCase.changeIsPublic(request.getIsPublic());
            testCase.changeTestCaseOrder(newTestCaseOrder);
        }

        // test_case_order 값 수정 정책 (우선순위가 제일 높기에 마지막에 실행)
        // 그리고 만약에 관리자가 수정한 order가 기존 order랑 중복된다고 해도 tie-breaker가 실행되었기에 페이징에는 문제가 없다.
        // 또한 테스트케이스를 보낼 JudgeService에서는 사실상 집합의 개념으로 테스트케이스를 이용하기에 Order가 중요하지 않게 된다.
        if (request.getTestCaseOrder() != null) {
            testCase.changeTestCaseOrder(request.getTestCaseOrder());
        }

        return TestCaseResponse.from(testCase);
    }

    /** 테스트케이스 삭제 */
    @Transactional(rollbackFor = Exception.class)
    public void deleteTestCase(UUID testCaseId, UUID userId) {

        TestCase testCase = testCaseFinder.getTestCase(testCaseId);

        testCase.softDelete(userId);
    }
}
