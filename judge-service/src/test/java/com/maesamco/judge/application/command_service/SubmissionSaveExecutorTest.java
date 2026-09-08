package com.maesamco.judge.application.command_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import com.maesamco.judge.domain.entity.Submission;
import com.maesamco.judge.domain.entity.SubmissionEventOutbox;
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

    @Test
    @DisplayName("저장에 성공하면 Outbox도 함께 기록하고, 저장이 Outbox 기록보다 먼저 일어난다")
    void savesSubmissionAndOutboxInOrder() {
        // given
        UUID submissionId = UUID.randomUUID();
        Submission submission = mock(Submission.class);
        given(submission.getId()).willReturn(submissionId);

        // when
        submissionSaveExecutor.saveWithOutbox(submission);

        // then
        InOrder inOrder = inOrder(submissionRepository, submissionEventOutboxRepository);
        inOrder.verify(submissionRepository).saveAndFlush(submission);
        inOrder.verify(submissionEventOutboxRepository).save(any(SubmissionEventOutbox.class));

        ArgumentCaptor<SubmissionEventOutbox> captor = ArgumentCaptor.forClass(SubmissionEventOutbox.class);
        verify(submissionEventOutboxRepository).save(captor.capture());
        // payload(JSONB 문자열)에 submissionId가 실제로 직렬화됐는지 확인
        assertThat(captor.getValue().getPayload()).contains(submissionId.toString());
    }

    @Test
    @DisplayName("저장이 실패하면 Outbox는 기록되지 않고 예외가 그대로 전파된다")
    void doesNotWriteOutboxWhenSaveFails() {
        // given
        Submission submission = mock(Submission.class);
        doThrow(new DataIntegrityViolationException("unique violation"))
                .when(submissionRepository).saveAndFlush(submission);

        // when / then
        assertThatThrownBy(() -> submissionSaveExecutor.saveWithOutbox(submission))
                .isInstanceOf(DataIntegrityViolationException.class);
        verify(submissionEventOutboxRepository, never()).save(any());
    }
}