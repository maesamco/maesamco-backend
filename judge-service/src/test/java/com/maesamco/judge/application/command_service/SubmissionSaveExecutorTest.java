package com.maesamco.judge.application.command_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import com.maesamco.judge.domain.entity.Submission;
import com.maesamco.judge.domain.entity.SubmissionEventOutbox;
import com.maesamco.judge.domain.entity.SubmissionLanguage;
import com.maesamco.judge.domain.repository.SubmissionEventOutboxRepository;
import com.maesamco.judge.domain.repository.SubmissionRepository;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class SubmissionSaveExecutorTest {

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private SubmissionEventOutboxRepository submissionEventOutboxRepository;

    @Spy
    private JsonMapper jsonMapper = JsonMapper.builder().build();

    @org.mockito.InjectMocks
    private SubmissionSaveExecutor submissionSaveExecutor;

    private final UUID userId = UUID.randomUUID();
    private final UUID problemId = UUID.randomUUID();
    private final UUID problemVersionId = UUID.randomUUID();

    @Test
    @DisplayName("advisory lock을 먼저 획득한 뒤 attemptNo를 산정해 저장하고, Outbox도 함께 기록한다")
    void acquiresLockThenComputesAttemptNoAndSavesWithOutbox() {
        // given
        given(submissionRepository.findMaxAttemptNoByUserIdAndProblemId(userId, problemId)).willReturn(1);

        // when
        Submission result = submissionSaveExecutor.createAndSave(
                userId, problemId, problemVersionId, "public class Main {}", SubmissionLanguage.JAVA17, "idem-key");

        // then — 이전 max(1) 기준으로 다음 attemptNo(2)가 산정됐는지
        assertThat(result.getAttemptNo()).isEqualTo(2);

        // then — lock 획득 → attemptNo 조회 → 저장 → Outbox 기록 순서로 일어나는지
        InOrder inOrder = inOrder(submissionRepository, submissionEventOutboxRepository);
        inOrder.verify(submissionRepository).acquireAttemptNoLock(userId.toString(), problemId.toString());
        inOrder.verify(submissionRepository).findMaxAttemptNoByUserIdAndProblemId(userId, problemId);
        inOrder.verify(submissionRepository).saveAndFlush(result);
        inOrder.verify(submissionEventOutboxRepository).save(any(SubmissionEventOutbox.class));

        ArgumentCaptor<SubmissionEventOutbox> captor = ArgumentCaptor.forClass(SubmissionEventOutbox.class);
        verify(submissionEventOutboxRepository).save(captor.capture());
        // payload(JSONB 문자열)에 이벤트 타입이 실제로 직렬화됐는지 확인.
        // (Submission.id는 Hibernate @UuidGenerator가 실제 persist 시점에 채우는 값이라,
        // saveAndFlush가 mock인 이 단위테스트에서는 id 자체를 신뢰성 있게 검증할 수 없다.
        // id가 payload에 실려 나가는지는 Testcontainers 기반 동시성 테스트에서 검증된다.)
        assertThat(captor.getValue().getPayload()).contains("JudgeRequested");
    }

    @Test
    @DisplayName("저장이 실패하면 Outbox는 기록되지 않고 예외가 그대로 전파된다")
    void doesNotWriteOutboxWhenSaveFails() {
        // given
        given(submissionRepository.findMaxAttemptNoByUserIdAndProblemId(userId, problemId)).willReturn(0);
        doThrow(new DataIntegrityViolationException("unique violation"))
                .when(submissionRepository).saveAndFlush(any(Submission.class));

        // when / then
        assertThatThrownBy(() -> submissionSaveExecutor.createAndSave(
                userId, problemId, problemVersionId, "code", SubmissionLanguage.JAVA17, "idem-key"))
                .isInstanceOf(DataIntegrityViolationException.class);
        verify(submissionEventOutboxRepository, never()).save(any());
    }
}