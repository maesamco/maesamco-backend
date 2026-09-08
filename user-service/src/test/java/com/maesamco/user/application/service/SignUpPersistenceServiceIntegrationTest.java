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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * SignUpPersistenceService의 실제 PostgreSQL 트랜잭션을 검증합니다.
 *
 * <p>회원가입 과정에서 User와 초기 UserGamificationState가
 * 하나의 DB 트랜잭션으로 저장되는지 확인합니다.</p>
 *
 * <p>두 번째 저장 작업인 게이미피케이션 상태 저장에서 예외가 발생하면
 * 먼저 저장된 User까지 함께 롤백되는지 실제 PostgreSQL을 통해 검증합니다.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@Import({
        JpaAuditingConfig.class,
        UserRepositoryImpl.class,
        UserGamificationStateRepositoryImpl.class,
        SignUpPersistenceService.class
})
@Sql(
        scripts =
                "/db/migration/"
                        + "V2__add_active_user_unique_indexes.sql",
        executionPhase =
                Sql.ExecutionPhase.BEFORE_TEST_CLASS
)
@Testcontainers
@Transactional(
        propagation = Propagation.NOT_SUPPORTED
)
class SignUpPersistenceServiceIntegrationTest {

    /**
     * 실제 PostgreSQL 환경에서 트랜잭션을 검증합니다.
     */
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                    DockerImageName.parse(
                            "postgres:16-alpine"
                    )
            );

    @Autowired
    private SignUpPersistenceService
            signUpPersistenceService;

    @Autowired
    private UserRepository userRepository;

    /**
     * 정상 테스트에서는 실제 Repository로 동작하고,
     * 롤백 테스트에서만 저장 시 예외를 발생시키기 위해 Spy로 사용합니다.
     */
    @MockitoSpyBean
    private UserGamificationStateRepository
            gamificationStateRepository;

    @Test
    @DisplayName(
            "사용자와 초기 게이미피케이션 상태를 "
                    + "하나의 트랜잭션으로 저장한다"
    )
    void saveUser_commitsUserAndGamificationState() {
        // given
        String emailLookupHash =
                "a".repeat(64);

        User user =
                createUser(
                        emailLookupHash,
                        "TransactionUser"
                );

        // when
        User savedUser =
                signUpPersistenceService.saveUser(
                        user
                );

        // then
        User foundUser =
                userRepository
                        .findById(
                                savedUser.getId()
                        )
                        .orElseThrow();

        assertThat(foundUser.getId())
                .isEqualTo(
                        savedUser.getId()
                );

        assertThat(
                foundUser.getEmailLookupHash()
        ).isEqualTo(
                emailLookupHash
        );

        assertThat(
                foundUser.getNickname()
        ).isEqualTo(
                "TransactionUser"
        );

        UserGamificationState
                gamificationState =
                gamificationStateRepository
                        .findByUserId(
                                savedUser.getId()
                        )
                        .orElseThrow();

        assertThat(
                gamificationState.getUserId()
        ).isEqualTo(
                savedUser.getId()
        );

        assertThat(
                gamificationState.getTotalXp()
        ).isZero();

        assertThat(
                gamificationState.getLevel()
        ).isEqualTo(1);

        assertThat(
                gamificationState.getCurrentStreak()
        ).isZero();

        assertThat(
                gamificationState.getLongestStreak()
        ).isZero();

        assertThat(
                gamificationState.getLastActivityDate()
        ).isNull();
    }

    @Test
    @DisplayName(
            "게이미피케이션 상태 저장에 실패하면 "
                    + "먼저 저장한 사용자도 롤백한다"
    )
    void saveUser_rollsBackUserWhenGamificationSaveFails() {
        // given
        String emailLookupHash =
                "b".repeat(64);

        User user =
                createUser(
                        emailLookupHash,
                        "RollbackUser"
                );

        doThrow(
                new IllegalStateException(
                        "게이미피케이션 저장 실패"
                )
        ).when(
                gamificationStateRepository
        ).save(
                any(
                        UserGamificationState.class
                )
        );

        // when & then
        assertThatThrownBy(
                () ->
                        signUpPersistenceService
                                .saveUser(
                                        user
                                )
        )
                .isInstanceOf(
                        IllegalStateException.class
                )
                .hasMessage(
                        "게이미피케이션 저장 실패"
                );

        /*
         * UserRepository.save()에서는 saveAndFlush()가 실행됐지만
         * 동일 트랜잭션의 후속 작업에서 예외가 발생했으므로
         * User INSERT도 최종적으로 롤백되어야 합니다.
         */
        assertThat(
                userRepository
                        .findByEmailLookupHash(
                                emailLookupHash
                        )
        ).isEmpty();
    }

    @Test
    @DisplayName(
            "이미 사용 중인 이메일이면 "
                    + "중복 이메일 예외를 반환한다"
    )
    void saveUser_rejectsDuplicateEmail() {
        // given
        String emailLookupHash =
                "c".repeat(64);

        User firstUser =
                createUser(
                        emailLookupHash,
                        "FirstUser"
                );

        signUpPersistenceService.saveUser(
                firstUser
        );

        User duplicatedUser =
                createUser(
                        emailLookupHash,
                        "SecondUser"
                );

        // when & then
        assertThatThrownBy(
                () ->
                        signUpPersistenceService
                                .saveUser(
                                        duplicatedUser
                                )
        )
                .isInstanceOf(
                        BusinessException.class
                )
                .extracting(
                        exception ->
                                ((BusinessException) exception)
                                        .getErrorCode()
                )
                .isEqualTo(
                        ErrorCode.USER_DUPLICATE_EMAIL
                );
    }

    @Test
    @DisplayName(
            "이미 사용 중인 닉네임이면 "
                    + "중복 닉네임 예외를 반환한다"
    )
    void saveUser_rejectsDuplicateNickname() {
        // given
        String firstEmailLookupHash =
                "d".repeat(64);

        User firstUser =
                createUser(
                        firstEmailLookupHash,
                        "DuplicateNickname"
                );

        signUpPersistenceService.saveUser(
                firstUser
        );

        String secondEmailLookupHash =
                "e".repeat(64);

        User duplicatedUser =
                createUser(
                        secondEmailLookupHash,
                        "duplicatenickname"
                );

        // when & then
        assertThatThrownBy(
                () ->
                        signUpPersistenceService
                                .saveUser(
                                        duplicatedUser
                                )
        )
                .isInstanceOf(
                        BusinessException.class
                )
                .extracting(
                        exception ->
                                ((BusinessException) exception)
                                        .getErrorCode()
                )
                .isEqualTo(
                        ErrorCode.USER_DUPLICATE_NICKNAME
                );
    }

    /**
     * 테스트에서 사용할 신규 사용자를 생성합니다.
     */
    private User createUser(
            String emailLookupHash,
            String nickname
    ) {
        return User.create(
                "encrypted-email",
                emailLookupHash,
                "argon2-password-hash",
                nickname,
                3,
                LearningLevel.BEGINNER
        );
    }
}
