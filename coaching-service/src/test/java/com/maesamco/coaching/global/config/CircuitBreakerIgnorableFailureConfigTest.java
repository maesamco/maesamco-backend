package com.maesamco.coaching.global.config;

import com.maesamco.coaching.global.exception.BusinessException;
import com.maesamco.coaching.global.exception.ErrorCode;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.common.circuitbreaker.configuration.CircuitBreakerConfigCustomizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PR #127 심층 재검토(2026-09-09) — 정상적인 *_NOT_FOUND는 서킷 브레이커 실패율
 * 계산에서 빠지되, 진짜 통신 실패(FEIGN_CLIENT_ERROR)는 여전히 실패로 잡히는지
 * Predicate 자체를 직접 검증한다. 실제 임계치(sliding-window-size 등)를 채워서
 * 상태 전이를 유도하는 통합 테스트보다, 이 Predicate 하나만 격리해서 검증하는 쪽이
 * application.yml 설정값 변경에 흔들리지 않고 더 결정적이다.
 */
class CircuitBreakerIgnorableFailureConfigTest {

    private final CircuitBreakerIgnorableFailureConfig config = new CircuitBreakerIgnorableFailureConfig();

    @Test
    @DisplayName("judge-service/content-service 커스터마이저 둘 다 SUBMISSION_NOT_FOUND/PROBLEM_NOT_FOUND를 실패로 안 잡는다")
    void ignoresNotFoundBusinessExceptions() {
        assertThat(ignorePredicateFor(config.judgeServiceCircuitBreakerCustomizer(), "judge-service")
                .test(new BusinessException(ErrorCode.SUBMISSION_NOT_FOUND))).isTrue();
        assertThat(ignorePredicateFor(config.contentServiceCircuitBreakerCustomizer(), "content-service")
                .test(new BusinessException(ErrorCode.PROBLEM_NOT_FOUND))).isTrue();
    }

    @Test
    @DisplayName("진짜 통신 실패(FEIGN_CLIENT_ERROR)는 여전히 실패로 잡힌다 — 서킷 브레이커가 무력화되지 않았는지 확인")
    void stillRecordsGenuineFeignFailures() {
        assertThat(ignorePredicateFor(config.judgeServiceCircuitBreakerCustomizer(), "judge-service")
                .test(new BusinessException(ErrorCode.FEIGN_CLIENT_ERROR))).isFalse();
        assertThat(ignorePredicateFor(config.contentServiceCircuitBreakerCustomizer(), "content-service")
                .test(new BusinessException(ErrorCode.FEIGN_CLIENT_ERROR))).isFalse();
    }

    @Test
    @DisplayName("BusinessException이 아닌 예외(예: 순수 네트워크 예외)는 실패로 잡힌다")
    void stillRecordsNonBusinessExceptions() {
        assertThat(ignorePredicateFor(config.judgeServiceCircuitBreakerCustomizer(), "judge-service")
                .test(new IllegalStateException("connection reset"))).isFalse();
    }

    @Test
    @DisplayName("커스터마이저 이름이 각각 judge-service/content-service 인스턴스를 정확히 가리킨다")
    void targetsExpectedInstanceNames() {
        assertThat(config.judgeServiceCircuitBreakerCustomizer().name()).isEqualTo("judge-service");
        assertThat(config.contentServiceCircuitBreakerCustomizer().name()).isEqualTo("content-service");
    }

    private Predicate<Throwable> ignorePredicateFor(CircuitBreakerConfigCustomizer customizer, String expectedName) {
        assertThat(customizer.name()).isEqualTo(expectedName);
        CircuitBreakerConfig.Builder builder = CircuitBreakerConfig.custom();
        customizer.customize(builder);
        return builder.build().getIgnoreExceptionPredicate();
    }
}
