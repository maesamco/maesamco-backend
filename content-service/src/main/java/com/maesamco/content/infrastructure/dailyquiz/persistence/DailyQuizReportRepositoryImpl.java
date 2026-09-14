package com.maesamco.content.infrastructure.dailyquiz.persistence;

import com.maesamco.content.domain.dailyquiz.entity.DailyQuizReport;
import com.maesamco.content.domain.dailyquiz.repository.DailyQuizReportRepository;
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
