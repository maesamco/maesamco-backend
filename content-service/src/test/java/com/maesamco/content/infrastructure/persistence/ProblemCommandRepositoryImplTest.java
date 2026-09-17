package com.maesamco.content.infrastructure.persistence;

import com.maesamco.content.domain.entity.problem.Problem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProblemCommandRepositoryImpl 테스트")
class ProblemCommandRepositoryImplTest {

    @Mock
    private SpringDataProblemRepository springDataProblemRepository;

    @InjectMocks
    private ProblemCommandRepositoryImpl problemCommandRepository;

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName(
                "Problem 저장을 SpringDataProblemRepository에 위임한다"
        )
        void save_delegatesToSpringDataRepository() {

            // given
            Problem problem =
                    mockProblem();

            when(
                    springDataProblemRepository.save(
                            problem
                    )
            ).thenReturn(
                    problem
            );

            // when
            problemCommandRepository.save(
                    problem
            );

            // then
            verify(
                    springDataProblemRepository
            ).save(
                    problem
            );
        }

        @Test
        @DisplayName(
                "save 호출 시 전달받은 Problem 객체를 그대로 SpringDataProblemRepository에 전달한다"
        )
        void save_passesSameProblemInstance() {

            // given
            Problem problem =
                    mockProblem();

            when(
                    springDataProblemRepository.save(
                            problem
                    )
            ).thenReturn(
                    problem
            );

            // when
            problemCommandRepository.save(
                    problem
            );

            // then
            verify(
                    springDataProblemRepository
            ).save(
                    same(problem)
            );
        }

        @Test
        @DisplayName(
                "SpringDataProblemRepository가 반환한 Problem 객체를 그대로 반환한다"
        )
        void save_returnsProblemReturnedBySpringDataRepository() {

            // given
            Problem givenProblem =
                    mockProblem();

            Problem savedProblem =
                    mockProblem();

            when(
                    springDataProblemRepository.save(
                            givenProblem
                    )
            ).thenReturn(
                    savedProblem
            );

            // when
            Problem result =
                    problemCommandRepository.save(
                            givenProblem
                    );

            // then
            assertThat(result)
                    .isSameAs(savedProblem);
        }

        @Test
        @DisplayName(
                "저장 전 Problem과 저장 후 반환된 Problem이 다른 객체여도 저장 결과 객체를 반환한다"
        )
        void save_whenReturnedInstanceIsDifferent_returnsReturnedInstance() {

            // given
            Problem originalProblem =
                    mockProblem();

            Problem persistedProblem =
                    mockProblem();

            when(
                    springDataProblemRepository.save(
                            originalProblem
                    )
            ).thenReturn(
                    persistedProblem
            );

            // when
            Problem result =
                    problemCommandRepository.save(
                            originalProblem
                    );

            // then
            assertThat(result)
                    .isNotSameAs(originalProblem);

            assertThat(result)
                    .isSameAs(persistedProblem);
        }

        @Test
        @DisplayName(
                "save는 SpringDataProblemRepository.save를 정확히 한 번 호출한다"
        )
        void save_callsSpringDataSaveExactlyOnce() {

            // given
            Problem problem =
                    mockProblem();

            when(
                    springDataProblemRepository.save(
                            problem
                    )
            ).thenReturn(
                    problem
            );

            // when
            problemCommandRepository.save(
                    problem
            );

            // then
            verify(
                    springDataProblemRepository,
                    times(1)
            ).save(
                    problem
            );
        }

        @Test
        @DisplayName(
                "save는 findByIdForUpdate를 호출하지 않는다"
        )
        void save_doesNotCallFindByIdForUpdate() {

            // given
            Problem problem =
                    mockProblem();

            when(
                    springDataProblemRepository.save(
                            problem
                    )
            ).thenReturn(
                    problem
            );

            // when
            problemCommandRepository.save(
                    problem
            );

            // then
            verify(
                    springDataProblemRepository,
                    never()
            ).findByIdForUpdate(
                    any(UUID.class)
            );
        }

