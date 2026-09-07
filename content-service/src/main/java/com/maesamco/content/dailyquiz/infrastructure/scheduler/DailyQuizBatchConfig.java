package com.maesamco.content.dailyquiz.infrastructure.scheduler;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * daily-quiz.batch 설정을 DailyQuizBatchProperties에 바인딩하고
 * 다른 Spring Bean에서 생성자 주입으로 사용할 수 있도록 등록
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DailyQuizBatchProperties.class)
public class DailyQuizBatchConfig {
}
