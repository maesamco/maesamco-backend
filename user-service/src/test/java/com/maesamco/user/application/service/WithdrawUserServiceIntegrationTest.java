package com.maesamco.user.application.service;

import com.maesamco.user.application.port.AuthSessionLogoutAllStore;
import com.maesamco.user.application.port.PasswordHasher;
import com.maesamco.user.domain.entity.LearningLevel;
import com.maesamco.user.domain.entity.User;
import com.maesamco.user.domain.entity.UserInterestConcept;
import com.maesamco.user.domain.repository.UserInterestConceptRepository;
import com.maesamco.user.domain.repository.UserRepository;
import com.maesamco.user.global.config.JpaAuditingConfig;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import com.maesamco.user.infrastructure.persistence.UserInterestConceptRepositoryImpl;
import com.maesamco.user.infrastructure.persistence.UserRepositoryImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 회원 탈퇴 서비스의 실제 PostgreSQL 연동과
 * 트랜잭션 롤백을 검증합니다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@Import({
        JpaAuditingConfig.class,
        UserRepositoryImpl.class,
        UserInterestConceptRepositoryImpl.class,
        WithdrawUserService.class
})
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class WithdrawUserServiceIntegrationTest {

    private static final UUID FIRST_CONCEPT_ID =
            UUID.fromString(
                    "11111111-1111-1111-1111-111111111111"
            );

    private static final UUID SECOND_CONCEPT_ID =
            UUID.fromString(
                    "22222222-2222-2222-2222-222222222222"
            );

    private static final String CURRENT_PASSWORD =
            "Abcd1234!";

    private static final String CURRENT_PASSWORD_HASH =
            "current-password-hash";

    private static final Instant INVALIDATED_AT =
            Instant.parse(
                    "2026-09-15T08:00:00Z"
            );

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                    DockerImageName.parse(
                            "postgres:16-alpine"
                    )
            );

    @Autowired
    private WithdrawUserService withdrawUserService;

    @Autowired
    private UserRepository userRepository;

    @MockitoSpyBean
    private UserInterestConceptRepository
            interestConceptRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private PasswordHasher passwordHasher;

    @MockitoBean
    private AuthSessionLogoutAllStore
            authSessionLogoutAllStore;

    @MockitoBean
    private Clock clock;

    @Test
    @DisplayName(
            "회원 탈퇴 성공 시 사용자와 관심 개념을 논리 삭제하고 "
                    + "모든 인증 세션을 무효화한다"
    )
    void withdraw_softDeletesUserAndInterests() {
        // given
        User user = saveUser(
                "a".repeat(64),
                "탈퇴성공사용자"
        );

        saveInterests(
                user.getId()
        );

        stubValidPassword();
        stubInvalidatedAt();

        // when
        withdrawUserService.withdraw(
                user.getId(),
                new WithdrawUserCommand(
                        CURRENT_PASSWORD
                )
        );

        // then
        assertThat(
                userRepository.findById(
                        user.getId()
                )
        ).isEmpty();

        assertThat(
                interestConceptRepository.findAllByUserId(
                        user.getId()
                )
        ).isEmpty();

        assertThat(
                countSoftDeletedUsers(
                        user.getId()
                )
        ).isEqualTo(1);

        assertThat(
                countSoftDeletedInterests(
                        user.getId()
                )
        ).isEqualTo(2);

        verify(authSessionLogoutAllStore)
                .logoutAll(
                        user.getId(),
                        INVALIDATED_AT
                );
    }

    @Test
    @DisplayName(
            "현재 비밀번호가 일치하지 않으면 "
                    + "사용자와 관심 개념을 변경하지 않는다"
    )
    void passwordMismatch_preservesUserAndInterests() {
        // given
        User user = saveUser(
                "b".repeat(64),
                "비밀번호불일치사용자"
        );

        saveInterests(
                user.getId()
        );

        String wrongPassword =
                "WrongPassword1!";

        when(passwordHasher.matches(
                wrongPassword,
                CURRENT_PASSWORD_HASH
        )).thenReturn(false);

        // when & then
        assertThatThrownBy(
                () -> withdrawUserService.withdraw(
                        user.getId(),
                        new WithdrawUserCommand(
                                wrongPassword
                        )
                )
        )
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(
                                        exception.getErrorCode()
                                ).isEqualTo(
                                        ErrorCode
                                                .USER_CURRENT_PASSWORD_MISMATCH
                                )
                );

        assertThat(
                userRepository.findById(
                        user.getId()
                )
        ).isPresent();

        assertThat(
                interestConceptRepository.findAllByUserId(
                        user.getId()
                )
        ).hasSize(2);

        assertThat(
                countSoftDeletedUsers(
                        user.getId()
                )
        ).isZero();

        assertThat(
                countSoftDeletedInterests(
                        user.getId()
                )
        ).isZero();

        verifyNoInteractions(
                authSessionLogoutAllStore,
                clock
        );
    }

    @Test
    @DisplayName(
            "관심 개념 저장에 실패하면 사용자와 관심 개념 변경을 "
                    + "모두 롤백하고 인증 세션을 유지한다"
    )
    void databaseFailure_rollsBackAndPreservesSessions() {
        // given
        User user = saveUser(
                "c".repeat(64),
                "DB실패사용자"
        );

        saveInterests(
                user.getId()
        );

        stubValidPassword();

        doThrow(
                new DataAccessResourceFailureException(
                        "관심 개념 저장 실패"
                )
        ).when(
                interestConceptRepository
        ).saveAllAndFlush(
                anyList()
        );

        // when & then
        assertThatThrownBy(
                () -> withdrawUserService.withdraw(
                        user.getId(),
                        new WithdrawUserCommand(
                                CURRENT_PASSWORD
                        )
                )
        )
                .isInstanceOf(
                        DataAccessResourceFailureException.class
                )
                .hasMessage(
                        "관심 개념 저장 실패"
                );

        assertThat(
                userRepository.findById(
                        user.getId()
                )
        ).isPresent();

        assertThat(
                interestConceptRepository.findAllByUserId(
                        user.getId()
                )
        ).hasSize(2);

        assertThat(
                countSoftDeletedUsers(
                        user.getId()
                )
        ).isZero();

        assertThat(
                countSoftDeletedInterests(
                        user.getId()
                )
        ).isZero();

        verifyNoInteractions(
                authSessionLogoutAllStore,
                clock
        );
    }

    @Test
    @DisplayName(
            "인증 세션 무효화에 실패하면 "
                    + "사용자와 관심 개념 논리 삭제를 모두 롤백한다"
    )
    void sessionInvalidationFailure_rollsBackDatabaseChanges() {
        // given
        User user = saveUser(
                "d".repeat(64),
                "세션실패사용자"
        );

        saveInterests(
                user.getId()
        );

        stubValidPassword();
        stubInvalidatedAt();

        doThrow(
                new IllegalStateException(
                        "인증 세션 무효화 실패"
                )
        ).when(
                authSessionLogoutAllStore
        ).logoutAll(
                user.getId(),
                INVALIDATED_AT
        );

        // when & then
        assertThatThrownBy(
                () -> withdrawUserService.withdraw(
                        user.getId(),
                        new WithdrawUserCommand(
                                CURRENT_PASSWORD
                        )
                )
        )
                .isInstanceOf(
                        IllegalStateException.class
                )
                .hasMessage(
                        "인증 세션 무효화 실패"
                );

        assertThat(
                userRepository.findById(
                        user.getId()
                )
        ).isPresent();

        assertThat(
                interestConceptRepository.findAllByUserId(
                        user.getId()
                )
        ).hasSize(2);

        assertThat(
                countSoftDeletedUsers(
                        user.getId()
                )
        ).isZero();

        assertThat(
                countSoftDeletedInterests(
                        user.getId()
                )
        ).isZero();
    }

    private User saveUser(
            String emailLookupHash,
            String nickname
    ) {
        return userRepository.save(
                User.create(
                        "encrypted-email",
                        emailLookupHash,
                        CURRENT_PASSWORD_HASH,
                        nickname,
                        3,
                        LearningLevel.BEGINNER
                )
        );
    }

    private void saveInterests(
            UUID userId
    ) {
        interestConceptRepository.saveAllAndFlush(
                List.of(
                        UserInterestConcept.create(
                                userId,
                                FIRST_CONCEPT_ID
                        ),
                        UserInterestConcept.create(
                                userId,
                                SECOND_CONCEPT_ID
                        )
                )
        );
    }

    private void stubValidPassword() {
        when(passwordHasher.matches(
                CURRENT_PASSWORD,
                CURRENT_PASSWORD_HASH
        )).thenReturn(true);
    }

    private void stubInvalidatedAt() {
        when(clock.instant())
                .thenReturn(INVALIDATED_AT);
    }

    /**
     * Hibernate의 @SQLRestriction을 우회하여
     * 실제 사용자 테이블의 논리 삭제 컬럼을 확인합니다.
     */
    private long countSoftDeletedUsers(
            UUID userId
    ) {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM user_schema.p_users
                WHERE id = ?
                  AND deleted_at IS NOT NULL
                  AND deleted_by = ?
                """,
                Long.class,
                userId,
                userId
        );

        return count == null
                ? 0
                : count;
    }

    /**
     * Hibernate의 @SQLRestriction을 우회하여
     * 실제 관심 개념 테이블의 논리 삭제 컬럼을 확인합니다.
     */
    private long countSoftDeletedInterests(
            UUID userId
    ) {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM user_schema.p_user_interest_concepts
                WHERE user_id = ?
                  AND deleted_at IS NOT NULL
                  AND deleted_by = ?
                """,
                Long.class,
                userId,
                userId
        );

        return count == null
                ? 0
                : count;
    }
}