        @Test
        @DisplayName(
                "save는 flush를 호출하지 않는다"
        )
        void save_doesNotCallFlush() {

            // given
            Problem problem =
                    mockProblem();

            when(
                    springDataProblemRepository.save(
                            problem
                    )
            ).thenReturn(
                    problem
            );

            // when
            problemCommandRepository.save(
                    problem
            );

            // then
            verify(
                    springDataProblemRepository,
                    never()
            ).flush();
        }

        @Test
        @DisplayName(
                "save 수행 시 SpringDataProblemRepository에는 save 이외의 상호작용이 발생하지 않는다"
        )
        void save_hasNoUnexpectedInteractions() {

            // given
            Problem problem =
                    mockProblem();

            when(
                    springDataProblemRepository.save(
                            problem
                    )
            ).thenReturn(
                    problem
            );

            // when
            problemCommandRepository.save(
                    problem
            );

            // then
            verify(
                    springDataProblemRepository
            ).save(
                    problem
            );

            verifyNoMoreInteractions(
                    springDataProblemRepository
            );
        }

        @Test
        @DisplayName(
                "save에 전달된 Problem을 ArgumentCaptor로 확인할 수 있다"
        )
        void save_capturesExactProblemArgument() {

            // given
            Problem problem =
                    mockProblem();

            when(
                    springDataProblemRepository.save(
                            problem
                    )
            ).thenReturn(
                    problem
            );

            ArgumentCaptor<Problem> captor =
                    ArgumentCaptor.forClass(
                            Problem.class
                    );

            // when
            problemCommandRepository.save(
                    problem
            );

            // then
            verify(
                    springDataProblemRepository
            ).save(
                    captor.capture()
            );

            Problem capturedProblem =
                    captor.getValue();

            assertThat(capturedProblem)
                    .isSameAs(problem);
        }

        @Test
        @DisplayName(
                "SpringDataProblemRepository.save에서 RuntimeException이 발생하면 예외를 그대로 전파한다"
        )
        void save_whenSpringDataRepositoryThrows_propagatesException() {

            // given
            Problem problem =
                    mockProblem();

            RuntimeException exception =
                    new RuntimeException(
                            "save failed"
                    );

            when(
                    springDataProblemRepository.save(
                            problem
                    )
            ).thenThrow(
                    exception
            );

            // when & then
            assertThatThrownBy(
                    () ->
                            problemCommandRepository.save(
                                    problem
                            )
            )
                    .isSameAs(exception);
        }

        @Test
        @DisplayName(
                "save 실패 시 예외를 RepositoryImpl 내부에서 다른 예외로 변환하지 않는다"
        )
        void save_whenSaveFails_doesNotTranslateException() {

            // given
            Problem problem =
                    mockProblem();

            IllegalStateException exception =
                    new IllegalStateException(
                            "persistence failure"
                    );

            when(
                    springDataProblemRepository.save(
                            problem
                    )
            ).thenThrow(
                    exception
            );

            // when & then
            assertThatThrownBy(
                    () ->
                            problemCommandRepository.save(
                                    problem
                            )
            )
                    .isInstanceOf(
                            IllegalStateException.class
                    )
                    .hasMessage(
                            "persistence failure"
                    )
                    .isSameAs(
                            exception
                    );
        }

        @Test
        @DisplayName(
                "save 실패 이후 flush를 추가로 호출하지 않는다"
        )
        void save_whenSaveFails_doesNotFlush() {

            // given
            Problem problem =
                    mockProblem();

            when(
                    springDataProblemRepository.save(
                            problem
                    )
            ).thenThrow(
                    new RuntimeException(
                            "save failed"
                    )
            );

            // when
            try {
                problemCommandRepository.save(
                        problem
                );
            } catch (RuntimeException ignored) {
            }

            // then
            verify(
                    springDataProblemRepository,
                    never()
            ).flush();
        }

