package com.maesamco.content.application.finder_service;

import com.maesamco.content.application.finder_service.TestCaseFinderService;
import com.maesamco.content.domain.entity.TestCase;
import com.maesamco.content.domain.repository.TestCaseRepository;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TestCaseFinderService 단위 테스트")
class TestCaseFinderServiceTest {

    @Mock
    private TestCaseRepository testCaseRepository;

    private TestCaseFinderService testCaseFinderService;

    @BeforeEach
    void setUp() {
        testCaseFinderService = new TestCaseFinderService(testCaseRepository);
    }

    // ============================================================
    // 1. getTestCase - 정상 조회
    // ============================================================

    @Nested
    @DisplayName("getTestCase 성공")
    class GetTestCaseSuccess {

        @Test
        @DisplayName("존재하는 testCaseId로 조회하면 Repository에서 조회한 TestCase를 그대로 반환한다")
        void getTestCase_existingTestCase_returnsTestCase() {

            // given
            UUID testCaseId = UUID.randomUUID();
            TestCase testCase = mock(TestCase.class);

            when(testCaseRepository.findById(testCaseId)).thenReturn(Optional.of(testCase));

            // when
            TestCase result = testCaseFinderService.getById(testCaseId);

            // then
            assertThat(result).isNotNull();
            assertThat(result).isSameAs(testCase);

            verify(testCaseRepository).findById(testCaseId);
            verifyNoMoreInteractions(testCaseRepository);
        }

        @Test
        @DisplayName("서로 다른 testCaseId로 조회하면 각각 정확한 ID를 Repository에 전달한다")
        void getTestCase_differentIds_queriesExactIds() {

            // given
            UUID firstTestCaseId = UUID.randomUUID();
            UUID secondTestCaseId = UUID.randomUUID();

            TestCase firstTestCase = mock(TestCase.class);
            TestCase secondTestCase = mock(TestCase.class);

            when(testCaseRepository.findById(firstTestCaseId)).thenReturn(Optional.of(firstTestCase));
            when(testCaseRepository.findById(secondTestCaseId)).thenReturn(Optional.of(secondTestCase));

            // when
            TestCase firstResult = testCaseFinderService.getById(firstTestCaseId);
            TestCase secondResult = testCaseFinderService.getById(secondTestCaseId);

            // then
            assertThat(firstResult).isSameAs(firstTestCase);
            assertThat(secondResult).isSameAs(secondTestCase);
            assertThat(firstResult).isNotSameAs(secondResult);

            verify(testCaseRepository).findById(firstTestCaseId);
            verify(testCaseRepository).findById(secondTestCaseId);
            verifyNoMoreInteractions(testCaseRepository);
        }
    }

    // ============================================================
    // 2. getTestCase - 조회 실패
    // ============================================================

    @Nested
    @DisplayName("getTestCase 실패")
    class GetTestCaseFailure {

        @Test
        @DisplayName("존재하지 않는 testCaseId로 조회하면 TEST_CASE_NOT_FOUND 예외가 발생한다")
        void getTestCase_notExistingTestCase_throwsTestCaseNotFound() {

            // given
            UUID testCaseId = UUID.randomUUID();

            when(testCaseRepository.findById(testCaseId)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> testCaseFinderService.getById(testCaseId))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(exception -> {
                        BusinessException businessException = (BusinessException) exception;
                        assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.TEST_CASE_NOT_FOUND);
                    });

            verify(testCaseRepository).findById(testCaseId);
            verifyNoMoreInteractions(testCaseRepository);
        }

        @Test
        @DisplayName("존재하지 않는 TestCase 조회 시 Repository를 한 번만 조회한다")
        void getTestCase_notExistingTestCase_queriesRepositoryOnlyOnce() {

            // given
            UUID testCaseId = UUID.randomUUID();

            when(testCaseRepository.findById(testCaseId)).thenReturn(Optional.empty());

            // when
            assertThatThrownBy(() -> testCaseFinderService.getById(testCaseId))
                    .isInstanceOf(BusinessException.class);

            // then
            verify(testCaseRepository, times(1)).findById(testCaseId);
            verifyNoMoreInteractions(testCaseRepository);
        }
    }

    // ============================================================
    // 3. Repository 예외 전파
    // ============================================================

    @Nested
    @DisplayName("Repository 예외 전파")
    class RepositoryExceptionPropagation {

        @Test
        @DisplayName("Repository에서 예상하지 못한 예외가 발생하면 TEST_CASE_NOT_FOUND로 변환하지 않고 그대로 전파한다")
        void getTestCase_repositoryThrowsUnexpectedException_propagatesException() {

            // given
            UUID testCaseId = UUID.randomUUID();
            RuntimeException repositoryException = new RuntimeException("test case repository failure");

            when(testCaseRepository.findById(testCaseId)).thenThrow(repositoryException);

            // when & then
            assertThatThrownBy(() -> testCaseFinderService.getById(testCaseId))
                    .isSameAs(repositoryException);

            verify(testCaseRepository).findById(testCaseId);
            verifyNoMoreInteractions(testCaseRepository);
        }
    }
}