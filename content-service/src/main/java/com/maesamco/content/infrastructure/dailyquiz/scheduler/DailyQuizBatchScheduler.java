package com.maesamco.content.infrastructure.dailyquiz.scheduler;

import com.maesamco.content.application.dailyquiz.service.DailyQuizBatchExecutionService;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 설정된 시간에 Daily Quiz 배치를 시작하고 같은 JVM 내 중복 실행을 방지하는 스케줄러입니다.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DailyQuizBatchScheduler {

    private final DailyQuizBatchExecutionService batchExecutionService;
    private final DailyQuizBatchProperties properties;
    private final Clock dailyQuizClock;
    private final ScheduledExecutorService retryExecutor;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Scheduled(
            cron = "${daily-quiz.batch.cron}",
            zone = "${daily-quiz.batch.zone}"
    )
    public void run() {
        runAttempt(LocalDate.now(dailyQuizClock), 0);
    }

    private void runAttempt(LocalDate attemptDate, int retryCount) {
        if (!running.compareAndSet(false, true)) {
            log.warn("Daily Quiz 배치가 이미 실행 중이어서 실행을 연기합니다. attemptDate={}", attemptDate);
            scheduleAttempt(attemptDate, retryCount);
            return;
        }

        try {
            log.info(
                    "Daily Quiz 배치를 시작합니다. attemptDate={}, chunkSize={}, retryCount={}",
                    attemptDate,
                    properties.chunkSize(),
                    retryCount
            );
            batchExecutionService.execute(attemptDate, properties.chunkSize());
            log.info("Daily Quiz 배치를 종료했습니다. attemptDate={}", attemptDate);
        } catch (RuntimeException exception) {
            log.error("Daily Quiz 배치 실행에 실패했습니다. attemptDate={}, retryCount={}",
                    attemptDate, retryCount, exception);
            if (retryCount < properties.maxRetries() && isRetryable(exception)) {
                scheduleAttempt(attemptDate, retryCount + 1);
            }
        } finally {
            running.set(false);
        }
    }

    private boolean isRetryable(RuntimeException exception) {
        if (exception instanceof BusinessException businessException) {
            return businessException.getErrorCode() == ErrorCode.FEIGN_CLIENT_ERROR;
        }
        return exception instanceof DataAccessException;
    }

    private void scheduleAttempt(LocalDate attemptDate, int retryCount) {
        try {
            retryExecutor.schedule(
                    () -> runAttempt(attemptDate, retryCount),
                    properties.retryDelayMs(),
                    TimeUnit.MILLISECONDS
            );
            log.info("Daily Quiz 배치 재실행을 예약했습니다. attemptDate={}, retryCount={}, delayMs={}",
                    attemptDate, retryCount, properties.retryDelayMs());
        } catch (RejectedExecutionException exception) {
            log.error("Daily Quiz 배치 재실행 예약에 실패했습니다. attemptDate={}, retryCount={}",
                    attemptDate, retryCount, exception);
        }
    }
}
