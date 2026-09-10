package com.maesamco.content.dailyquiz.infrastructure.scheduler;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
// import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/**
 * daily-quiz.batch 설정을 DailyQuizBatchProperties에 바인딩하고
 * 다른 Spring Bean에서 생성자 주입으로 사용할 수 있도록 등록
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DailyQuizBatchProperties.class)
// @EnableScheduling
public class DailyQuizBatchConfig {

    /**
     * 배치 실행 날짜와 개념 조회 cutoff가 동일한 timezone을 사용하도록
     * Daily Quiz 전용 Clock을 제공합니다.
     */
    @Bean
    public Clock dailyQuizClock(DailyQuizBatchProperties properties) {
        return Clock.system(properties.zoneId());
    }
}
