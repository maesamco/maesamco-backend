package com.maesamco.judge.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import com.maesamco.judge.application.ProblemExecutionSpecService;
import com.maesamco.judge.application.command.ProblemExecutionSpecSaveCommand;
import com.maesamco.judge.application.exception.InvalidProblemPublishedEventException;
import com.maesamco.judge.domain.entity.ProblemExecutionSpec;
import com.maesamco.judge.domain.repository.ProblemExecutionSpecRepository;
import com.maesamco.judge.infrastructure.messaging.event.ProblemPublishedEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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
                UUID.randomUUID(), "ProblemPublished", 1,
                Instant.parse("2026-09-02T03:00:00Z"),
                UUID.randomUUID(), UUID.randomUUID(), "JAVA17",
                "public class Main {}",
                List.of(new ProblemPublishedEvent.TestCaseItem(UUID.randomUUID(), true, "3\n1 2 3", "6", 1)),
                1000, 128,
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
            ProblemExecutionSpecSaveCommand command = ProblemExecutionSpecSaveCommand.from(validEvent());
            given(problemExecutionSpecRepository.existsByProblemIdAndProblemVersionId(
                    command.problemId(), command.problemVersionId())).willReturn(true);

            // when
            problemExecutionSpecService.saveIfAbsent(command);

            // then
            verify(problemExecutionSpecRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("처음 보는 이벤트면 이벤트 필드를 그대로 옮겨 저장한다")
        void savesWhenAbsent() {
            // given
            ProblemExecutionSpecSaveCommand command = ProblemExecutionSpecSaveCommand.from(validEvent());
            given(problemExecutionSpecRepository.existsByProblemIdAndProblemVersionId(
                    command.problemId(), command.problemVersionId())).willReturn(false);

            // when
            problemExecutionSpecService.saveIfAbsent(command);

            // then
            ArgumentCaptor<ProblemExecutionSpec> captor = ArgumentCaptor.forClass(ProblemExecutionSpec.class);
            verify(problemExecutionSpecRepository, times(1)).saveAndFlush(captor.capture());

            ProblemExecutionSpec saved = captor.getValue();
            assertThat(saved.getProblemId()).isEqualTo(command.problemId());
            assertThat(saved.getProblemVersionId()).isEqualTo(command.problemVersionId());
            assertThat(saved.getTimeLimitMs()).isEqualTo(command.timeLimitMs());
            assertThat(saved.getMemoryLimitMb()).isEqualTo(command.memoryLimitMb());
            assertThat(saved.getPublishedAt()).isEqualTo(command.publishedAt());
            assertThat(saved.getTestCases()).contains(command.testCases().getFirst().testCaseId().toString());
        }

        @Test
        @DisplayName("존재 확인 이후 저장 사이에 다른 스레드가 먼저 저장해 UNIQUE 충돌이 나도 예외를 전파하지 않는다")
        void swallowsRaceConditionUniqueViolation() {
            // given
            ProblemExecutionSpecSaveCommand command = ProblemExecutionSpecSaveCommand.from(validEvent());
            given(problemExecutionSpecRepository.existsByProblemIdAndProblemVersionId(
                    command.problemId(), command.problemVersionId())).willReturn(false);
            given(problemExecutionSpecRepository.saveAndFlush(any()))
                    .willThrow(new DataIntegrityViolationException("duplicate key"));

            // when / then
            assertThatCode(() -> problemExecutionSpecService.saveIfAbsent(command))
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
            ProblemExecutionSpecSaveCommand command = ProblemExecutionSpecSaveCommand.from(event);
            given(problemExecutionSpecRepository.existsByProblemIdAndProblemVersionId(
                    command.problemId(), command.problemVersionId())).willReturn(false);

            // when / then
            assertThatThrownBy(() -> problemExecutionSpecService.saveIfAbsent(command))
                    .isInstanceOf(IllegalArgumentException.class);
            verify(problemExecutionSpecRepository, never()).saveAndFlush(any());
        }
    }

    /**
     * publishedAt/timeLimit/memoryLimit 검증은 이제 Service가 아니라
     * ProblemExecutionSpecSaveCommand.from()에서 일어난다 — 그래서 Service/Repository는
     * 전혀 필요 없고, Command 팩토리만 단독으로 테스트한다.
     * (Repository mock을 여기서 쓰면 아무 데도 안 쓰이는 stub 때문에
     * Mockito strict-stub 모드에서 UnnecessaryStubbingException이 난다.)
     */
    @Nested
    @DisplayName("ProblemExecutionSpecSaveCommand.from 검증")
    class FromValidation {

        @Test
        @DisplayName("publishedAt이 없으면 잘못된 이벤트로 간주해 예외를 던진다")
        void throwsWhenPublishedAtMissing() {
            ProblemPublishedEvent event = new ProblemPublishedEvent(
                    UUID.randomUUID(), "ProblemPublished", 1, Instant.now(),
                    UUID.randomUUID(), UUID.randomUUID(), "JAVA17", null,
                    List.of(), 1000, 128, null
            );

            assertThatThrownBy(() -> ProblemExecutionSpecSaveCommand.from(event))
                    .isInstanceOf(InvalidProblemPublishedEventException.class);
        }

        @Test
        @DisplayName("timeLimit이 0 이하면 잘못된 이벤트로 간주해 예외를 던진다")
        void throwsWhenTimeLimitNotPositive() {
            ProblemPublishedEvent event = new ProblemPublishedEvent(
                    UUID.randomUUID(), "ProblemPublished", 1, Instant.now(),
                    UUID.randomUUID(), UUID.randomUUID(), "JAVA17", null,
                    List.of(), 0, 128, Instant.now()
            );

            assertThatThrownBy(() -> ProblemExecutionSpecSaveCommand.from(event))
                    .isInstanceOf(InvalidProblemPublishedEventException.class);
        }

        @Test
        @DisplayName("memoryLimit이 0 이하면 잘못된 이벤트로 간주해 예외를 던진다")
        void throwsWhenMemoryLimitNotPositive() {
            ProblemPublishedEvent event = new ProblemPublishedEvent(
                    UUID.randomUUID(), "ProblemPublished", 1, Instant.now(),
                    UUID.randomUUID(), UUID.randomUUID(), "JAVA17", null,
                    List.of(), 1000, -1, Instant.now()
            );

            assertThatThrownBy(() -> ProblemExecutionSpecSaveCommand.from(event))
                    .isInstanceOf(InvalidProblemPublishedEventException.class);
        }
    }
}