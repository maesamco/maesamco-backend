package com.maesamco.content.application.persistence_service;

import com.maesamco.content.application.finder.ProblemFinder;
import com.maesamco.content.application.finder.TestCaseFinder;
import com.maesamco.content.domain.entity.TestCase;
import com.maesamco.content.domain.repository.TestCaseRepository;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import com.maesamco.content.global.common.pagination.PageQuery;
import com.maesamco.content.global.common.pagination.PageResult;
import com.maesamco.content.application.command.TestCaseCreateCommand;
import com.maesamco.content.application.command.TestCaseUpdateCommand;
import com.maesamco.content.application.result.TestCaseResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TestCaseService 단위 테스트")
class TestCaseServiceTest {

    @Mock
    private TestCaseRepository testCaseRepository;

    @Mock
    private TestCaseFinder testCaseFinder;

    @Mock
    private ProblemFinder problemFinder;

    private TestCaseService testCaseService;

    @BeforeEach
    void setUp() {
        testCaseService = new TestCaseService(testCaseRepository, testCaseFinder, problemFinder);
    }

    // ============================================================
    // 1. createTestCase
    // ============================================================

    @Nested
    @DisplayName("createTestCase")
    class CreateTestCase {

        @Test
        @DisplayName("테스트케이스 생성 시 Problem을 먼저 검증하고 해당 공개 그룹의 마지막 order + 1로 저장한다")
        void createTestCase_success() {

            // given
            UUID problemId = UUID.randomUUID();
            TestCaseCreateCommand request = mock(TestCaseCreateCommand.class);
            TestCase savedTestCase = mock(TestCase.class);

            when(request.getInput()).thenReturn("1 2");
            when(request.getExpectedOutput()).thenReturn("3");
            when(request.getIsPublic()).thenReturn(true);
            when(testCaseRepository.findMaxTestCaseOrderByProblemIdAndIsPublic(problemId, true)).thenReturn(4);
            when(testCaseRepository.save(any(TestCase.class))).thenReturn(savedTestCase);

            // when
            TestCaseResult result = testCaseService.createTestCase(problemId, request);

            // then
            ArgumentCaptor<TestCase> captor = ArgumentCaptor.forClass(TestCase.class);

            verify(problemFinder).lockById(problemId);
            verify(testCaseRepository).findMaxTestCaseOrderByProblemIdAndIsPublic(problemId, true);
            verify(testCaseRepository).save(captor.capture());

            TestCase testCase = captor.getValue();

            assertThat(testCase.getProblemId()).isEqualTo(problemId);
            assertThat(testCase.getInput()).isEqualTo("1 2");
            assertThat(testCase.getExpectedOutput()).isEqualTo("3");
            assertThat(testCase.getIsPublic()).isTrue();
            assertThat(testCase.getTestCaseOrder()).isEqualTo(5);
            assertThat(result).isNotNull();

            verifyNoInteractions(testCaseFinder);
            verifyNoMoreInteractions(problemFinder, testCaseRepository);
        }

        @Test
        @DisplayName("해당 공개 여부 그룹에 테스트케이스가 없으면 order는 1부터 시작한다")
        void createTestCase_emptyGroup_orderStartsAtOne() {

            // given
            UUID problemId = UUID.randomUUID();
            TestCaseCreateCommand request = mock(TestCaseCreateCommand.class);
            TestCase savedTestCase = mock(TestCase.class);

            when(request.getIsPublic()).thenReturn(false);
            when(testCaseRepository.findMaxTestCaseOrderByProblemIdAndIsPublic(problemId, false)).thenReturn(0);
            when(testCaseRepository.save(any(TestCase.class))).thenReturn(savedTestCase);

            // when
            testCaseService.createTestCase(problemId, request);

            // then
            ArgumentCaptor<TestCase> captor = ArgumentCaptor.forClass(TestCase.class);
            verify(testCaseRepository).save(captor.capture());

            assertThat(captor.getValue().getTestCaseOrder()).isEqualTo(1);
            assertThat(captor.getValue().getIsPublic()).isFalse();
        }

