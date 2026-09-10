package com.maesamco.judge.application.command_service;

import com.maesamco.judge.domain.entity.Submission;
import com.maesamco.judge.domain.entity.SubmissionEventOutbox;
import com.maesamco.judge.domain.repository.SubmissionEventOutboxRepository;
import com.maesamco.judge.domain.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SubmissionSaveExecutor {

    private static final String JUDGE_REQUESTED_EVENT_TYPE = "JudgeRequested";
    private static final int JUDGE_REQUESTED_EVENT_VERSION = 1;

    private final SubmissionRepository submissionRepository;
    private final SubmissionEventOutboxRepository submissionEventOutboxRepository;
    private final JsonMapper jsonMapper;

    /**
     * 제출 저장 + Outbox 기록을 하나의 독립된 트랜잭션으로 묶습니다.
     * REQUIRES_NEW로 별도 트랜잭션을 여는 이유: submit()의 재시도 루프에서 한 시도가
     * DataIntegrityViolationException으로 실패해도, PostgreSQL이 그 트랜잭션을 abort
     * 상태로 만들 뿐 다음 시도의 새 트랜잭션까지 오염시키지 않게 하기 위함입니다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveWithOutbox(Submission submission) {
        submissionRepository.saveAndFlush(submission);
        submissionEventOutboxRepository.save(SubmissionEventOutbox.create(
                submission.getId(), JUDGE_REQUESTED_EVENT_TYPE, writeJudgeRequestedPayload(submission.getId())
        ));
    }
    private String writeJudgeRequestedPayload(UUID submissionId) {
        try {
            return jsonMapper.writeValueAsString(new JudgeRequestedPayload(UUID.randomUUID(), JUDGE_REQUESTED_EVENT_TYPE, JUDGE_REQUESTED_EVENT_VERSION,
                    Instant.now(), submissionId
            ));
        } catch (JacksonException ex) {
            throw new IllegalArgumentException("JudgeRequested payload 직렬화 실패. submissionId=" + submissionId, ex);
        }
    }

    private record JudgeRequestedPayload(
            UUID eventId, String eventType, int eventVersion, Instant occurredAt, UUID submissionId
    ) {}
}
