package com.maesamco.content.domain.dailyquiz;

import org.junit.jupiter.api.Test;

import static com.maesamco.content.domain.dailyquiz.DailyQuizPolicy.TARGET_QUESTION_COUNT;
import static org.assertj.core.api.Assertions.assertThat;

class DailyQuizQuestionTypePolicyTest {

    @Test
    void 문항_유형_분배_수는_목표_문항_수와_일치한다() {
        assertThat(DailyQuizQuestionTypePolicy.targetTypes())
                .hasSize(TARGET_QUESTION_COUNT);
    }
}