        @Test
        @DisplayName("Problem 검증 후 order 조회와 저장을 순서대로 수행한다")
        void createTestCase_executesInCorrectOrder() {

            // given
            UUID problemId = UUID.randomUUID();
            TestCaseCreateCommand request = mock(TestCaseCreateCommand.class);
            TestCase savedTestCase = mock(TestCase.class);

            when(request.getIsPublic()).thenReturn(true);
            when(testCaseRepository.findMaxTestCaseOrderByProblemIdAndIsPublic(problemId, true)).thenReturn(2);
            when(testCaseRepository.save(any(TestCase.class))).thenReturn(savedTestCase);

            // when
            testCaseService.createTestCase(problemId, request);

            // then
            InOrder inOrder = inOrder(problemFinder, testCaseRepository);
            inOrder.verify(problemFinder).lockById(problemId);
            inOrder.verify(testCaseRepository).findMaxTestCaseOrderByProblemIdAndIsPublic(problemId, true);
            inOrder.verify(testCaseRepository).save(any(TestCase.class));
        }
    }

    // ============================================================
    // 2. getTestCase
    // Finder의 NOT_FOUND 정책은 TestCaseFinderServiceTest에서 검증
    // ============================================================

    @Nested
    @DisplayName("getTestCase")
    class GetTestCase {

        @Test
        @DisplayName("테스트케이스 단건 조회 시 TestCaseFinder로 조회하고 상위 Problem의 활성 상태를 함께 확인한다")
        void getTestCase_success() {

            // given
            UUID testCaseId = UUID.randomUUID();
            UUID problemId = UUID.randomUUID();
            TestCase testCase = mock(TestCase.class);

            when(testCase.getProblemId()).thenReturn(problemId);
            when(testCaseFinder.getById(testCaseId)).thenReturn(testCase);

            // when
            TestCaseResult result = testCaseService.getTestCase(testCaseId);

            // then
            assertThat(result).isNotNull();

            verify(testCaseFinder).getById(testCaseId);
            verify(problemFinder).getById(problemId);
            verifyNoInteractions(testCaseRepository);
            verifyNoMoreInteractions(testCaseFinder);
        }

