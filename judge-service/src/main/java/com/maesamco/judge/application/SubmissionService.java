package com.maesamco.judge.application;

import com.maesamco.judge.application.command.SubmissionCreateCommand;
import com.maesamco.judge.application.result.SubmissionCreateResult;
import com.maesamco.judge.domain.entity.ProblemExecutionSpec;
import com.maesamco.judge.domain.entity.Submission;
import com.maesamco.judge.domain.entity.SubmissionEventOutbox;
import com.maesamco.judge.domain.entity.SubmissionLanguage;
import com.maesamco.judge.domain.repository.ProblemExecutionSpecRepository;
import com.maesamco.judge.domain.repository.SubmissionEventOutboxRepository;
import com.maesamco.judge.domain.repository.SubmissionRepository;
import com.maesamco.judge.global.exception.BusinessException;
import com.maesamco.judge.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class SubmissionService {

    private static final String JUDGE_REQUESTED_EVENT_TYPE = "JudgeRequestedEvent";
    private static final int MAX_SAVE_RETRY = 3;

    private final SubmissionRepository submissionRepository;
    private final SubmissionEventOutboxRepository submissionEventOutboxRepository;
    private final ProblemExecutionSpecRepository  problemExecutionSpecRepository;
    private final JsonMapper jsonMapper;

    @Transactional
    public SubmissionCreateResult submit(SubmissionCreateCommand command) {
        //멱등성 체크
        Optional<Submission> existing = submissionRepository.findByIdempotencyKey(command.idempotencyKey());
        if (existing.isPresent()) {
            return toIdempotentResult(existing.get(), command);
        }
        //문제 스펙 조회
        ProblemExecutionSpec spec = problemExecutionSpecRepository
                .findFirstByProblemIdOrderByPublishedAtDesc(command.problemId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_NOT_FOUND));

        //저장+경합 재시도 루프
        for (int attempt = 1; attempt <= MAX_SAVE_RETRY; attempt++) {
            int attemptNo = submissionRepository.findMaxAttemptNoByUserIdAndProblemId(
                    command.userId(), command.problemId()) +1;
            Submission submission = Submission.create(
                    command.userId(), command.problemId(), spec.getProblemVersionId(), attemptNo,
                    command.code(), toSubmissionLanguage(command.language()), command.idempotencyKey()
            );

            try {
                submissionRepository.saveAndFlush(submission);
            } catch (DataIntegrityViolationException ex) {
                Optional<Submission> racedByKey = submissionRepository.findByIdempotencyKey(command.idempotencyKey());
                if (racedByKey.isPresent()) {
                    return toIdempotentResult(racedByKey.get(), command);
                }
                log.info("[Judge] 제출 저장 경합 상태(attemptNo 추정) — 재시도 {}/{}. userId={}, problemId={}",
                        attempt, MAX_SAVE_RETRY, command.userId(), command.problemId());
                continue;
            }
            publishJudgeRequestedOutbox(submission);
            return SubmissionCreateResult.of(submission.getId(), submission.getStatus());
        }
        throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                "동시 제출 경합으로 인해 접수에 실패했습니다. 다시 시도해주세요.");
    }

    private SubmissionCreateResult toIdempotentResult(Submission existing, SubmissionCreateCommand command) {
        boolean sameBody = existing.getProblemId().equals(command.problemId())
                && existing.getCode().equals(command.code())
                && existing.getLanguage() == toSubmissionLanguage(command.language());
        if (!sameBody) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        }
        log.info("[Judge] 동일 Idempotency-Key 재요청 감지 — 기존 제출 반환. submissionId={}", existing.getId());
        return SubmissionCreateResult.of(existing.getId(), existing.getStatus());
    }

    private SubmissionLanguage toSubmissionLanguage(String language) {
        try {
            return SubmissionLanguage.valueOf(language);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "지원하지 않는 language 값입니다: " + language);
        }
    }

    private void publishJudgeRequestedOutbox(Submission submission) {
        submissionEventOutboxRepository.save(SubmissionEventOutbox.create(
                submission.getId(), JUDGE_REQUESTED_EVENT_TYPE, writeJudgeRequestedPayload(submission.getId())));

    }

    private String writeJudgeRequestedPayload(UUID submissionId) {
        try {
            return jsonMapper.writeValueAsString(new JudgeRequestedPayload(submissionId));
        } catch (JacksonException ex) {
            throw new IllegalArgumentException("JudgeRequested payload 직렬화 실패. submissionId=" + submissionId, ex);
        }
    }

    private record JudgeRequestedPayload(UUID submissionId) {}
}
