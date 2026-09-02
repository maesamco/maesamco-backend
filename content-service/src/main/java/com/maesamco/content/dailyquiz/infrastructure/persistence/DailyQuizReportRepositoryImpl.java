package com.maesamco.content.dailyquiz.infrastructure.persistence;

import com.maesamco.content.dailyquiz.domain.entity.DailyQuizReport;
import com.maesamco.content.dailyquiz.domain.repository.DailyQuizReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DailyQuizReportRepositoryImpl implements DailyQuizReportRepository {

    private final SpringDataDailyQuizReportRepository springDataRepository;

    @Override
    public DailyQuizReport save(DailyQuizReport report) {
        return springDataRepository.save(report);
    }
}
