package com.maesamco.judge.application.command_service;

import com.maesamco.judge.application.command.SubmissionCreateCommand;
import com.maesamco.judge.application.result.SubmissionCreateResult;
import com.maesamco.judge.domain.entity.ProblemExecutionSpec;
import com.maesamco.judge.domain.entity.Submission;
import com.maesamco.judge.domain.entity.SubmissionLanguage;
import com.maesamco.judge.domain.repository.ProblemExecutionSpecRepository;
import com.maesamco.judge.domain.repository.SubmissionRepository;
import com.maesamco.judge.global.exception.BusinessException;
import com.maesamco.judge.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class SubmissionCommandService {

    private static final int MAX_SAVE_RETRY = 3;

    private final SubmissionRepository submissionRepository;
    private final ProblemExecutionSpecRepository  problemExecutionSpecRepository;
    private final SubmissionSaveExecutor submissionSaveExecutor;

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
                submissionSaveExecutor.saveWithOutbox(submission);
            } catch (DataIntegrityViolationException ex) {
                // 이전 시도의 트랜잭션은 이미 REQUIRES_NEW 경계에서 롤백/종료됐으므로
                // 여기서의 재조회는 새 트랜잭션에서 안전하게 실행된다.
                Optional<Submission> racedByKey = submissionRepository.findByIdempotencyKey(command.idempotencyKey());
                if (racedByKey.isPresent()) {
                    return toIdempotentResult(racedByKey.get(), command);
                }
                log.info("[Judge] 제출 저장 경합 상태(attemptNo 추정) — 재시도 {}/{}. userId={}, problemId={}",
                        attempt, MAX_SAVE_RETRY, command.userId(), command.problemId());
                continue;
            }
            return SubmissionCreateResult.of(submission.getId(), submission.getStatus());
        }
        throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                "동시 제출 경합으로 인해 접수에 실패했습니다. 다시 시도해주세요.");
    }

    private SubmissionCreateResult toIdempotentResult(Submission existing, SubmissionCreateCommand command) {
        boolean sameRequest = existing.getUserId().equals(command.userId())
                && existing.getProblemId().equals(command.problemId())
                && existing.getCode().equals(command.code())
                && existing.getLanguage() == toSubmissionLanguage(command.language());
        if (!sameRequest) {
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
}
