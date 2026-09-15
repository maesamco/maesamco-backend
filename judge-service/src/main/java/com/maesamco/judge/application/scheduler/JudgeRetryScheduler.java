package com.maesamco.judge.application.scheduler;

import com.maesamco.judge.application.facade.JudgeExecutionFacade;
import com.maesamco.judge.domain.entity.Submission;
import com.maesamco.judge.domain.entity.SubmissionStatus;
import com.maesamco.judge.domain.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JudgeRetryScheduler {

    private static final long BASE_BACKOFF_SECONDS = 10L;

    private final SubmissionRepository submissionRepository;
    private final JudgeExecutionFacade judgeExecutionFacade;

    @Value("${judge.retry.batch-size:100}")
    private int batchSize;

    @Value("${judge.retry.max-attempts:3}")
    private int maxRetryAttempts;

    @Scheduled(fixedDelayString = "${judge.retry.fixed-delay-ms:10000}") // Judge0 재시도는 폴링만큼 자주 돌 필요는 없어서 일단 여유롭게 10초로 잡음.
    public void retryPendingSubmissions() {
        List<Submission> retryTargets =
                submissionRepository.findByStatusOrderBySubmittedAtAscForUpdateSkipLocked(
                        SubmissionStatus.RETRY_WAIT.name(),batchSize);

        for (Submission submission : retryTargets) {
            if (!isRetryDue(submission)) {
                continue;
            }
            try {
                judgeExecutionFacade.execute(submission.getId());
            } catch (ObjectOptimisticLockingFailureException e) {
                log.debug("[Judge] 재시도 중 낙관적 락 충돌 — 스킵. submissionId={}", submission.getId());
            } catch (Exception e) {
                log.error("[Judge] 재시도 재실행 중 예외 — 다음 스케줄에서 다시 시도. submissionId={}", submission.getId(), e);
            }
        }
    }

    /**
     * retryCount 기준 지수 백오프 — 재시도 1회차는 10초, 2회차는 20초, 3회차는 40초 후에 재시도 대상이 됨.
     * exponent 상한을 maxRetryAttempts로 잡아둔 건, retryCount가 정책상 이 값을 넘을 수 없기도 하고
     * 혹시 모를 비정상 값에도 시프트 연산이 안전하도록 하기 위함.
     */
    private boolean isRetryDue(Submission submission) {
        int exponent = Math.min(Math.max(submission.getRetryCount() - 1, 0), maxRetryAttempts);
        long backoffSeconds = BASE_BACKOFF_SECONDS * (1L << exponent);
        Instant readyAt = submission.getUpdatedAt().plusSeconds(backoffSeconds);
        return !readyAt.isAfter(Instant.now());
    }
}