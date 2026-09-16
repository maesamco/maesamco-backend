package com.maesamco.content.application.dailyquiz.port;

import com.maesamco.content.application.dailyquiz.result.DailyQuizTargetUserPage;

import java.util.UUID;

/**
 * Daily Quiz 생성 대상 사용자를 cursor 방식으로 조회하는 포트
 */
public interface DailyQuizTargetUserPort {

    DailyQuizTargetUserPage getTargetUsers(UUID cursor, int size);
}
