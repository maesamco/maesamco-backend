package com.maesamco.content.infrastructure.persistence;

import com.maesamco.content.domain.entity.problem.ProblemProgress;
import com.maesamco.content.domain.entity.problem.ProblemProgressStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProblemProgressRepositoryImpl 테스트")
class ProblemProgressRepositoryImplTest {

    @Mock
    private SpringDataProblemProgressRepository springDataProblemProgressRepository;

    @InjectMocks
    private ProblemProgressRepositoryImpl problemProgressRepository;

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("ProblemProgress를 저장하고 Spring Data Repository가 반환한 엔티티를 반환한다")
        void save_success() {
            // given
            ProblemProgress problemProgress = mock(ProblemProgress.class);
            ProblemProgress savedProblemProgress = mock(ProblemProgress.class);

            when(springDataProblemProgressRepository.save(problemProgress))
                    .thenReturn(savedProblemProgress);

            // when
            ProblemProgress result = problemProgressRepository.save(problemProgress);

            // then
            assertThat(result).isSameAs(savedProblemProgress);

            verify(springDataProblemProgressRepository, times(1))
                    .save(problemProgress);

            verifyNoMoreInteractions(springDataProblemProgressRepository);
        }

        @Test
        @DisplayName("save 호출 시 전달받은 ProblemProgress 인스턴스를 그대로 전달한다")
        void save_passesExactProblemProgress() {
            // given
            ProblemProgress problemProgress = mock(ProblemProgress.class);

            when(springDataProblemProgressRepository.save(problemProgress))
                    .thenReturn(problemProgress);

            // when
            ProblemProgress result = problemProgressRepository.save(problemProgress);

            // then
            assertThat(result).isSameAs(problemProgress);

            verify(springDataProblemProgressRepository)
                    .save(problemProgress);

            verifyNoMoreInteractions(springDataProblemProgressRepository);
        }
    }

    @Nested
    @DisplayName("findByUserIdAndProblemId")
    class FindByUserIdAndProblemId {

        @Test
        @DisplayName("사용자와 문제에 해당하는 ProblemProgress가 존재하면 반환한다")
        void findByUserIdAndProblemId_exists_returnsProblemProgress() {
            // given
            UUID userId = UUID.randomUUID();
            UUID problemId = UUID.randomUUID();
            ProblemProgress problemProgress = mock(ProblemProgress.class);

            when(springDataProblemProgressRepository.findByUserIdAndProblemId(userId, problemId))
                    .thenReturn(Optional.of(problemProgress));

            // when
            Optional<ProblemProgress> result =
                    problemProgressRepository.findByUserIdAndProblemId(userId, problemId);

            // then
            assertThat(result)
                    .isPresent()
                    .containsSame(problemProgress);

            verify(springDataProblemProgressRepository, times(1))
                    .findByUserIdAndProblemId(userId, problemId);

            verifyNoMoreInteractions(springDataProblemProgressRepository);
        }

        @Test
        @DisplayName("사용자와 문제에 해당하는 ProblemProgress가 없으면 빈 Optional을 반환한다")
        void findByUserIdAndProblemId_notExists_returnsEmpty() {
            // given
            UUID userId = UUID.randomUUID();
            UUID problemId = UUID.randomUUID();

            when(springDataProblemProgressRepository.findByUserIdAndProblemId(userId, problemId))
                    .thenReturn(Optional.empty());

            // when
            Optional<ProblemProgress> result =
                    problemProgressRepository.findByUserIdAndProblemId(userId, problemId);

            // then
            assertThat(result).isEmpty();

            verify(springDataProblemProgressRepository, times(1))
                    .findByUserIdAndProblemId(userId, problemId);

            verifyNoMoreInteractions(springDataProblemProgressRepository);
        }

        @Test
        @DisplayName("userId와 problemId를 변경하지 않고 Spring Data Repository에 전달한다")
        void findByUserIdAndProblemId_passesArgumentsCorrectly() {
            // given
            UUID userId = UUID.randomUUID();
            UUID problemId = UUID.randomUUID();

            when(springDataProblemProgressRepository.findByUserIdAndProblemId(userId, problemId))
                    .thenReturn(Optional.empty());

            // when
            problemProgressRepository.findByUserIdAndProblemId(userId, problemId);

            // then
            verify(springDataProblemProgressRepository)
                    .findByUserIdAndProblemId(userId, problemId);

            verifyNoMoreInteractions(springDataProblemProgressRepository);
        }
    }

    @Nested
    @DisplayName("findByUserIdOrderByCreatedAtDescIdDesc")
    class FindByUserIdOrderByCreatedAtDescIdDesc {

        @Test
        @DisplayName("사용자의 ProblemProgress 목록을 Spring Data Repository의 조회 결과 순서 그대로 반환한다")
        void findByUserIdOrderByCreatedAtDescIdDesc_success() {
            // given
            UUID userId = UUID.randomUUID();

            ProblemProgress first = mock(ProblemProgress.class);
            ProblemProgress second = mock(ProblemProgress.class);
            ProblemProgress third = mock(ProblemProgress.class);

            List<ProblemProgress> expected =
                    List.of(first, second, third);

            when(springDataProblemProgressRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId))
                    .thenReturn(expected);

            // when
            List<ProblemProgress> result =
                    problemProgressRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId);

            // then
            assertThat(result)
                    .isSameAs(expected)
                    .containsExactly(first, second, third);

            verify(springDataProblemProgressRepository, times(1))
                    .findByUserIdOrderByCreatedAtDescIdDesc(userId);

            verifyNoMoreInteractions(springDataProblemProgressRepository);
        }

        @Test
        @DisplayName("사용자의 ProblemProgress가 없으면 빈 목록을 반환한다")
        void findByUserIdOrderByCreatedAtDescIdDesc_empty_returnsEmptyList() {
            // given
            UUID userId = UUID.randomUUID();

            when(springDataProblemProgressRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId))
                    .thenReturn(List.of());

            // when
            List<ProblemProgress> result =
                    problemProgressRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId);

            // then
            assertThat(result).isEmpty();

            verify(springDataProblemProgressRepository, times(1))
                    .findByUserIdOrderByCreatedAtDescIdDesc(userId);

            verifyNoMoreInteractions(springDataProblemProgressRepository);
        }

        @Test
        @DisplayName("userId를 변경하지 않고 Spring Data Repository에 전달한다")
        void findByUserIdOrderByCreatedAtDescIdDesc_passesUserId() {
            // given
            UUID userId = UUID.randomUUID();

            when(springDataProblemProgressRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId))
                    .thenReturn(List.of());

            // when
            problemProgressRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId);

            // then
            verify(springDataProblemProgressRepository)
                    .findByUserIdOrderByCreatedAtDescIdDesc(userId);

            verifyNoMoreInteractions(springDataProblemProgressRepository);
        }
    }

    @Nested
    @DisplayName("findByUserIdAndProgressStatusOrderByCreatedAtDescIdDesc")
    class FindByUserIdAndProgressStatusOrderByCreatedAtDescIdDesc {

        @Test
        @DisplayName("사용자와 풀이 상태에 해당하는 ProblemProgress 목록을 조회 결과 순서 그대로 반환한다")
        void findByUserIdAndProgressStatusOrderByCreatedAtDescIdDesc_success() {
            // given
            UUID userId = UUID.randomUUID();
            ProblemProgressStatus progressStatus = ProblemProgressStatus.WRONG;

            ProblemProgress first = mock(ProblemProgress.class);
            ProblemProgress second = mock(ProblemProgress.class);

            List<ProblemProgress> expected =
                    List.of(first, second);

            when(springDataProblemProgressRepository
                    .findByUserIdAndProgressStatusOrderByCreatedAtDescIdDesc(userId, progressStatus))
                    .thenReturn(expected);

            // when
            List<ProblemProgress> result =
                    problemProgressRepository
                            .findByUserIdAndProgressStatusOrderByCreatedAtDescIdDesc(
                                    userId,
                                    progressStatus
                            );

            // then
            assertThat(result)
                    .isSameAs(expected)
                    .containsExactly(first, second);

            verify(springDataProblemProgressRepository, times(1))
                    .findByUserIdAndProgressStatusOrderByCreatedAtDescIdDesc(
                            userId,
                            progressStatus
                    );

            verifyNoMoreInteractions(springDataProblemProgressRepository);
        }

        @Test
        @DisplayName("사용자와 풀이 상태에 해당하는 ProblemProgress가 없으면 빈 목록을 반환한다")
        void findByUserIdAndProgressStatusOrderByCreatedAtDescIdDesc_empty_returnsEmptyList() {
            // given
            UUID userId = UUID.randomUUID();
            ProblemProgressStatus progressStatus = ProblemProgressStatus.CORRECT;

            when(springDataProblemProgressRepository
                    .findByUserIdAndProgressStatusOrderByCreatedAtDescIdDesc(userId, progressStatus))
                    .thenReturn(List.of());

            // when
            List<ProblemProgress> result =
                    problemProgressRepository
                            .findByUserIdAndProgressStatusOrderByCreatedAtDescIdDesc(
                                    userId,
                                    progressStatus
                            );

            // then
            assertThat(result).isEmpty();

            verify(springDataProblemProgressRepository, times(1))
                    .findByUserIdAndProgressStatusOrderByCreatedAtDescIdDesc(
                            userId,
                            progressStatus
                    );

            verifyNoMoreInteractions(springDataProblemProgressRepository);
        }

        @Test
        @DisplayName("userId와 progressStatus를 변경하지 않고 Spring Data Repository에 전달한다")
        void findByUserIdAndProgressStatusOrderByCreatedAtDescIdDesc_passesArgumentsCorrectly() {
            // given
            UUID userId = UUID.randomUUID();
            ProblemProgressStatus progressStatus = ProblemProgressStatus.WRONG;

            when(springDataProblemProgressRepository
                    .findByUserIdAndProgressStatusOrderByCreatedAtDescIdDesc(userId, progressStatus))
                    .thenReturn(List.of());

            // when
            problemProgressRepository
                    .findByUserIdAndProgressStatusOrderByCreatedAtDescIdDesc(
                            userId,
                            progressStatus
                    );

            // then
            verify(springDataProblemProgressRepository)
                    .findByUserIdAndProgressStatusOrderByCreatedAtDescIdDesc(
                            userId,
                            progressStatus
                    );

            verifyNoMoreInteractions(springDataProblemProgressRepository);
        }
    }
}