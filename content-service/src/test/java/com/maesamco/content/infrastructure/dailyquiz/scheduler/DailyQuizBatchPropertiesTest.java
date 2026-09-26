package com.maesamco.content.infrastructure.dailyquiz.scheduler;

import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DailyQuizBatchPropertiesTest {

    @Test
    void 재시도_횟수는_0부터_5까지만_허용한다() {
        assertThatThrownBy(() -> new DailyQuizBatchProperties(
                "0 0 3 * * *", "Asia/Seoul", 100, -1, 300_000
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
    }

    @Test
    void 재시도_간격은_최소_1초여야_한다() {
        assertThatThrownBy(() -> new DailyQuizBatchProperties(
                "0 0 3 * * *", "Asia/Seoul", 100, 2, 0
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
    }
}
