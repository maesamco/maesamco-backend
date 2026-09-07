package com.maesamco.content.testcase.application.service;

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

        // 특정 문제에 대한 테스트케이스 생성이기에, 문제가 존재해야 한다.
        problemFinder.findProblemById(problemId);

        // TODO: 일차적으로 관리자가 testcase 생성하는 것만 생각한다.
        TestCase testCase = TestCase.createByAdmin(
                problemId,
                request.getInput(),
                request.getExpectedOutput(),
                request.getIsPublic(),
                request.getTestCaseOrder()
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

    /** 특정 문제 테스트케이스 목록 조회 */
    @Transactional(readOnly = true)
    public PageResponse<TestCaseResponse> searchTestCases(UUID problemId, Pageable pageable) {

        // 존재하지 않는 문제에 대한 조회 방지
        problemFinder.findProblemById(problemId);

        Page<TestCase> testCases = testCaseRepository.searchTestCases(problemId, pageable);

        return PageResponse.from(testCases, TestCaseResponse::from);
    }

    /** 테스트케이스 수정 */
    @Transactional(rollbackFor = Exception.class)
    public TestCaseResponse updateTestCase(UUID testCaseId, TestCaseUpdateRequest request) {

        TestCase testCase = testCaseFinder.getTestCase(testCaseId);

        if (request.getInput() != null) { testCase.changeInput(request.getInput()); }
        if (request.getExpectedOutput() != null) { testCase.changeExpectedOutput(request.getExpectedOutput()); }
        if (request.getIsPublic() != null) { testCase.changeIsPublic(request.getIsPublic());}
        if (request.getTestCaseOrder() != null) { testCase.changeTestCaseOrder(request.getTestCaseOrder());}

        return TestCaseResponse.from(testCase);
    }

    /** 테스트케이스 삭제 */
    @Transactional(rollbackFor = Exception.class)
    public void deleteTestCase(UUID testCaseId, UUID userId) {

        TestCase testCase = testCaseFinder.getTestCase(testCaseId);

        testCase.softDelete(userId);
    }
}