        @Test
        @DisplayName(
                "save를 두 번 호출하면 SpringDataProblemRepository.save도 두 번 호출한다"
        )
        void save_whenCalledTwice_delegatesTwice() {

            // given
            Problem firstProblem =
                    mockProblem();

            Problem secondProblem =
                    mockProblem();

            when(
                    springDataProblemRepository.save(
                            firstProblem
                    )
            ).thenReturn(
                    firstProblem
            );

            when(
                    springDataProblemRepository.save(
                            secondProblem
                    )
            ).thenReturn(
                    secondProblem
            );

            // when
            problemCommandRepository.save(
                    firstProblem
            );

            problemCommandRepository.save(
                    secondProblem
            );

            // then
            verify(
                    springDataProblemRepository
            ).save(
                    firstProblem
            );

            verify(
                    springDataProblemRepository
            ).save(
                    secondProblem
            );

            verify(
                    springDataProblemRepository,
                    times(2)
            ).save(
                    any(Problem.class)
            );
        }

        @Test
        @DisplayName(
                "연속된 save 호출의 순서를 그대로 유지한다"
        )
        void save_multipleCalls_preservesInvocationOrder() {

            // given
            Problem firstProblem =
                    mockProblem();

            Problem secondProblem =
                    mockProblem();

            when(
                    springDataProblemRepository.save(
                            firstProblem
                    )
            ).thenReturn(
                    firstProblem
            );

            when(
                    springDataProblemRepository.save(
                            secondProblem
                    )
            ).thenReturn(
                    secondProblem
            );

            // when
            problemCommandRepository.save(
                    firstProblem
            );

            problemCommandRepository.save(
                    secondProblem
            );

            // then
            InOrder inOrder =
                    inOrder(
                            springDataProblemRepository
                    );

            inOrder.verify(
                    springDataProblemRepository
            ).save(
                    firstProblem
            );

            inOrder.verify(
                    springDataProblemRepository
            ).save(
                    secondProblem
            );

            inOrder.verifyNoMoreInteractions();
        }
    }

    @Nested
    @DisplayName("findByIdForUpdate")
    class FindByIdForUpdate {

        @Test
        @DisplayName(
                "Problem이 존재하면 잠금 조회 결과를 반환한다"
        )
        void findByIdForUpdate_existingProblem_returnsProblem() {

            // given
            UUID problemId =
                    UUID.randomUUID();

            Problem problem =
                    mockProblem();

            when(
                    springDataProblemRepository.findByIdForUpdate(
                            problemId
                    )
            ).thenReturn(
                    Optional.of(problem)
            );

            // when
            Optional<Problem> result =
                    problemCommandRepository.findByIdForUpdate(
                            problemId
                    );

            // then
            assertThat(result)
                    .isPresent();

            assertThat(result.get())
                    .isSameAs(problem);
        }

        @Test
        @DisplayName(
                "Problem이 존재하지 않으면 Optional.empty를 반환한다"
        )
        void findByIdForUpdate_nonExistingProblem_returnsEmpty() {

            // given
            UUID problemId =
                    UUID.randomUUID();

            when(
                    springDataProblemRepository.findByIdForUpdate(
                            problemId
                    )
            ).thenReturn(
                    Optional.empty()
            );

            // when
            Optional<Problem> result =
                    problemCommandRepository.findByIdForUpdate(
                            problemId
                    );

            // then
            assertThat(result)
                    .isEmpty();
        }

        @Test
        @DisplayName(
                "findByIdForUpdate는 SpringDataProblemRepository의 잠금 조회 메서드에 위임한다"
        )
        void findByIdForUpdate_delegatesToLockingRepositoryMethod() {

            // given
            UUID problemId =
                    UUID.randomUUID();

            when(
                    springDataProblemRepository.findByIdForUpdate(
                            problemId
                    )
            ).thenReturn(
                    Optional.empty()
            );

            // when
            problemCommandRepository.findByIdForUpdate(
                    problemId
            );

            // then
            verify(
                    springDataProblemRepository
            ).findByIdForUpdate(
                    problemId
            );
        }

        @Test
        @DisplayName(
                "findByIdForUpdate에 전달된 UUID를 그대로 SpringDataProblemRepository에 전달한다"
        )
        void findByIdForUpdate_passesSameProblemId() {

            // given
            UUID problemId =
                    UUID.randomUUID();

            when(
                    springDataProblemRepository.findByIdForUpdate(
                            problemId
                    )
            ).thenReturn(
                    Optional.empty()
            );

            // when
            problemCommandRepository.findByIdForUpdate(
                    problemId
            );

            // then
            verify(
                    springDataProblemRepository
            ).findByIdForUpdate(
                    same(problemId)
            );
        }

        @Test
        @DisplayName(
                "findByIdForUpdate의 UUID 인자를 ArgumentCaptor로 확인한다"
        )
        void findByIdForUpdate_capturesProblemId() {

            // given
            UUID problemId =
                    UUID.randomUUID();

            when(
                    springDataProblemRepository.findByIdForUpdate(
                            problemId
                    )
            ).thenReturn(
                    Optional.empty()
            );

            ArgumentCaptor<UUID> captor =
                    ArgumentCaptor.forClass(
                            UUID.class
                    );

            // when
            problemCommandRepository.findByIdForUpdate(
                    problemId
            );

            // then
            verify(
                    springDataProblemRepository
            ).findByIdForUpdate(
                    captor.capture()
            );

            UUID capturedProblemId =
                    captor.getValue();

            assertThat(capturedProblemId)
                    .isEqualTo(problemId);

            assertThat(capturedProblemId)
                    .isSameAs(problemId);
        }

        @Test
        @DisplayName(
                "SpringDataProblemRepository가 반환한 Optional을 그대로 반환한다"
        )
        void findByIdForUpdate_returnsSameOptionalInstance() {

            // given
            UUID problemId =
                    UUID.randomUUID();

            Problem problem =
                    mockProblem();

            Optional<Problem> expected =
                    Optional.of(problem);

            when(
                    springDataProblemRepository.findByIdForUpdate(
                            problemId
                    )
            ).thenReturn(
                    expected
            );

            // when
            Optional<Problem> result =
                    problemCommandRepository.findByIdForUpdate(
                            problemId
                    );

            // then
            assertThat(result)
                    .isSameAs(expected);
        }

        @Test
        @DisplayName(
                "findByIdForUpdate는 잠금 조회를 정확히 한 번 호출한다"
        )
        void findByIdForUpdate_callsLockingQueryExactlyOnce() {

            // given
            UUID problemId =
                    UUID.randomUUID();

            when(
                    springDataProblemRepository.findByIdForUpdate(
                            problemId
                    )
            ).thenReturn(
                    Optional.empty()
            );

            // when
            problemCommandRepository.findByIdForUpdate(
                    problemId
            );

            // then
            verify(
                    springDataProblemRepository,
                    times(1)
            ).findByIdForUpdate(
                    problemId
            );
        }

        @Test
        @DisplayName(
                "findByIdForUpdate는 save를 호출하지 않는다"
        )
        void findByIdForUpdate_doesNotCallSave() {

            // given
            UUID problemId =
                    UUID.randomUUID();

            when(
                    springDataProblemRepository.findByIdForUpdate(
                            problemId
                    )
            ).thenReturn(
                    Optional.empty()
            );

            // when
            problemCommandRepository.findByIdForUpdate(
                    problemId
            );

            // then
            verify(
                    springDataProblemRepository,
                    never()
            ).save(
                    any(Problem.class)
            );
        }

        @Test
        @DisplayName(
                "findByIdForUpdate는 flush를 호출하지 않는다"
        )
        void findByIdForUpdate_doesNotCallFlush() {

            // given
            UUID problemId =
                    UUID.randomUUID();

            when(
                    springDataProblemRepository.findByIdForUpdate(
                            problemId
                    )
            ).thenReturn(
                    Optional.empty()
            );

            // when
            problemCommandRepository.findByIdForUpdate(
                    problemId
            );

            // then
            verify(
                    springDataProblemRepository,
                    never()
            ).flush();
        }

        @Test
        @DisplayName(
                "findByIdForUpdate 수행 시 잠금 조회 외의 다른 상호작용이 발생하지 않는다"
        )
        void findByIdForUpdate_hasNoUnexpectedInteractions() {

            // given
            UUID problemId =
                    UUID.randomUUID();

            when(
                    springDataProblemRepository.findByIdForUpdate(
                            problemId
                    )
            ).thenReturn(
                    Optional.empty()
            );

            // when
            problemCommandRepository.findByIdForUpdate(
                    problemId
            );

            // then
            verify(
                    springDataProblemRepository
            ).findByIdForUpdate(
                    problemId
            );

            verifyNoMoreInteractions(
                    springDataProblemRepository
            );
        }

        @Test
        @DisplayName(
                "SpringDataProblemRepository의 잠금 조회에서 RuntimeException이 발생하면 그대로 전파한다"
        )
        void findByIdForUpdate_whenRepositoryThrows_propagatesException() {

            // given
            UUID problemId =
                    UUID.randomUUID();

            RuntimeException exception =
                    new RuntimeException(
                            "findByIdForUpdate failed"
                    );

            when(
                    springDataProblemRepository.findByIdForUpdate(
                            problemId
                    )
            ).thenThrow(
                    exception
            );

            // when & then
            assertThatThrownBy(
                    () ->
                            problemCommandRepository.findByIdForUpdate(
                                    problemId
                            )
            )
                    .isSameAs(exception);
        }

        @Test
        @DisplayName(
                "잠금 조회 예외를 RepositoryImpl에서 임의의 다른 예외로 변환하지 않는다"
        )
        void findByIdForUpdate_doesNotTranslateException() {

            // given
            UUID problemId =
                    UUID.randomUUID();

            IllegalStateException exception =
                    new IllegalStateException(
                            "lock query failed"
                    );

            when(
                    springDataProblemRepository.findByIdForUpdate(
                            problemId
                    )
            ).thenThrow(
                    exception
            );

            // when & then
            assertThatThrownBy(
                    () ->
                            problemCommandRepository.findByIdForUpdate(
                                    problemId
                            )
            )
                    .isInstanceOf(
                            IllegalStateException.class
                    )
                    .hasMessage(
                            "lock query failed"
                    )
                    .isSameAs(
                            exception
                    );
        }

        @Test
        @DisplayName(
                "잠금 조회 실패 이후 save를 추가로 호출하지 않는다"
        )
        void findByIdForUpdate_whenQueryFails_doesNotCallSave() {

            // given
            UUID problemId =
                    UUID.randomUUID();

            when(
                    springDataProblemRepository.findByIdForUpdate(
                            problemId
                    )
            ).thenThrow(
                    new RuntimeException(
                            "lock failed"
                    )
            );

            // when
            try {
                problemCommandRepository.findByIdForUpdate(
                        problemId
                );
            } catch (RuntimeException ignored) {
            }

            // then
            verify(
                    springDataProblemRepository,
                    never()
            ).save(
                    any(Problem.class)
            );
        }

        @Test
        @DisplayName(
                "잠금 조회 실패 이후 flush를 추가로 호출하지 않는다"
        )
        void findByIdForUpdate_whenQueryFails_doesNotCallFlush() {

            // given
            UUID problemId =
                    UUID.randomUUID();

            when(
                    springDataProblemRepository.findByIdForUpdate(
                            problemId
                    )
            ).thenThrow(
                    new RuntimeException(
                            "lock failed"
                    )
            );

            // when
            try {
                problemCommandRepository.findByIdForUpdate(
                        problemId
                );
            } catch (RuntimeException ignored) {
            }

            // then
            verify(
                    springDataProblemRepository,
                    never()
            ).flush();
        }

        @Test
        @DisplayName(
                "서로 다른 Problem ID로 잠금 조회하면 각각의 ID로 Repository를 호출한다"
        )
        void findByIdForUpdate_multipleIds_delegatesEachId() {

            // given
            UUID firstProblemId =
                    UUID.randomUUID();

            UUID secondProblemId =
                    UUID.randomUUID();

            when(
                    springDataProblemRepository.findByIdForUpdate(
                            firstProblemId
                    )
            ).thenReturn(
                    Optional.empty()
            );

            when(
                    springDataProblemRepository.findByIdForUpdate(
                            secondProblemId
                    )
            ).thenReturn(
                    Optional.empty()
            );

            // when
            problemCommandRepository.findByIdForUpdate(
                    firstProblemId
            );

            problemCommandRepository.findByIdForUpdate(
                    secondProblemId
            );

            // then
            verify(
                    springDataProblemRepository
            ).findByIdForUpdate(
                    firstProblemId
            );

            verify(
                    springDataProblemRepository
            ).findByIdForUpdate(
                    secondProblemId
            );

            verify(
                    springDataProblemRepository,
                    times(2)
            ).findByIdForUpdate(
                    any(UUID.class)
            );
        }

        @Test
        @DisplayName(
                "연속된 findByIdForUpdate 호출 순서를 유지한다"
        )
        void findByIdForUpdate_multipleCalls_preservesOrder() {

            // given
            UUID firstProblemId =
                    UUID.randomUUID();

            UUID secondProblemId =
                    UUID.randomUUID();

            when(
                    springDataProblemRepository.findByIdForUpdate(
                            firstProblemId
                    )
            ).thenReturn(
                    Optional.empty()
            );

            when(
                    springDataProblemRepository.findByIdForUpdate(
                            secondProblemId
                    )
            ).thenReturn(
                    Optional.empty()
            );

            // when
            problemCommandRepository.findByIdForUpdate(
                    firstProblemId
            );

            problemCommandRepository.findByIdForUpdate(
                    secondProblemId
            );

            // then
            InOrder inOrder =
                    inOrder(
                            springDataProblemRepository
                    );

            inOrder.verify(
                    springDataProblemRepository
            ).findByIdForUpdate(
                    firstProblemId
            );

            inOrder.verify(
                    springDataProblemRepository
            ).findByIdForUpdate(
                    secondProblemId
            );

            inOrder.verifyNoMoreInteractions();
        }
    }

    @Nested
    @DisplayName("flush")
    class Flush {

        @Test
        @DisplayName(
                "flush를 SpringDataProblemRepository에 위임한다"
        )
        void flush_delegatesToSpringDataRepository() {

            // when
            problemCommandRepository.flush();

            // then
            verify(
                    springDataProblemRepository
            ).flush();
        }

        @Test
        @DisplayName(
                "flush는 SpringDataProblemRepository.flush를 정확히 한 번 호출한다"
        )
        void flush_callsSpringDataFlushExactlyOnce() {

            // when
            problemCommandRepository.flush();

            // then
            verify(
                    springDataProblemRepository,
                    times(1)
            ).flush();
        }

        @Test
        @DisplayName(
                "flush 호출 시 save를 호출하지 않는다"
        )
        void flush_doesNotCallSave() {

            // when
            problemCommandRepository.flush();

            // then
            verify(
                    springDataProblemRepository,
                    never()
            ).save(
                    any(Problem.class)
            );
        }

        @Test
        @DisplayName(
                "flush 호출 시 findByIdForUpdate를 호출하지 않는다"
        )
        void flush_doesNotCallFindByIdForUpdate() {

            // when
            problemCommandRepository.flush();

            // then
            verify(
                    springDataProblemRepository,
                    never()
            ).findByIdForUpdate(
                    any(UUID.class)
            );
        }

        @Test
        @DisplayName(
                "flush 수행 시 SpringDataProblemRepository에는 flush 이외의 상호작용이 발생하지 않는다"
        )
        void flush_hasNoUnexpectedInteractions() {

            // when
            problemCommandRepository.flush();

            // then
            verify(
                    springDataProblemRepository
            ).flush();

            verifyNoMoreInteractions(
                    springDataProblemRepository
            );
        }

        @Test
        @DisplayName(
                "SpringDataProblemRepository.flush에서 RuntimeException이 발생하면 그대로 전파한다"
        )
        void flush_whenSpringDataRepositoryThrows_propagatesException() {

            // given
            RuntimeException exception =
                    new RuntimeException(
                            "flush failed"
                    );

            org.mockito.Mockito.doThrow(
                    exception
            ).when(
                    springDataProblemRepository
            ).flush();

            // when & then
            assertThatThrownBy(
                    () ->
                            problemCommandRepository.flush()
            )
                    .isSameAs(exception);
        }

        @Test
        @DisplayName(
                "flush 예외를 RepositoryImpl에서 다른 예외로 변환하지 않는다"
        )
        void flush_doesNotTranslateException() {

            // given
            IllegalStateException exception =
                    new IllegalStateException(
                            "database flush failed"
                    );

            org.mockito.Mockito.doThrow(
                    exception
            ).when(
                    springDataProblemRepository
            ).flush();

            // when & then
            assertThatThrownBy(
                    () ->
                            problemCommandRepository.flush()
            )
                    .isInstanceOf(
                            IllegalStateException.class
                    )
                    .hasMessage(
                            "database flush failed"
                    )
                    .isSameAs(
                            exception
                    );
        }

        @Test
        @DisplayName(
                "flush를 두 번 호출하면 SpringDataProblemRepository.flush도 두 번 호출한다"
        )
        void flush_whenCalledTwice_delegatesTwice() {

            // when
            problemCommandRepository.flush();
            problemCommandRepository.flush();

            // then
            verify(
                    springDataProblemRepository,
                    times(2)
            ).flush();
        }

        @Test
        @DisplayName(
                "flush를 여러 번 호출해도 다른 Repository 메서드를 호출하지 않는다"
        )
        void flush_multipleCalls_doesNotInvokeOtherMethods() {

            // when
            problemCommandRepository.flush();
            problemCommandRepository.flush();
            problemCommandRepository.flush();

            // then
            verify(
                    springDataProblemRepository,
                    times(3)
            ).flush();

            verify(
                    springDataProblemRepository,
                    never()
            ).save(
                    any(Problem.class)
            );

            verify(
                    springDataProblemRepository,
                    never()
            ).findByIdForUpdate(
                    any(UUID.class)
            );

            verifyNoMoreInteractions(
                    springDataProblemRepository
            );
        }
    }

    @Nested
    @DisplayName("Command Repository 메서드 간 상호작용")
    class CommandRepositoryInteraction {

        @Test
        @DisplayName(
                "findByIdForUpdate 후 save를 호출하면 잠금 조회 후 저장 순서로 위임한다"
        )
        void findThenSave_preservesInvocationOrder() {

            // given
            UUID problemId =
                    UUID.randomUUID();

            Problem problem =
                    mockProblem();

            when(
                    springDataProblemRepository.findByIdForUpdate(
                            problemId
                    )
            ).thenReturn(
                    Optional.of(problem)
            );

            when(
                    springDataProblemRepository.save(
                            problem
                    )
            ).thenReturn(
                    problem
            );

            // when
            Optional<Problem> found =
                    problemCommandRepository.findByIdForUpdate(
                            problemId
                    );

            Problem saved =
                    problemCommandRepository.save(
                            found.orElseThrow()
                    );

            // then
            assertThat(saved)
                    .isSameAs(problem);

            InOrder inOrder =
                    inOrder(
                            springDataProblemRepository
                    );

            inOrder.verify(
                    springDataProblemRepository
            ).findByIdForUpdate(
                    problemId
            );

            inOrder.verify(
                    springDataProblemRepository
            ).save(
                    problem
            );

            inOrder.verifyNoMoreInteractions();
        }

        @Test
        @DisplayName(
                "save 후 flush를 호출하면 저장 후 flush 순서로 위임한다"
        )
        void saveThenFlush_preservesInvocationOrder() {

            // given
            Problem problem =
                    mockProblem();

            when(
                    springDataProblemRepository.save(
                            problem
                    )
            ).thenReturn(
                    problem
            );

            // when
            problemCommandRepository.save(
                    problem
            );

            problemCommandRepository.flush();

            // then
            InOrder inOrder =
                    inOrder(
                            springDataProblemRepository
                    );

            inOrder.verify(
                    springDataProblemRepository
            ).save(
                    problem
            );

            inOrder.verify(
                    springDataProblemRepository
            ).flush();

            inOrder.verifyNoMoreInteractions();
        }

        @Test
        @DisplayName(
                "findByIdForUpdate 후 save와 flush를 호출하면 조회, 저장, flush 순서를 유지한다"
        )
        void findSaveFlush_preservesCompleteCommandOrder() {

            // given
            UUID problemId =
                    UUID.randomUUID();

            Problem problem =
                    mockProblem();

            when(
                    springDataProblemRepository.findByIdForUpdate(
                            problemId
                    )
            ).thenReturn(
                    Optional.of(problem)
            );

            when(
                    springDataProblemRepository.save(
                            problem
                    )
            ).thenReturn(
                    problem
            );

            // when
            Optional<Problem> found =
                    problemCommandRepository.findByIdForUpdate(
                            problemId
                    );

            Problem foundProblem =
                    found.orElseThrow();

            problemCommandRepository.save(
                    foundProblem
            );

            problemCommandRepository.flush();

            // then
            InOrder inOrder =
                    inOrder(
                            springDataProblemRepository
                    );

            inOrder.verify(
                    springDataProblemRepository
            ).findByIdForUpdate(
                    problemId
            );

            inOrder.verify(
                    springDataProblemRepository
            ).save(
                    problem
            );

            inOrder.verify(
                    springDataProblemRepository
            ).flush();

            inOrder.verifyNoMoreInteractions();
        }

        @Test
        @DisplayName(
                "조회 결과가 없으면 RepositoryImpl이 자체적으로 save나 flush를 수행하지 않는다"
        )
        void findNotFound_doesNotAutomaticallyPerformCommand() {

            // given
            UUID problemId =
                    UUID.randomUUID();

            when(
                    springDataProblemRepository.findByIdForUpdate(
                            problemId
                    )
            ).thenReturn(
                    Optional.empty()
            );

            // when
            Optional<Problem> result =
                    problemCommandRepository.findByIdForUpdate(
                            problemId
                    );

            // then
            assertThat(result)
                    .isEmpty();

            verify(
                    springDataProblemRepository
            ).findByIdForUpdate(
                    problemId
            );

            verify(
                    springDataProblemRepository,
                    never()
            ).save(
                    any(Problem.class)
            );

            verify(
                    springDataProblemRepository,
                    never()
            ).flush();

            verifyNoMoreInteractions(
                    springDataProblemRepository
            );
        }

        @Test
        @DisplayName(
                "save 호출 자체는 자동 flush를 발생시키지 않는다"
        )
        void save_doesNotImplicitlyFlush() {

            // given
            Problem problem =
                    mockProblem();

            when(
                    springDataProblemRepository.save(
                            problem
                    )
            ).thenReturn(
                    problem
            );

            // when
            problemCommandRepository.save(
                    problem
            );

            // then
            verify(
                    springDataProblemRepository
            ).save(
                    problem
            );

            verify(
                    springDataProblemRepository,
                    never()
            ).flush();

            verifyNoMoreInteractions(
                    springDataProblemRepository
            );
        }

        @Test
        @DisplayName(
                "findByIdForUpdate 호출 자체는 자동 저장을 발생시키지 않는다"
        )
        void findByIdForUpdate_doesNotImplicitlySave() {

            // given
            UUID problemId =
                    UUID.randomUUID();

            Problem problem =
                    mockProblem();

            when(
                    springDataProblemRepository.findByIdForUpdate(
                            problemId
                    )
            ).thenReturn(
                    Optional.of(problem)
            );

            // when
            Optional<Problem> result =
                    problemCommandRepository.findByIdForUpdate(
                            problemId
                    );

            // then
            assertThat(result)
                    .containsSame(problem);

            verify(
                    springDataProblemRepository
            ).findByIdForUpdate(
                    problemId
            );

            verify(
                    springDataProblemRepository,
                    never()
            ).save(
                    any(Problem.class)
            );

            verifyNoMoreInteractions(
                    springDataProblemRepository
            );
        }
    }

    private Problem mockProblem() {
        return org.mockito.Mockito.mock(
                Problem.class
        );
    }
}