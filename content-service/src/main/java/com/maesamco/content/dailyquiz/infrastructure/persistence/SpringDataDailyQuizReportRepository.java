package com.maesamco.content.dailyquiz.infrastructure.persistence;

import com.maesamco.content.dailyquiz.domain.entity.DailyQuizReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface SpringDataDailyQuizReportRepository
        extends JpaRepository<DailyQuizReport, UUID> {

    /*
     * TODO 신고 서비스 구현 시 다음 처리를 조건부 UPDATE로 구현합니다.
     *
     * 1. 구버전에 늦게 접수된 신고 단건 자동 해결
     *    WHERE id = :reportId
     *      AND resolved_at IS NULL
     *
     * 2. 관리자 새 버전 생성 후 이전 버전의 미해결 신고 일괄 처리
     *    WHERE daily_quiz_question_id = :questionId
     *      AND resolved_at IS NULL
     *
     * UPDATE 영향 행 수를 기준으로 최초 처리 여부와 실제 처리 건수를 판단합니다.
     * 일괄 처리 쿼리를 도입할 때 미해결 신고 부분 인덱스도
     * 후속 Flyway 마이그레이션으로 함께 추가합니다.
     */
}
