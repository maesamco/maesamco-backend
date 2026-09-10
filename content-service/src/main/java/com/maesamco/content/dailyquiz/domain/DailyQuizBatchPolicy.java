package com.maesamco.content.dailyquiz.domain;

/**
 * Daily Quiz 배치 실행 범위에 관한 정책입니다.
 */
public final class DailyQuizBatchPolicy {

    public static final int MIN_BATCH_CHUNK_SIZE = 1;
    public static final int MAX_BATCH_CHUNK_SIZE = 1000;

    private DailyQuizBatchPolicy() {
    }
}
