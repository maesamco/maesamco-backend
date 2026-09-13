package com.maesamco.content.quicktest;

import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import com.maesamco.content.global.response.PageResponse;
import com.maesamco.content.problem.application.port.ProblemFinder;
import com.maesamco.content.testcase.application.port.TestCaseFinder;
import com.maesamco.content.testcase.application.service.TestCaseService;
import com.maesamco.content.testcase.domain.entity.TestCase;
import com.maesamco.content.testcase.domain.enums.TestCaseStatus;
import com.maesamco.content.testcase.domain.repository.TestCaseRepository;
import com.maesamco.content.testcase.presentation.dto.request.TestCaseCreateRequest;
import com.maesamco.content.testcase.presentation.dto.request.TestCaseUpdateRequest;
import com.maesamco.content.testcase.presentation.dto.response.TestCaseResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TestCaseServiceTest {

    @Mock
    private TestCaseRepository testCaseRepository;

    @Mock
    private TestCaseFinder testCaseFinder;

    @Mock
    private ProblemFinder problemFinder;

    @InjectMocks
    private TestCaseService testCaseService;


    @Test
    @DisplayName("공개 테스트케이스 단건 조회 시 테스트케이스 정보를 반환한다.")
    void getPublicTestCase_success() {

        // given
        UUID testCaseId = UUID.randomUUID();
        UUID problemId = UUID.randomUUID();

        TestCase testCase = mock(TestCase.class);

        when(testCaseFinder.getTestCase(testCaseId)).thenReturn(testCase);
        when(testCase.getId()).thenReturn(testCaseId);
        when(testCase.getProblemId()).thenReturn(problemId);
        when(testCase.getInput()).thenReturn("1 2");
        when(testCase.getExpectedOutput()).thenReturn("3");
        when(testCase.getIsPublic()).thenReturn(true);
        when(testCase.getTestCaseStatus()).thenReturn(TestCaseStatus.APPROVED);
        when(testCase.getTestCaseOrder()).thenReturn(1);

        // when
        TestCaseResponse response = testCaseService.getPublicTestCase(testCaseId);

        // then
        assertThat(response.getId()).isEqualTo(testCaseId);
        assertThat(response.getProblemId()).isEqualTo(problemId);
        assertThat(response.getInput()).isEqualTo("1 2");
        assertThat(response.getExpectedOutput()).isEqualTo("3");
        assertThat(response.getIsPublic()).isTrue();
        assertThat(response.getTestCaseStatus()).isEqualTo(TestCaseStatus.APPROVED);
        assertThat(response.getTestCaseOrder()).isEqualTo(1);

        verify(problemFinder)
                .getProblem(problemId);

        System.out.println("===== 공개 테스트케이스 단건 조회 결과 =====");
        System.out.println("testCaseId = " + response.getId());
        System.out.println("problemId = " + response.getProblemId());
        System.out.println("input = " + response.getInput());
        System.out.println("expectedOutput = " + response.getExpectedOutput());
        System.out.println("isPublic = " + response.getIsPublic());
        System.out.println("testCaseStatus = " + response.getTestCaseStatus());
        System.out.println("testCaseOrder = " + response.getTestCaseOrder());
    }


    @Test
    @DisplayName("비공개 테스트케이스는 공개 조회할 수 없다.")
    void getPublicTestCase_private() {

        // given
        UUID testCaseId = UUID.randomUUID();

        TestCase testCase = mock(TestCase.class);

        when(testCaseFinder.getTestCase(testCaseId)).thenReturn(testCase);
        when(testCase.getIsPublic()).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> testCaseService.getPublicTestCase(testCaseId))
                .isInstanceOf(BusinessException.class);

        System.out.println("===== 비공개 테스트케이스 조회 결과 =====");
        System.out.println("testCaseId = " + testCaseId);
        System.out.println("isPublic = false");
        System.out.println("공개 조회 불가");
    }

    @Test
    @DisplayName("상위 문제가 존재하지 않으면 공개 테스트케이스도 조회할 수 없다.")
    void getPublicTestCase_parentProblemNotFound() {

        // given
        UUID testCaseId = UUID.randomUUID();
        UUID problemId = UUID.randomUUID();

        TestCase testCase = mock(TestCase.class);

        when(testCaseFinder.getTestCase(testCaseId))
                .thenReturn(testCase);

        when(testCase.getProblemId())
                .thenReturn(problemId);

        when(problemFinder.getProblem(problemId))
                .thenThrow(
                        new BusinessException(
                                ErrorCode.PROBLEM_NOT_FOUND
                        )
                );

        // when & then
        assertThatThrownBy(
                () -> testCaseService.getPublicTestCase(testCaseId)
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(
                                ((BusinessException) exception).getErrorCode()
                        ).isEqualTo(ErrorCode.PROBLEM_NOT_FOUND)
                );

        verify(problemFinder)
                .getProblem(problemId);

        verify(testCase, never())
                .getIsPublic();
    }

    @Test
    @DisplayName("공개/비공개 테스트케이스를 추가한 후 공개 테스트케이스 목록만 조회한다.")
    void searchTestCasesPublic_success() {

        // given
        UUID problemId = UUID.randomUUID();

        TestCase testCase1 = mock(TestCase.class);
        TestCase testCase2 = mock(TestCase.class);
        TestCase testCase3 = mock(TestCase.class);

        when(testCase1.getId()).thenReturn(UUID.randomUUID());
        when(testCase1.getProblemId()).thenReturn(problemId);
        when(testCase1.getInput()).thenReturn("1 2");
        when(testCase1.getExpectedOutput()).thenReturn("3");
        when(testCase1.getIsPublic()).thenReturn(true);
        when(testCase1.getTestCaseStatus()).thenReturn(TestCaseStatus.APPROVED);
        when(testCase1.getTestCaseOrder()).thenReturn(1);

        when(testCase2.getId()).thenReturn(UUID.randomUUID());
        when(testCase2.getProblemId()).thenReturn(problemId);
        when(testCase2.getInput()).thenReturn("10 20");
        when(testCase2.getExpectedOutput()).thenReturn("30");
        when(testCase2.getIsPublic()).thenReturn(true);
        when(testCase2.getTestCaseStatus()).thenReturn(TestCaseStatus.APPROVED);
        when(testCase2.getTestCaseOrder()).thenReturn(2);

        when(testCase3.getId()).thenReturn(UUID.randomUUID());
        when(testCase3.getProblemId()).thenReturn(problemId);
        when(testCase3.getInput()).thenReturn("100 200");
        when(testCase3.getExpectedOutput()).thenReturn("300");
        when(testCase3.getIsPublic()).thenReturn(true);
        when(testCase3.getTestCaseStatus()).thenReturn(TestCaseStatus.APPROVED);
        when(testCase3.getTestCaseOrder()).thenReturn(3);

        // 공개 테스트케이스 생성 요청
        TestCaseCreateRequest publicRequest = mock(TestCaseCreateRequest.class);

        when(publicRequest.getInput()).thenReturn("1000 2000");
        when(publicRequest.getExpectedOutput()).thenReturn("3000");
        when(publicRequest.getIsPublic()).thenReturn(true);

        // 비공개 테스트케이스 생성 요청
        TestCaseCreateRequest privateRequest = mock(TestCaseCreateRequest.class);

        when(privateRequest.getInput()).thenReturn("5000 6000");
        when(privateRequest.getExpectedOutput()).thenReturn("11000");
        when(privateRequest.getIsPublic()).thenReturn(false);

        // 기존 공개 테스트케이스의 최대 order = 3
        when(testCaseRepository.findMaxTestCaseOrderByProblemIdAndIsPublic(problemId, true)).thenReturn(3);

        // 기존 비공개 테스트케이스는 없다고 가정
        when(testCaseRepository.findMaxTestCaseOrderByProblemIdAndIsPublic(problemId, false)).thenReturn(0);

        // save 시 실제 생성된 TestCase를 그대로 반환하면서 ID만 테스트용으로 주입
        when(testCaseRepository.save(any(TestCase.class))).thenAnswer(invocation -> {
            TestCase testCase = invocation.getArgument(0);
            ReflectionTestUtils.setField(testCase, "id", UUID.randomUUID());
            return testCase;
        });

        // 공개 1개 생성
        testCaseService.createTestCase(problemId, publicRequest);

        // 비공개 1개 생성
        testCaseService.createTestCase(problemId, privateRequest);

        // 실제 save된 두 TestCase 가져오기
        ArgumentCaptor<TestCase> captor = ArgumentCaptor.forClass(TestCase.class);
        verify(testCaseRepository, times(2)).save(captor.capture());

        List<TestCase> createdTestCases = captor.getAllValues();

        TestCase createdPublicTestCase = createdTestCases.stream()
                .filter(TestCase::getIsPublic)
                .findFirst()
                .orElseThrow();

        TestCase createdPrivateTestCase = createdTestCases.stream()
                .filter(testCase -> !testCase.getIsPublic())
                .findFirst()
                .orElseThrow();

        // 생성된 테스트케이스 확인
        assertThat(createdPublicTestCase.getTestCaseOrder()).isEqualTo(4);
        assertThat(createdPrivateTestCase.getTestCaseOrder()).isEqualTo(1);

        Pageable pageable = PageRequest.of(0, 10);

        // 공개 목록이므로 비공개 테스트케이스는 포함하지 않음
        Page<TestCase> testCasePage = new PageImpl<>(
                List.of(
                        testCase1,
                        testCase2,
                        testCase3,
                        createdPublicTestCase
                ),
                pageable,
                4
        );

        when(testCaseRepository.searchTestCases(problemId, true, pageable)).thenReturn(testCasePage);

        // when
        PageResponse<TestCaseResponse> response =
                testCaseService.searchTestCasesPublic(problemId, pageable);

        // then
        assertThat(response.content()).hasSize(4);

        assertThat(response.content())
                .allMatch(TestCaseResponse::getIsPublic);

        assertThat(response.content().get(3).getInput()).isEqualTo("1000 2000");
        assertThat(response.content().get(3).getExpectedOutput()).isEqualTo("3000");
        assertThat(response.content().get(3).getTestCaseOrder()).isEqualTo(4);

        System.out.println("===== 새로 생성한 테스트케이스 =====");
        System.out.println("[공개]");
        System.out.println("testCaseId = " + createdPublicTestCase.getId());
        System.out.println("input = " + createdPublicTestCase.getInput());
        System.out.println("expectedOutput = " + createdPublicTestCase.getExpectedOutput());
        System.out.println("isPublic = " + createdPublicTestCase.getIsPublic());
        System.out.println("order = " + createdPublicTestCase.getTestCaseOrder());

        System.out.println();

        System.out.println("[비공개]");
        System.out.println("testCaseId = " + createdPrivateTestCase.getId());
        System.out.println("input = " + createdPrivateTestCase.getInput());
        System.out.println("expectedOutput = " + createdPrivateTestCase.getExpectedOutput());
        System.out.println("isPublic = " + createdPrivateTestCase.getIsPublic());
        System.out.println("order = " + createdPrivateTestCase.getTestCaseOrder());

        System.out.println();
        System.out.println("===== 공개 테스트케이스 목록 조회 결과 =====");

        for (TestCaseResponse testCase : response.content()) {
            System.out.println(
                    "testCaseId = " + testCase.getId()
                            + ", input = " + testCase.getInput()
                            + ", expectedOutput = " + testCase.getExpectedOutput()
                            + ", isPublic = " + testCase.getIsPublic()
                            + ", status = " + testCase.getTestCaseStatus()
                            + ", order = " + testCase.getTestCaseOrder()
            );
        }
    }

    @Test
    @DisplayName("테스트케이스 생성 시 문제 행을 먼저 잠근 뒤 순번을 계산한다.")
    void createTestCase_locksProblemBeforeCalculatingOrder() {

        // given
        UUID problemId = UUID.randomUUID();

        TestCaseCreateRequest request =
                mock(TestCaseCreateRequest.class);

        when(request.getInput())
                .thenReturn("1 2");

        when(request.getExpectedOutput())
                .thenReturn("3");

        when(request.getIsPublic())
                .thenReturn(true);

        when(testCaseRepository
                .findMaxTestCaseOrderByProblemIdAndIsPublic(
                        problemId,
                        true
                ))
                .thenReturn(3);

        when(testCaseRepository.save(any(TestCase.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        // when
        testCaseService.createTestCase(
                problemId,
                request
        );

        // then
        InOrder inOrder =
                inOrder(
                        problemFinder,
                        testCaseRepository
                );

        inOrder.verify(problemFinder)
                .getProblemForUpdate(problemId);

        inOrder.verify(testCaseRepository)
                .findMaxTestCaseOrderByProblemIdAndIsPublic(
                        problemId,
                        true
                );

        verify(testCaseRepository)
                .save(any(TestCase.class));
    }

    @Test
    @DisplayName("테스트케이스 공개 여부 전환 시 문제 행을 먼저 잠근 뒤 순번을 계산한다.")
    void updateTestCase_visibilityChange_locksProblemBeforeCalculatingOrder() {

        // given
        UUID testCaseId = UUID.randomUUID();
        UUID problemId = UUID.randomUUID();

        TestCase testCase = mock(TestCase.class);
        TestCaseUpdateRequest request =
                mock(TestCaseUpdateRequest.class);

        when(testCaseFinder.getTestCase(testCaseId))
                .thenReturn(testCase);

        when(testCase.getProblemId())
                .thenReturn(problemId);

        when(testCase.getIsPublic())
                .thenReturn(false);

        when(request.getIsPublic())
                .thenReturn(true);

        when(testCaseRepository
                .findMaxTestCaseOrderByProblemIdAndIsPublic(
                        problemId,
                        true
                ))
                .thenReturn(4);

        // when
        testCaseService.updateTestCase(
                testCaseId,
                request
        );

        // then
        InOrder inOrder =
                inOrder(
                        problemFinder,
                        testCaseRepository
                );

        inOrder.verify(problemFinder)
                .getProblemForUpdate(problemId);

        inOrder.verify(testCaseRepository)
                .findMaxTestCaseOrderByProblemIdAndIsPublic(
                        problemId,
                        true
                );

        verify(testCase)
                .changeIsPublic(true);

        verify(testCase)
                .changeTestCaseOrder(5);
    }
}
