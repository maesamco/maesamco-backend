package com.maesamco.content.dailyquiz.infrastructure.scheduler;

import com.maesamco.content.dailyquiz.application.service.DailyQuizBatchExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
// import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 설정된 시간에 Daily Quiz 배치를 시작하고 같은 JVM 내 중복 실행을 방지하는 스케줄러입니다.
 *
 * TODO: 개념 후보 조회에 필요한 선행 Repository 구현이 병합되면
 * 이 클래스를 Spring Bean으로 등록하고 스케줄링을 활성화합니다.
 */
// @Component
@Slf4j
@RequiredArgsConstructor
public class DailyQuizBatchScheduler {

    private final DailyQuizBatchExecutionService batchExecutionService;
    private final DailyQuizBatchProperties properties;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Scheduled(
            cron = "${daily-quiz.batch.cron}",
            zone = "${daily-quiz.batch.zone}"
    )
    public void run() {
        // 이미 배치가 실행 중이면 이번 실행을 건너뜁니다.
        if (!running.compareAndSet(false, true)) {
            log.warn("Daily Quiz 배치가 이미 실행 중이므로 이번 실행을 건너뜁니다.");
            return;
        }

        try {
            // 설정된 timezone을 기준으로 attemptDate를 계산합니다.
            LocalDate attemptDate = LocalDate.now(properties.zoneId());

            // attemptDate와 chunkSize를 전달해 배치 실행 서비스를 호출합니다.
            log.info(
                    "Daily Quiz 배치를 시작합니다. attemptDate={}, chunkSize={}",
                    attemptDate,
                    properties.chunkSize()
            );
            batchExecutionService.execute(attemptDate, properties.chunkSize());
            log.info("Daily Quiz 배치를 종료했습니다. attemptDate={}", attemptDate);
        } catch (RuntimeException exception) {
            log.error("Daily Quiz 배치 실행 중 오류가 발생했습니다.", exception);
        } finally {
            // 성공 또는 실패와 관계없이 실행 상태를 반드시 해제합니다.
            running.set(false);
        }
    }
}
