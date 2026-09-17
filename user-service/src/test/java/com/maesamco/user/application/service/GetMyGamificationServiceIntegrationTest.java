package com.maesamco.user.application.service;

import com.maesamco.user.domain.entity.LearningLevel;
import com.maesamco.user.domain.entity.User;
import com.maesamco.user.domain.entity.UserGamificationState;
import com.maesamco.user.domain.repository.UserGamificationStateRepository;
import com.maesamco.user.domain.repository.UserRepository;
import com.maesamco.user.global.config.JpaAuditingConfig;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import com.maesamco.user.infrastructure.persistence.UserGamificationStateRepositoryImpl;
import com.maesamco.user.infrastructure.persistence.UserRepositoryImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 내 게이미피케이션 상태 조회의 실제 PostgreSQL 연동을 검증합니다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@Import({
        JpaAuditingConfig.class,
        UserRepositoryImpl.class,
        UserGamificationStateRepositoryImpl.class,
        GetMyGamificationService.class
})
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GetMyGamificationServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                    DockerImageName.parse(
                            "postgres:16-alpine"
                    )
            );

    @Autowired
    private GetMyGamificationService getMyGamificationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserGamificationStateRepository
            userGamificationStateRepository;

    @Test
    @DisplayName(
            "PostgreSQL에 저장된 초기 게이미피케이션 상태를 조회한다"
    )
    void getMyGamification_readsInitialState() {
        User user =
                saveActiveUser(
                        1
                );

        userGamificationStateRepository.save(
                UserGamificationState.create(
                        user.getId()
                )
        );

        GetMyGamificationResult result =
                getMyGamificationService
                        .getMyGamification(
                                user.getId()
                        );

        assertThat(result.totalXp())
                .isZero();

        assertThat(result.level())
                .isEqualTo(1);

        assertThat(result.currentStreak())
                .isZero();

        assertThat(result.longestStreak())
                .isZero();

        assertThat(result.lastActivityDate())
                .isNull();
    }

    @Test
    @DisplayName(
            "PostgreSQL에 저장된 XP와 스트릭 상태를 정확히 조회한다"
    )
    void getMyGamification_readsPersistedState() {
        User user =
                saveActiveUser(
                        2
                );

        UserGamificationState state =
                UserGamificationState.create(
                        user.getId()
                );

        state.applyXp(
                120L,
                2
        );

        for (int day = 1; day <= 7; day++) {
            state.recordActivity(
                    LocalDate.of(
                            2026,
                            9,
                            day
                    )
            );
        }

        state.recordActivity(
                LocalDate.of(
                        2026,
                        9,
                        14
                )
        );

        state.recordActivity(
                LocalDate.of(
                        2026,
                        9,
                        15
                )
        );

        state.recordActivity(
                LocalDate.of(
                        2026,
                        9,
                        16
                )
        );

        userGamificationStateRepository.save(
                state
        );

        GetMyGamificationResult result =
                getMyGamificationService
                        .getMyGamification(
                                user.getId()
                        );

        assertThat(result.totalXp())
                .isEqualTo(120L);

        assertThat(result.level())
                .isEqualTo(2);

        assertThat(result.currentStreak())
                .isEqualTo(3);

        assertThat(result.longestStreak())
                .isEqualTo(7);

        assertThat(result.lastActivityDate())
                .isEqualTo(
                        LocalDate.of(
                                2026,
                                9,
                                16
                        )
                );
    }

    @Test
    @DisplayName(
            "활성 사용자에게 상태가 없으면 GAMIFICATION_STATE_NOT_FOUND를 반환한다"
    )
    void getMyGamification_rejectsMissingState() {
        User user =
                saveActiveUser(
                        3
                );

        assertThatThrownBy(
                () ->
                        getMyGamificationService
                                .getMyGamification(
                                        user.getId()
                                )
        )
                .isInstanceOf(BusinessException.class)
                .extracting(
                        exception ->
                                ((BusinessException) exception)
                                        .getErrorCode()
                )
                .isEqualTo(
                        ErrorCode.GAMIFICATION_STATE_NOT_FOUND
                );
    }

    @Test
    @DisplayName(
            "정지 사용자는 상태가 존재해도 USER_NOT_ACTIVE로 거부한다"
    )
    void getMyGamification_rejectsSuspendedUser() {
        User user =
                saveActiveUser(
                        4
                );

        userGamificationStateRepository.save(
                UserGamificationState.create(
                        user.getId()
                )
        );

        user.suspend();

        userRepository.save(
                user
        );

        assertThatThrownBy(
                () ->
                        getMyGamificationService
                                .getMyGamification(
                                        user.getId()
                                )
        )
                .isInstanceOf(BusinessException.class)
                .extracting(
                        exception ->
                                ((BusinessException) exception)
                                        .getErrorCode()
                )
                .isEqualTo(
                        ErrorCode.USER_NOT_ACTIVE
                );
    }

    @Test
    @DisplayName(
            "논리 삭제된 사용자는 USER_NOT_FOUND로 처리한다"
    )
    void getMyGamification_rejectsWithdrawnUser() {
        User user =
                saveActiveUser(
                        5
                );

        userGamificationStateRepository.save(
                UserGamificationState.create(
                        user.getId()
                )
        );

        user.softDelete(
                user.getId(),
                Instant.parse(
                        "2026-09-16T07:00:00Z"
                )
        );

        userRepository.save(
                user
        );

        assertThatThrownBy(
                () ->
                        getMyGamificationService
                                .getMyGamification(
                                        user.getId()
                                )
        )
                .isInstanceOf(BusinessException.class)
                .extracting(
                        exception ->
                                ((BusinessException) exception)
                                        .getErrorCode()
                )
                .isEqualTo(
                        ErrorCode.USER_NOT_FOUND
                );
    }

    @Test
    @DisplayName(
            "존재하지 않는 사용자는 USER_NOT_FOUND를 반환한다"
    )
    void getMyGamification_rejectsUnknownUser() {
        UUID unknownUserId =
                UUID.fromString(
                        "99999999-9999-9999-9999-999999999999"
                );

        assertThatThrownBy(
                () ->
                        getMyGamificationService
                                .getMyGamification(
                                        unknownUserId
                                )
        )
                .isInstanceOf(BusinessException.class)
                .extracting(
                        exception ->
                                ((BusinessException) exception)
                                        .getErrorCode()
                )
                .isEqualTo(
                        ErrorCode.USER_NOT_FOUND
                );
    }

    private User saveActiveUser(
            int sequence
    ) {
        return userRepository.save(
                User.create(
                        "encrypted-email-" + sequence,
                        String.format(
                                "%064x",
                                sequence
                        ),
                        "password-hash-" + sequence,
                        "테스트사용자" + sequence,
                        sequence,
                        LearningLevel.BEGINNER
                )
        );
    }
}
