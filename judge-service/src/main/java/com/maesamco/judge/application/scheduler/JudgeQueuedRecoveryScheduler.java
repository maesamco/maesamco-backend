package com.maesamco.judge.application.scheduler;

import com.maesamco.judge.application.facade.JudgeExecutionFacade;
import com.maesamco.judge.domain.entity.Submission;
import com.maesamco.judge.domain.entity.SubmissionStatus;
import com.maesamco.judge.domain.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * 채점이 시작되지 않은 채 QUEUED에 오래 머문 제출을 다시 집어 채점한다(이슈 #350).
 *
 * <p>QUEUED는 "Kafka에 발행됐고 소비자가 RUNNING으로 바꾸기를 기다리는" 상태다. 그런데 소비자가
 * 메시지를 소비하고도 RUNNING 전이를 못 하면(낙관적 락 충돌, 일시적 DB 오류 등) 메시지는 이미 소비
 * 완료 처리돼 재전달되지 않아 제출이 QUEUED에 영구히 남는다. RETRY_WAIT는 {@link JudgeRetryScheduler}가
 * 집어가지만 QUEUED를 집어가는 곳이 없었다.</p>
 *
 * <p>QUEUED는 아직 Judge0에 제출하기 전이라 다시 실행해도 중복 채점이 없다. 소비자와 동시에 집어도
 * RUNNING 전이의 낙관적 락으로 한쪽만 이기고, 진 쪽은 최신 상태를 보고 스킵한다
 * ({@link JudgeExecutionFacade#execute}).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "judge.queued-recovery",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class JudgeQueuedRecoveryScheduler {

    private final SubmissionRepository submissionRepository;
    private final JudgeExecutionFacade judgeExecutionFacade;

    /** 정상 흐름에서 QUEUED는 소비자가 곧바로 RUNNING으로 바꾸므로, 이 시간을 넘기면 정체로 본다. */
    @Value("${judge.queued-recovery.stale-seconds:60}")
    private long staleSeconds;

    @Value("${judge.queued-recovery.batch-size:50}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${judge.queued-recovery.fixed-delay-ms:30000}")
    public void recoverStalledQueuedSubmissions() {
        Instant threshold = Instant.now().minusSeconds(staleSeconds);
        List<Submission> stalled = submissionRepository.findByStatusAndUpdatedAtBeforeOrderBySubmittedAtAsc(
                SubmissionStatus.QUEUED, threshold, PageRequest.of(0, batchSize));

        for (Submission submission : stalled) {
            log.warn("[Judge] QUEUED에 {}초 넘게 머문 제출을 다시 채점한다. submissionId={}",
                    staleSeconds, submission.getId());
            try {
                judgeExecutionFacade.execute(submission.getId());
            } catch (ObjectOptimisticLockingFailureException e) {
                log.debug("[Judge] QUEUED 복구 중 낙관적 락 충돌 — 다른 흐름이 처리 중이라 스킵. submissionId={}",
                        submission.getId());
            } catch (Exception e) {
                log.error("[Judge] QUEUED 복구 실행 중 예외 — 다음 스케줄에서 다시 시도. submissionId={}",
                        submission.getId(), e);
            }
        }
    }
}
