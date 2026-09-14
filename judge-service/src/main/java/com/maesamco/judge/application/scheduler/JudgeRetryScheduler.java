package com.maesamco.judge.application.scheduler;

import com.maesamco.judge.application.facade.JudgeExecutionFacade;
import com.maesamco.judge.domain.entity.Submission;
import com.maesamco.judge.domain.entity.SubmissionStatus;
import com.maesamco.judge.domain.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JudgeRetryScheduler {

    private final SubmissionRepository submissionRepository;
    private final JudgeExecutionFacade  judgeExecutionFacade;

    @Scheduled(fixedDelayString = "${judge.retry.fixed-delay-ms:10000}") // Judge0 재시도는 폴링만큼 자주 돌 필요는 없어서 일단 여유롭게 10초로 잡음.
    public void retryPendingSubmissions() {
        List<Submission> retryTargets =
                submissionRepository.findTop100ByStatusOrderBySubmittedAtAsc(SubmissionStatus.RETRY_WAIT);

        for(Submission submission : retryTargets) {
            try {
                judgeExecutionFacade.execute(submission.getId());
            } catch (ObjectOptimisticLockingFailureException e) {
                log.debug("[Judge] 재시도 중 낙관적 락 충돌 — 스킵. submissionId={}", submission.getId());
            } catch (Exception e){
                log.error("[Judge] 재시도 재실행 중 예외 — 다음 스케줄에서 다시 시도. submissionId={}", submission.getId(), e);
            }
        }
    }
}