        @Test
        @DisplayName("이슈 #336 — 상위 Problem이 삭제되었거나 없으면 PROBLEM_NOT_FOUND 예외가 발생한다")
        void getTestCase_parentProblemMissing_throws() {

            // given
            UUID testCaseId = UUID.randomUUID();
            UUID problemId = UUID.randomUUID();
            TestCase testCase = mock(TestCase.class);

            when(testCase.getProblemId()).thenReturn(problemId);
            when(testCaseFinder.getById(testCaseId)).thenReturn(testCase);
            when(problemFinder.getById(problemId))
                    .thenThrow(new BusinessException(ErrorCode.PROBLEM_NOT_FOUND));

            // when & then
            assertThatThrownBy(() -> testCaseService.getTestCase(testCaseId))
                    .isInstanceOfSatisfying(BusinessException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PROBLEM_NOT_FOUND));
        }
    }

    // ============================================================
    // 3. getPublicTestCase
    // ============================================================

    @Nested
    @DisplayName("getPublicTestCase")
    class GetPublicTestCase {

        @Test
        @DisplayName("공개 테스트케이스이고 상위 Problem이 유효하면 정상 조회한다")
        void getPublicTestCase_publicTestCase_success() {

            // given
            UUID testCaseId = UUID.randomUUID();
            UUID problemId = UUID.randomUUID();
            TestCase testCase = mock(TestCase.class);

            when(testCaseFinder.getById(testCaseId)).thenReturn(testCase);
            when(testCase.getProblemId()).thenReturn(problemId);
            when(testCase.getIsPublic()).thenReturn(true);

            // when
            TestCaseResult result = testCaseService.getPublicTestCase(testCaseId);

            // then
            assertThat(result).isNotNull();

            verify(testCaseFinder).getById(testCaseId);
            verify(problemFinder).getById(problemId);
            verifyNoInteractions(testCaseRepository);
        }

        @Test
        @DisplayName("비공개 테스트케이스이면 TEST_CASE_ACCESS_DENIED 예외가 발생한다")
        void getPublicTestCase_privateTestCase_throwsAccessDenied() {

            // given
            UUID testCaseId = UUID.randomUUID();
            UUID problemId = UUID.randomUUID();
            TestCase testCase = mock(TestCase.class);

            when(testCaseFinder.getById(testCaseId)).thenReturn(testCase);
            when(testCase.getProblemId()).thenReturn(problemId);
            when(testCase.getIsPublic()).thenReturn(false);

            // when & then
            assertThatThrownBy(() -> testCaseService.getPublicTestCase(testCaseId))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(exception -> {
                        BusinessException businessException = (BusinessException) exception;
                        assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.TEST_CASE_ACCESS_DENIED);
                    });

            verify(testCaseFinder).getById(testCaseId);
            verify(problemFinder).getById(problemId);
            verifyNoInteractions(testCaseRepository);
        }

        @Test
        @DisplayName("공개 여부 검사 전에 상위 Problem의 활성 상태를 먼저 검증한다")
        void getPublicTestCase_checksProblemBeforePublicAccess() {

            // given
            UUID testCaseId = UUID.randomUUID();
            UUID problemId = UUID.randomUUID();
            TestCase testCase = mock(TestCase.class);

            when(testCaseFinder.getById(testCaseId)).thenReturn(testCase);
            when(testCase.getProblemId()).thenReturn(problemId);
            when(testCase.getIsPublic()).thenReturn(true);

            // when
            testCaseService.getPublicTestCase(testCaseId);

            // then
            InOrder inOrder = inOrder(testCaseFinder, problemFinder, testCase);
            inOrder.verify(testCaseFinder).getById(testCaseId);
            inOrder.verify(problemFinder).getById(problemId);
            inOrder.verify(testCase).getIsPublic();
        }
    }

    // ============================================================
    // 4. searchTestCasesPublic
    // ============================================================

    @Nested
    @DisplayName("searchTestCasesPublic")
    class SearchTestCasesPublic {

        @Test
        @DisplayName("Problem을 검증한 후 공개 테스트케이스만 조회한다")
        void searchTestCasesPublic_success() {

            // given
            UUID problemId = UUID.randomUUID();
            PageQuery pageQuery = PageQuery.of(0, 10);
            PageResult<TestCase> page = new PageResult<>(List.of(), pageQuery.page(), pageQuery.size(), 0);

            when(testCaseRepository.searchTestCases(problemId, true, pageQuery)).thenReturn(page);

            // when
            PageResult<TestCaseResult> result = testCaseService.searchTestCasesPublic(problemId, pageQuery);

            // then
            assertThat(result).isNotNull();

            verify(problemFinder).getById(problemId);
            verify(testCaseRepository).searchTestCases(problemId, true, pageQuery);
            verifyNoInteractions(testCaseFinder);
        }

        @Test
        @DisplayName("Problem 검증 후 Repository 조회를 수행한다")
        void searchTestCasesPublic_checksProblemBeforeSearch() {

            // given
            UUID problemId = UUID.randomUUID();
            PageQuery pageQuery = PageQuery.of(1, 20);
            PageResult<TestCase> page = new PageResult<>(List.of(), pageQuery.page(), pageQuery.size(), 0);

            when(testCaseRepository.searchTestCases(problemId, true, pageQuery)).thenReturn(page);

            // when
            testCaseService.searchTestCasesPublic(problemId, pageQuery);

            // then
            InOrder inOrder = inOrder(problemFinder, testCaseRepository);
            inOrder.verify(problemFinder).getById(problemId);
            inOrder.verify(testCaseRepository).searchTestCases(problemId, true, pageQuery);
        }

        @Test
        @DisplayName("전달받은 problemId와 Pageable을 변경하지 않고 공개 조건 true로 Repository에 전달한다")
        void searchTestCasesPublic_passesExactArguments() {

            // given
            UUID problemId = UUID.randomUUID();
            PageQuery pageQuery = PageQuery.of(2, 15);
            PageResult<TestCase> page = new PageResult<>(List.of(), pageQuery.page(), pageQuery.size(), 0);

            when(testCaseRepository.searchTestCases(problemId, true, pageQuery)).thenReturn(page);

            // when
            testCaseService.searchTestCasesPublic(problemId, pageQuery);

            // then
            verify(testCaseRepository).searchTestCases(eq(problemId), eq(true), same(pageQuery));
        }
    }

    // ============================================================
    // 5. searchTestCasesAll
    // ============================================================

    @Nested
    @DisplayName("searchTestCasesAll")
    class SearchTestCasesAll {

        @Test
        @DisplayName("Problem을 검증한 후 공개와 비공개 테스트케이스 전체를 조회한다")
        void searchTestCasesAll_success() {

            // given
            UUID problemId = UUID.randomUUID();
            PageQuery pageQuery = PageQuery.of(0, 10);
            PageResult<TestCase> page = new PageResult<>(List.of(), pageQuery.page(), pageQuery.size(), 0);

            when(testCaseRepository.searchTestCasesAll(problemId, pageQuery)).thenReturn(page);

            // when
            PageResult<TestCaseResult> result = testCaseService.searchTestCasesAll(problemId, pageQuery);

            // then
            assertThat(result).isNotNull();

            verify(problemFinder).getById(problemId);
            verify(testCaseRepository).searchTestCasesAll(problemId, pageQuery);
            verifyNoInteractions(testCaseFinder);
        }

        @Test
        @DisplayName("Problem 검증 후 전체 테스트케이스 Repository 조회를 수행한다")
        void searchTestCasesAll_checksProblemBeforeSearch() {

            // given
            UUID problemId = UUID.randomUUID();
            PageQuery pageQuery = PageQuery.of(1, 20);
            PageResult<TestCase> page = new PageResult<>(List.of(), pageQuery.page(), pageQuery.size(), 0);

            when(testCaseRepository.searchTestCasesAll(problemId, pageQuery)).thenReturn(page);

            // when
            testCaseService.searchTestCasesAll(problemId, pageQuery);

            // then
            InOrder inOrder = inOrder(problemFinder, testCaseRepository);
            inOrder.verify(problemFinder).getById(problemId);
            inOrder.verify(testCaseRepository).searchTestCasesAll(problemId, pageQuery);
        }

        @Test
        @DisplayName("전달받은 problemId와 Pageable을 변경하지 않고 Repository에 전달한다")
        void searchTestCasesAll_passesExactArguments() {

            // given
            UUID problemId = UUID.randomUUID();
            PageQuery pageQuery = PageQuery.of(3, 15);
            PageResult<TestCase> page = new PageResult<>(List.of(), pageQuery.page(), pageQuery.size(), 0);

            when(testCaseRepository.searchTestCasesAll(problemId, pageQuery)).thenReturn(page);

            // when
            testCaseService.searchTestCasesAll(problemId, pageQuery);

            // then
            verify(testCaseRepository).searchTestCasesAll(eq(problemId), same(pageQuery));
        }
    }

    // ============================================================
    // 6. updateTestCase
    // Finder의 NOT_FOUND 정책은 TestCaseFinderServiceTest에서 검증
    // ============================================================

    @Nested
    @DisplayName("updateTestCase")
    class UpdateTestCase {
        @Test
        @DisplayName("이슈 #336 — 상위 Problem이 삭제되었으면 수정할 수 없고 TestCase는 변경되지 않는다")
        void updateTestCase_parentProblemMissing_throwsAndChangesNothing() {

            // given
            UUID testCaseId = UUID.randomUUID();
            UUID problemId = UUID.randomUUID();
            TestCase testCase = mock(TestCase.class);
            TestCaseUpdateCommand request = mock(TestCaseUpdateCommand.class);

            when(testCase.getProblemId()).thenReturn(problemId);
            when(testCaseFinder.getById(testCaseId)).thenReturn(testCase);
            when(problemFinder.getById(problemId))
                    .thenThrow(new BusinessException(ErrorCode.PROBLEM_NOT_FOUND));

            // when & then
            assertThatThrownBy(() -> testCaseService.updateTestCase(testCaseId, request))
                    .isInstanceOfSatisfying(BusinessException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PROBLEM_NOT_FOUND));

            verify(testCase, never()).changeInput(any());
            verify(testCase, never()).changeExpectedOutput(any());
            verify(testCase, never()).changeTestCaseOrder(anyInt());
        }


        @Test
        @DisplayName("input과 expectedOutput이 전달되면 두 값을 모두 수정한다")
        void updateTestCase_inputAndExpectedOutput_success() {

            // given
            UUID testCaseId = UUID.randomUUID();
            TestCase testCase = mock(TestCase.class);
            TestCaseUpdateCommand request = mock(TestCaseUpdateCommand.class);

            when(testCaseFinder.getById(testCaseId)).thenReturn(testCase);
            when(request.getInput()).thenReturn("10 20");
            when(request.getExpectedOutput()).thenReturn("30");

            // when
            TestCaseResult result = testCaseService.updateTestCase(testCaseId, request);

            // then
            assertThat(result).isNotNull();

            verify(testCase).changeInput("10 20");
            verify(testCase).changeExpectedOutput("30");
            verifyNoInteractions(testCaseRepository);
            verify(problemFinder).getById(any());
            verifyNoMoreInteractions(problemFinder);
        }

        @Test
        @DisplayName("input만 전달되면 input만 수정한다")
        void updateTestCase_onlyInput_changesInputOnly() {

            // given
            UUID testCaseId = UUID.randomUUID();
            TestCase testCase = mock(TestCase.class);
            TestCaseUpdateCommand request = mock(TestCaseUpdateCommand.class);

            when(testCaseFinder.getById(testCaseId)).thenReturn(testCase);
            when(request.getInput()).thenReturn("새 입력");

            // when
            testCaseService.updateTestCase(testCaseId, request);

            // then
            verify(testCase).changeInput("새 입력");
            verify(testCase, never()).changeExpectedOutput(any());
            verifyNoInteractions(testCaseRepository);
            verify(problemFinder).getById(any());
            verifyNoMoreInteractions(problemFinder);
        }

        @Test
        @DisplayName("expectedOutput만 전달되면 expectedOutput만 수정한다")
        void updateTestCase_onlyExpectedOutput_changesExpectedOutputOnly() {

            // given
            UUID testCaseId = UUID.randomUUID();
            TestCase testCase = mock(TestCase.class);
            TestCaseUpdateCommand request = mock(TestCaseUpdateCommand.class);

            when(testCaseFinder.getById(testCaseId)).thenReturn(testCase);
            when(request.getExpectedOutput()).thenReturn("새 출력");

            // when
            testCaseService.updateTestCase(testCaseId, request);

            // then
            verify(testCase, never()).changeInput(any());
            verify(testCase).changeExpectedOutput("새 출력");
            verifyNoInteractions(testCaseRepository);
            verify(problemFinder).getById(any());
            verifyNoMoreInteractions(problemFinder);
        }

        @Test
        @DisplayName("공개 여부가 변경되면 Problem을 쓰기 잠금으로 조회하고 새 그룹의 마지막 order + 1을 부여한다")
        void updateTestCase_isPublicChanged_assignsNewGroupOrder() {

            // given
            UUID testCaseId = UUID.randomUUID();
            UUID problemId = UUID.randomUUID();
            TestCase testCase = mock(TestCase.class);
            TestCaseUpdateCommand request = mock(TestCaseUpdateCommand.class);

            when(testCaseFinder.getById(testCaseId)).thenReturn(testCase);
            when(testCase.getProblemId()).thenReturn(problemId);
            when(testCase.getIsPublic()).thenReturn(false);
            when(request.getIsPublic()).thenReturn(true);
            when(testCaseRepository.findMaxTestCaseOrderByProblemIdAndIsPublic(problemId, true)).thenReturn(5);

            // when
            testCaseService.updateTestCase(testCaseId, request);

            // then
            verify(problemFinder).lockById(problemId);
            verify(testCaseRepository).findMaxTestCaseOrderByProblemIdAndIsPublic(problemId, true);
            verify(testCase).changeIsPublic(true);
            verify(testCase).changeTestCaseOrder(6);
        }

        @Test
        @DisplayName("공개 여부가 기존 값과 같으면 Problem 잠금과 order 재계산을 수행하지 않는다")
        void updateTestCase_sameIsPublic_doesNotRecalculateOrder() {
            // given
            UUID testCaseId = UUID.randomUUID();
            UUID problemId = UUID.randomUUID();

            TestCase testCase = TestCase.createByAdmin(
                    problemId,
                    "input",
                    "output",
                    true,
                    1
            );

            TestCaseUpdateCommand request = mock(TestCaseUpdateCommand.class);

            when(testCaseFinder.getById(testCaseId)).thenReturn(testCase);
            when(request.getIsPublic()).thenReturn(true);
            when(request.getTestCaseOrder()).thenReturn(null);

            // when
            TestCaseResult result = testCaseService.updateTestCase(testCaseId, request);

            // then
            assertThat(result).isNotNull();
            assertThat(testCase.getIsPublic()).isTrue();
            assertThat(testCase.getTestCaseOrder()).isEqualTo(1);

            verify(testCaseFinder).getById(testCaseId);
            verifyNoInteractions(testCaseRepository);
            verify(problemFinder).getById(any());
            verifyNoMoreInteractions(problemFinder);
        }

        @Test
        @DisplayName("isPublic이 null이면 공개 여부와 order를 변경하지 않는다")
        void updateTestCase_isPublicNull_doesNotChangePublicGroup() {

            // given
            UUID testCaseId = UUID.randomUUID();
            TestCase testCase = mock(TestCase.class);
            TestCaseUpdateCommand request = mock(TestCaseUpdateCommand.class);

            when(testCaseFinder.getById(testCaseId)).thenReturn(testCase);
            when(request.getIsPublic()).thenReturn(null);
            when(request.getTestCaseOrder()).thenReturn(null);

            // when
            testCaseService.updateTestCase(testCaseId, request);

            // then
            verify(testCaseFinder).getById(testCaseId);
            verify(testCase, never()).changeIsPublic(anyBoolean());
            verify(testCase, never()).changeTestCaseOrder(anyInt());
            verifyNoInteractions(testCaseRepository);
            verify(problemFinder).getById(any());
            verifyNoMoreInteractions(problemFinder);
        }

        @Test
        @DisplayName("testCaseOrder가 전달되면 해당 값으로 직접 변경한다")
        void updateTestCase_testCaseOrderProvided_changesOrder() {

            // given
            UUID testCaseId = UUID.randomUUID();
            TestCase testCase = mock(TestCase.class);
            TestCaseUpdateCommand request = mock(TestCaseUpdateCommand.class);

            when(testCaseFinder.getById(testCaseId)).thenReturn(testCase);
            when(request.getTestCaseOrder()).thenReturn(10);

            // when
            testCaseService.updateTestCase(testCaseId, request);

            // then
            verify(testCase).changeTestCaseOrder(10);
            verifyNoInteractions(testCaseRepository);
            verify(problemFinder).getById(any());
            verifyNoMoreInteractions(problemFinder);
        }

        @Test
        @DisplayName("공개 여부 변경과 testCaseOrder가 함께 전달되면 명시적인 testCaseOrder를 마지막에 적용한다")
        void updateTestCase_isPublicAndExplicitOrder_explicitOrderHasPriority() {

            // given
            UUID testCaseId = UUID.randomUUID();
            UUID problemId = UUID.randomUUID();
            TestCase testCase = mock(TestCase.class);
            TestCaseUpdateCommand request = mock(TestCaseUpdateCommand.class);

            when(testCaseFinder.getById(testCaseId)).thenReturn(testCase);
            when(testCase.getProblemId()).thenReturn(problemId);
            when(testCase.getIsPublic()).thenReturn(false);
            when(request.getIsPublic()).thenReturn(true);
            when(request.getTestCaseOrder()).thenReturn(99);
            when(testCaseRepository.findMaxTestCaseOrderByProblemIdAndIsPublic(problemId, true)).thenReturn(5);

            // when
            testCaseService.updateTestCase(testCaseId, request);

            // then
            InOrder inOrder = inOrder(testCase);
            inOrder.verify(testCase).changeIsPublic(true);
            inOrder.verify(testCase).changeTestCaseOrder(6);
            inOrder.verify(testCase).changeTestCaseOrder(99);
        }

        @Test
        @DisplayName("모든 수정 필드가 null이면 TestCase의 값을 변경하지 않는다")
        void updateTestCase_allFieldsNull_doesNotChangeTestCase() {
            // given
            UUID testCaseId = UUID.randomUUID();
            UUID problemId = UUID.randomUUID();

            TestCase testCase = TestCase.createByAdmin(
                    problemId,
                    "기존 입력",
                    "기존 출력",
                    true,
                    3
            );

            TestCaseUpdateCommand request = mock(TestCaseUpdateCommand.class);

            when(request.getIsPublic()).thenReturn(null);
            when(request.getTestCaseOrder()).thenReturn(null);
            when(testCaseFinder.getById(testCaseId)).thenReturn(testCase);

            // when
            TestCaseResult result = testCaseService.updateTestCase(testCaseId, request);

            // then
            assertThat(result).isNotNull();
            assertThat(testCase.getInput()).isEqualTo("기존 입력");
            assertThat(testCase.getExpectedOutput()).isEqualTo("기존 출력");
            assertThat(testCase.getIsPublic()).isTrue();
            assertThat(testCase.getTestCaseOrder()).isEqualTo(3);

            verify(testCaseFinder).getById(testCaseId);
            verifyNoInteractions(testCaseRepository);
            verify(problemFinder).getById(any());
            verifyNoMoreInteractions(problemFinder);
        }

        @Test
        @DisplayName("테스트케이스 수정 시 Repository.save를 명시적으로 호출하지 않는다")
        void updateTestCase_doesNotCallSave() {

            // given
            UUID testCaseId = UUID.randomUUID();
            TestCase testCase = mock(TestCase.class);
            TestCaseUpdateCommand request = mock(TestCaseUpdateCommand.class);

            when(testCaseFinder.getById(testCaseId)).thenReturn(testCase);
            when(request.getInput()).thenReturn("수정된 입력");

            // when
            testCaseService.updateTestCase(testCaseId, request);

            // then
            verify(testCase).changeInput("수정된 입력");
            verify(testCaseRepository, never()).save(any(TestCase.class));
        }
    }

    // ============================================================
    // 7. deleteTestCase
    // Finder의 NOT_FOUND 정책은 TestCaseFinderServiceTest에서 검증
    // ============================================================

    @Nested
    @DisplayName("deleteTestCase")
    class DeleteTestCase {

        @Test
        @DisplayName("이슈 #336 — 상위 Problem이 삭제되었으면 삭제할 수 없고 softDelete는 호출되지 않는다")
        void deleteTestCase_parentProblemMissing_throwsAndDoesNotDelete() {

            // given
            UUID testCaseId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            UUID problemId = UUID.randomUUID();
            TestCase testCase = mock(TestCase.class);

            when(testCase.getProblemId()).thenReturn(problemId);
            when(testCaseFinder.getById(testCaseId)).thenReturn(testCase);
            when(problemFinder.getById(problemId))
                    .thenThrow(new BusinessException(ErrorCode.PROBLEM_NOT_FOUND));

            // when & then
            assertThatThrownBy(() -> testCaseService.deleteTestCase(testCaseId, userId))
                    .isInstanceOfSatisfying(BusinessException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PROBLEM_NOT_FOUND));

            verify(testCase, never()).softDelete(any());
        }

        @Test
        @DisplayName("테스트케이스 삭제 시 요청 사용자 ID로 softDelete한다")
        void deleteTestCase_success() {

            // given
            UUID testCaseId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            TestCase testCase = mock(TestCase.class);

            when(testCaseFinder.getById(testCaseId)).thenReturn(testCase);

            // when
            testCaseService.deleteTestCase(testCaseId, userId);

            // then
            verify(testCaseFinder).getById(testCaseId);
            verify(testCase).softDelete(userId);
            verifyNoInteractions(testCaseRepository);
            verify(problemFinder).getById(any());
            verifyNoMoreInteractions(problemFinder);
        }

        @Test
        @DisplayName("테스트케이스 삭제 시 Repository의 delete나 save를 직접 호출하지 않는다")
        void deleteTestCase_doesNotCallRepositoryModification() {

            // given
            UUID testCaseId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            TestCase testCase = mock(TestCase.class);

            when(testCaseFinder.getById(testCaseId)).thenReturn(testCase);

            // when
            testCaseService.deleteTestCase(testCaseId, userId);

            // then
            verify(testCase).softDelete(userId);
            verifyNoInteractions(testCaseRepository);
            verify(problemFinder).getById(any());
            verifyNoMoreInteractions(problemFinder);
        }
    }
}