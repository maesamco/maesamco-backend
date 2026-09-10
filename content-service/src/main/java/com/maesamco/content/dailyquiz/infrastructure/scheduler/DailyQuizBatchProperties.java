package com.maesamco.content.dailyquiz.infrastructure.scheduler;

import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.scheduling.support.CronExpression;

import java.time.DateTimeException;
import java.time.ZoneId;

import static com.maesamco.content.dailyquiz.domain.DailyQuizBatchPolicy.MAX_BATCH_CHUNK_SIZE;
import static com.maesamco.content.dailyquiz.domain.DailyQuizBatchPolicy.MIN_BATCH_CHUNK_SIZE;

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
            throw invalidInput("Daily Quiz 배치 cron은 필수입니다.");
        }
        try {
            CronExpression.parse(cron);
        } catch (IllegalArgumentException exception) {
            throw invalidInput("Daily Quiz 배치 cron 형식이 올바르지 않습니다.");
        }

        if (zone == null || zone.isBlank()) {
            throw invalidInput("Daily Quiz 배치 timezone은 필수입니다.");
        }
        try {
            zone = ZoneId.of(zone).getId();
        } catch (DateTimeException exception) {
            throw invalidInput("Daily Quiz 배치 timezone이 올바르지 않습니다.");
        }

        if (chunkSize < MIN_BATCH_CHUNK_SIZE) {
            throw invalidInput(
                    "Daily Quiz 배치 chunk size는 %d 이상이어야 합니다."
                            .formatted(MIN_BATCH_CHUNK_SIZE)
            );
        }

        if (chunkSize > MAX_BATCH_CHUNK_SIZE) {
            throw invalidInput(
                    "Daily Quiz 배치 chunk size는 %d 이하여야 합니다."
                            .formatted(MAX_BATCH_CHUNK_SIZE)
            );
        }
    }

    private static BusinessException invalidInput(String message) {
        return new BusinessException(ErrorCode.INVALID_INPUT_VALUE, message);
    }

    public ZoneId zoneId() {
        return ZoneId.of(zone);
    }
}
