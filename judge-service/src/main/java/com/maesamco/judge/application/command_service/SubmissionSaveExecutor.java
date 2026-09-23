package com.maesamco.judge.application.command_service;

import com.maesamco.judge.domain.entity.Submission;
import com.maesamco.judge.domain.entity.SubmissionEventOutbox;
import com.maesamco.judge.domain.entity.SubmissionLanguage;
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
     * attemptNo 산정 + 제출 저장 + Outbox 기록을 하나의 독립된 트랜잭션으로 묶습니다.
     *
     * "조회 후 +1" 패턴은 동시 요청 사이에서 경합할 수 있어서(#287), attemptNo를 계산하기
     * 전에 pg_advisory_xact_lock으로 (userId, problemId) 조합을 직렬화합니다. 같은 조합에
     * 대한 다른 트랜잭션은 이 락이 풀릴 때까지 대기하므로, 두 스레드가 같은 max값을 읽어
     * 같은 attemptNo로 INSERT를 시도하는 상황 자체가 사라집니다. 락은 세션이 아니라
     * 트랜잭션에 묶여 있어서(xact_lock) 이 메서드의 트랜잭션이 커밋되거나 롤백되는 순간
     * PostgreSQL이 자동으로 해제합니다 — 별도 해제 호출이 필요 없습니다.
     *
     * REQUIRES_NEW로 별도 트랜잭션을 여는 이유: submit()의 재시도 루프에서 한 시도가
     * DataIntegrityViolationException으로 실패해도, PostgreSQL이 그 트랜잭션을 abort
     * 상태로 만들 뿐 다음 시도의 새 트랜잭션(그리고 새 advisory lock 획득)까지 오염시키지
     * 않게 하기 위함입니다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Submission createAndSave(
            UUID userId, UUID problemId, UUID problemVersionId, String code,
            SubmissionLanguage language, String idempotencyKey) {
        submissionRepository.acquireAttemptNoLock(userId.toString(), problemId.toString());

        int attemptNo = submissionRepository.findMaxAttemptNoByUserIdAndProblemId(userId, problemId) + 1;
        Submission submission = Submission.create(
                userId, problemId, problemVersionId, attemptNo, code, language, idempotencyKey);

        submissionRepository.saveAndFlush(submission);
        submissionEventOutboxRepository.save(SubmissionEventOutbox.create(
                submission.getId(), JUDGE_REQUESTED_EVENT_TYPE, writeJudgeRequestedPayload(submission.getId())
        ));
        return submission;
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