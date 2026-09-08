package com.maesamco.coaching.global.config;

import com.maesamco.coaching.global.exception.BusinessException;
import com.maesamco.coaching.global.exception.ErrorCode;
import io.github.resilience4j.common.circuitbreaker.configuration.CircuitBreakerConfigCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.EnumSet;
import java.util.Set;

/**
 * JudgeServiceAdapter/ContentServiceAdapter는 원격 서비스가 명시적으로 404를 준 경우
 * (FeignException.NotFound)를 SUBMISSION_NOT_FOUND/PROBLEM_NOT_FOUND로 변환해서
 * 던진다 — 이건 실제 장애가 아니라 정상적인 비즈니스 결과다.
 *
 * 그런데 이 예외와 진짜 통신 실패(FEIGN_CLIENT_ERROR)가 팀 컨벤션 13절("예외 클래스를
 * 서비스별로 안 나누고 BusinessException 하나로 통일")에 따라 같은 BusinessException
 * 클래스를 쓴다 — resilience4j의 ignore-exceptions(application.yml)는 예외 클래스
 * 단위로만 필터링해서 이 둘을 구분할 수 없다. 그대로 BusinessException 전체를
 * ignore하면 FEIGN_CLIENT_ERROR(진짜 실패)까지 서킷 판정에서 빠져서 서킷 브레이커
 * 자체가 무력화된다.
 *
 * 그래서 ErrorCode 값을 보는 Predicate를 CircuitBreakerConfigCustomizer로 프로그래밍
 * 등록한다 — judge-service/content-service 인스턴스가 없으면 정상적인 "존재하지 않음"
 * 응답도 실패율에 잡혀서, 그 응답이 절반만 섞여도(sliding-window-size=10,
 * minimum-number-of-calls=5, failure-rate-threshold=50) 원격 서비스가 완전히
 * 건강한데도 서킷이 열릴 수 있었다(PR #127 심층 재검토, 2026-09-09).
 */
@Configuration
public class CircuitBreakerIgnorableFailureConfig {

    /**
     * 원격 서비스가 명시적으로 "존재하지 않음"으로 응답해서 생기는 ErrorCode만 담는다.
     * FEIGN_CLIENT_ERROR(통신 실패)는 절대 포함하지 않는다 — 그건 서킷이 계속 실패로
     * 카운트해야 한다. 새 FeignAdapter가 같은 성격의 *_NOT_FOUND를 추가하면 여기도
     * 같이 추가할 것.
     */
    private static final Set<ErrorCode> IGNORABLE_NOT_FOUND_CODES =
            EnumSet.of(ErrorCode.SUBMISSION_NOT_FOUND, ErrorCode.PROBLEM_NOT_FOUND);

    @Bean
    public CircuitBreakerConfigCustomizer judgeServiceCircuitBreakerCustomizer() {
        return ignorableNotFoundCustomizer("judge-service");
    }

    @Bean
    public CircuitBreakerConfigCustomizer contentServiceCircuitBreakerCustomizer() {
        return ignorableNotFoundCustomizer("content-service");
    }

    private CircuitBreakerConfigCustomizer ignorableNotFoundCustomizer(String circuitBreakerName) {
        return CircuitBreakerConfigCustomizer.of(circuitBreakerName,
                builder -> builder.ignoreException(this::isIgnorableNotFound));
    }

    private boolean isIgnorableNotFound(Throwable throwable) {
        return throwable instanceof BusinessException businessException
                && IGNORABLE_NOT_FOUND_CODES.contains(businessException.getErrorCode());
    }
}
