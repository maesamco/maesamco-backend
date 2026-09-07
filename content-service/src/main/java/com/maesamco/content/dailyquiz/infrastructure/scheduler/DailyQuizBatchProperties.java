package com.maesamco.content.dailyquiz.infrastructure.scheduler;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.scheduling.support.CronExpression;

import java.time.ZoneId;

/**
 * Daily Quiz 배치 실행에 필요한 설정값입니다.
 */
@ConfigurationProperties(prefix = "daily-quiz.batch")
public record DailyQuizBatchProperties(
        // 배치 실행 시간
        String cron,
        // 실행 및 퀴즈 날짜 기준 timezone
        String zone,
        // 한 번에 조회할 사용자 수
        int chunkSize
) {
    public DailyQuizBatchProperties {
        if (cron == null || cron.isBlank()) {
            throw new IllegalArgumentException("Daily Quiz 배치 cron은 필수입니다.");
        }
        CronExpression.parse(cron);

        if (zone == null || zone.isBlank()) {
            throw new IllegalArgumentException("Daily Quiz 배치 timezone은 필수입니다.");
        }
        zone = ZoneId.of(zone).getId();

        if (chunkSize < 1) {
            throw new IllegalArgumentException("Daily Quiz 배치 chunk size는 1 이상이어야 합니다.");
        }

        if (chunkSize > 1000) {
            throw new IllegalArgumentException("Daily Quiz 배치 chunk size는 1000 이하여야 합니다.");
        }
    }

    public ZoneId zoneId() {
        return ZoneId.of(zone);
    }
}
