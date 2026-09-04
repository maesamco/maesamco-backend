package com.maesamco.judge.application.service;

import com.maesamco.judge.domain.entity.ProblemExecutionSpec;
import com.maesamco.judge.domain.repository.ProblemExecutionSpecRepository;
import com.maesamco.judge.infrastructure.messaging.event.InvalidProblemPublishedEventException;
import com.maesamco.judge.infrastructure.messaging.event.ProblemPublishedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProblemExecutionSpecServiceTest {

    @Mock
    private ProblemExecutionSpecRepository problemExecutionSpecRepository;

    @Spy
    private JsonMapper jsonMapper = JsonMapper.builder().build();

    @InjectMocks
    private ProblemExecutionSpecService problemExecutionSpecService;

    private ProblemPublishedEvent validEvent() {
        return new ProblemPublishedEvent(
                UUID.randomUUID(),
                "ProblemPublished",
                1,
                Instant.parse("2026-09-02T03:00:00Z"),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "JAVA",
                "public class Main {}",
                List.of(new ProblemPublishedEvent.TestCaseItem(UUID.randomUUID(), true, "3\n1 2 3", "6", 1)),
                1000,
                128,
                Instant.parse("2026-09-02T03:00:00Z")
        );
    }

    @Nested
    @DisplayName("saveIfAbsent")
    class SaveIfAbsent {

        @Test
        @DisplayName("동일 (problemId, problemVersionId)가 이미 있으면 저장하지 않고 스킵한다")
        void skipsWhenAlreadyExists() {
            // given
            ProblemPublishedEvent event = validEvent();
            given(problemExecutionSpecRepository.existsByProblemIdAndProblemVersionId(
                    event.problemId(), event.problemVersionId())).willReturn(true);

            // when
            problemExecutionSpecService.saveIfAbsent(event);

            // then
            verify(problemExecutionSpecRepository, never()).save(any());
        }

        @Test
        @DisplayName("처음 보는 이벤트면 이벤트 필드를 그대로 옮겨 저장한다")
        void savesWhenAbsent() {
            // given
            ProblemPublishedEvent event = validEvent();
            given(problemExecutionSpecRepository.existsByProblemIdAndProblemVersionId(
                    event.problemId(), event.problemVersionId())).willReturn(false);

            // when
            problemExecutionSpecService.saveIfAbsent(event);

            // then
            ArgumentCaptor<ProblemExecutionSpec> captor = ArgumentCaptor.forClass(ProblemExecutionSpec.class);
            verify(problemExecutionSpecRepository, times(1)).saveAndFlush(captor.capture());

            ProblemExecutionSpec saved = captor.getValue();
            assertThat(saved.getProblemId()).isEqualTo(event.problemId());
            assertThat(saved.getProblemVersionId()).isEqualTo(event.problemVersionId());
            assertThat(saved.getTimeLimitMs()).isEqualTo(event.timeLimit());
            assertThat(saved.getMemoryLimitMb()).isEqualTo(event.memoryLimit());
            assertThat(saved.getPublishedAt()).isEqualTo(event.publishedAt());
            // testCases는 JSONB 컬럼에 문자열로 들어가므로, 원본 배열 원소 수가 그대로
            // 직렬화됐는지만 확인한다 (구조 자체는 TODO — Content 확인 후 필드 매칭 테스트 보강).
            assertThat(saved.getTestCases()).contains(event.testCases().get(0).testCaseId().toString());
        }

        @Test
        @DisplayName("publishedAt이 없는 이벤트는 잘못된 이벤트로 간주해 예외를 던진다")
        void throwsWhenPublishedAtMissing() {
            // given
            ProblemPublishedEvent event = new ProblemPublishedEvent(
                    UUID.randomUUID(), "ProblemPublished", 1, Instant.now(),
                    UUID.randomUUID(), UUID.randomUUID(), "JAVA", null,
                    List.of(), 1000, 128, null
            );
            given(problemExecutionSpecRepository.existsByProblemIdAndProblemVersionId(
                    event.problemId(), event.problemVersionId())).willReturn(false);

            // when / then
            assertThatThrownBy(() -> problemExecutionSpecService.saveIfAbsent(event))
                    .isInstanceOf(InvalidProblemPublishedEventException.class);
            verify(problemExecutionSpecRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("존재 확인 이후 저장 사이에 다른 스레드가 먼저 저장해 UNIQUE 충돌이 나도 예외를 전파하지 않는다")
        void swallowsRaceConditionUniqueViolation() {
            // given
            ProblemPublishedEvent event = validEvent();
            given(problemExecutionSpecRepository.existsByProblemIdAndProblemVersionId(
                    event.problemId(), event.problemVersionId())).willReturn(false);
            given(problemExecutionSpecRepository.saveAndFlush(any()))
                    .willThrow(new DataIntegrityViolationException("duplicate key"));

            // when / then — 예외가 밖으로 새면 안 됨(멱등 처리 대상이지 오류가 아님)
            assertThatCode(() -> problemExecutionSpecService.saveIfAbsent(event))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("language가 SubmissionLanguage에 없는 값이면 예외를 던져 Kafka 에러 핸들러(재시도/DLT)로 넘긴다")
        void throwsWhenLanguageIsUnknown() {
            // given
            ProblemPublishedEvent event = new ProblemPublishedEvent(
                    UUID.randomUUID(), "ProblemPublished", 1, Instant.now(),
                    UUID.randomUUID(), UUID.randomUUID(), "PYTHON", null,
                    List.of(), 1000, 128, Instant.now()
            );
            given(problemExecutionSpecRepository.existsByProblemIdAndProblemVersionId(
                    event.problemId(), event.problemVersionId())).willReturn(false);

            // when / then
            assertThatThrownBy(() -> problemExecutionSpecService.saveIfAbsent(event))
                    .isInstanceOf(IllegalArgumentException.class);
            verify(problemExecutionSpecRepository, never()).save(any());
        }
    }

    @Test
    @DisplayName("timeLimit이 0 이하인 이벤트는 잘못된 이벤트로 간주해 예외를 던진다")
    void throwsWhenTimeLimitNotPositive() {
        // given
        ProblemPublishedEvent event = new ProblemPublishedEvent(
                UUID.randomUUID(), "ProblemPublished", 1, Instant.now(),
                UUID.randomUUID(), UUID.randomUUID(), "JAVA", null,
                List.of(), 0, 128, Instant.now()   // timeLimit = 0
        );
        given(problemExecutionSpecRepository.existsByProblemIdAndProblemVersionId(
                event.problemId(), event.problemVersionId())).willReturn(false);

        // when / then
        assertThatThrownBy(() -> problemExecutionSpecService.saveIfAbsent(event))
                .isInstanceOf(InvalidProblemPublishedEventException.class);
        verify(problemExecutionSpecRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("memoryLimit이 0 이하인 이벤트는 잘못된 이벤트로 간주해 예외를 던진다")
    void throwsWhenMemoryLimitNotPositive() {
        // given
        ProblemPublishedEvent event = new ProblemPublishedEvent(
                UUID.randomUUID(), "ProblemPublished", 1, Instant.now(),
                UUID.randomUUID(), UUID.randomUUID(), "JAVA", null,
                List.of(), 1000, -1, Instant.now()   // memoryLimit = -1
        );
        given(problemExecutionSpecRepository.existsByProblemIdAndProblemVersionId(
                event.problemId(), event.problemVersionId())).willReturn(false);

        // when / then
        assertThatThrownBy(() -> problemExecutionSpecService.saveIfAbsent(event))
                .isInstanceOf(InvalidProblemPublishedEventException.class);
        verify(problemExecutionSpecRepository, never()).saveAndFlush(any());
    }
}
