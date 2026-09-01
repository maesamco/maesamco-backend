package com.maesamco.content.dailyquiz.domain.repository;

import com.maesamco.content.dailyquiz.domain.entity.DailyQuizReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DailyQuizReportRepository extends JpaRepository<DailyQuizReport, UUID> {

    /*
     * TODO 관리자 새 버전 생성 서비스 구현 시 resolvedAt IS NULL 조건을 포함한
     * 미해결 신고 일괄 UPDATE와 이를 위한 부분 인덱스를 함께 추가합니다.
     */
}
