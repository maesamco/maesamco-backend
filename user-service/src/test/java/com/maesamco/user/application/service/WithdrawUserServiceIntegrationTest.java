package com.maesamco.user.application.service;

import com.maesamco.user.application.port.AuthSessionLogoutAllStore;
import com.maesamco.user.application.port.EmailCipher;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
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
        WithdrawUserService.class,
        UpdateMyProfileService.class
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
    private UpdateMyProfileService updateMyProfileService;

    @MockitoSpyBean
    private UserRepository userRepository;

    @MockitoSpyBean
    private UserInterestConceptRepository
            interestConceptRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private PasswordHasher passwordHasher;

    @MockitoBean
    private EmailCipher emailCipher;

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
        stubInvalidatedAt();

        doThrow(
                new DataAccessResourceFailureException(
                        "관심 개념 저장 실패"
                )
        ).when(
                interestConceptRepository
        ).softDeleteAllByUserId(
                user.getId(),
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
                authSessionLogoutAllStore
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

    @Test
    @DisplayName(
            "프로필 수정이 탈퇴 전 User를 읽었더라도 "
                    + "탈퇴 커밋 이후에는 낙관적 락 충돌로 계정을 되살리지 못한다"
    )
    void concurrentProfileUpdateCannotRestoreWithdrawnUser()
            throws Exception {
        // given
        User user = saveUser(
                "e".repeat(64),
                "동시수정사용자"
        );

        stubValidPassword();
        stubInvalidatedAt();

        CountDownLatch profileLoaded =
                new CountDownLatch(1);

        CountDownLatch allowProfileSave =
                new CountDownLatch(1);

        AtomicInteger findByIdCount =
                new AtomicInteger();

        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Optional<User> result =
                    (Optional<User>) invocation.callRealMethod();

            if (findByIdCount.incrementAndGet() == 1) {
                profileLoaded.countDown();

                if (!allowProfileSave.await(
                        5,
                        TimeUnit.SECONDS
                )) {
                    throw new IllegalStateException(
                            "프로필 수정 재개 대기 시간 초과"
                    );
                }
            }

            return result;
        }).when(userRepository)
                .findById(user.getId());

        UpdateMyProfileCommand profileCommand =
                new UpdateMyProfileCommand(
                        "탈퇴후수정시도",
                        LearningLevel.BASIC,
                        12
                );

        ExecutorService executorService =
                Executors.newSingleThreadExecutor();

        try {
            Future<UpdateMyProfileResult> profileFuture =
                    executorService.submit(
                            () -> updateMyProfileService
                                    .updateMyProfile(
                                            user.getId(),
                                            profileCommand
                                    )
                    );

            assertThat(
                    profileLoaded.await(
                            5,
                            TimeUnit.SECONDS
                    )
            ).isTrue();

            // 프로필 트랜잭션이 탈퇴 전 version을 읽은 상태에서
            // 탈퇴를 먼저 완료시킵니다.
            withdrawUserService.withdraw(
                    user.getId(),
                    new WithdrawUserCommand(
                            CURRENT_PASSWORD
                    )
            );

            allowProfileSave.countDown();

            assertThatThrownBy(
                    () -> profileFuture.get(
                            10,
                            TimeUnit.SECONDS
                    )
            )
                    .isInstanceOfSatisfying(
                            ExecutionException.class,
                            exception ->
                                    assertThat(
                                            exception.getCause()
                                    ).isInstanceOfSatisfying(
                                            BusinessException.class,
                                            cause ->
                                                    assertThat(
                                                            cause.getErrorCode()
                                                    ).isEqualTo(
                                                            ErrorCode
                                                                    .USER_PROFILE_UPDATE_CONFLICT
                                                    )
                                    )
                    );
        } finally {
            allowProfileSave.countDown();
            executorService.shutdownNow();
        }

        // 탈퇴 행이 다시 활성화되지 않아야 합니다.
        assertThat(
                countSoftDeletedUsers(
                        user.getId()
                )
        ).isEqualTo(1);

        assertThat(
                userRepository.findById(
                        user.getId()
                )
        ).isEmpty();
    }

    @Test
    @DisplayName(
            "동일 사용자의 두 탈퇴 요청이 겹치면 "
                    + "비관적 쓰기 잠금으로 직렬화한다"
    )
    void concurrentWithdrawalsAreSerialized()
            throws Exception {
        // given
        User user = saveUser(
                "f".repeat(64),
                "동시탈퇴사용자"
        );

        stubInvalidatedAt();

        CountDownLatch firstPasswordCheck =
                new CountDownLatch(1);

        CountDownLatch allowFirstWithdrawal =
                new CountDownLatch(1);

        CountDownLatch secondLockAttempt =
                new CountDownLatch(1);

        AtomicInteger passwordCheckCount =
                new AtomicInteger();

        AtomicInteger lockAttemptCount =
                new AtomicInteger();

        doAnswer(invocation -> {
            if (lockAttemptCount.incrementAndGet() == 2) {
                secondLockAttempt.countDown();
            }

            return invocation.callRealMethod();
        }).when(userRepository)
                .findByIdForUpdate(
                        user.getId()
                );

        doAnswer(invocation -> {
            if (passwordCheckCount.incrementAndGet() == 1) {
                firstPasswordCheck.countDown();

                if (!allowFirstWithdrawal.await(
                        5,
                        TimeUnit.SECONDS
                )) {
                    throw new IllegalStateException(
                            "첫 번째 탈퇴 재개 대기 시간 초과"
                    );
                }
            }

            return true;
        }).when(passwordHasher)
                .matches(
                        CURRENT_PASSWORD,
                        CURRENT_PASSWORD_HASH
                );

        ExecutorService executorService =
                Executors.newFixedThreadPool(2);

        try {
            Future<Void> firstFuture =
                    executorService.submit(() -> {
                        withdrawUserService.withdraw(
                                user.getId(),
                                new WithdrawUserCommand(
                                        CURRENT_PASSWORD
                                )
                        );

                        return null;
                    });

            assertThat(
                    firstPasswordCheck.await(
                            5,
                            TimeUnit.SECONDS
                    )
            ).isTrue();

            Future<Void> secondFuture =
                    executorService.submit(() -> {
                        withdrawUserService.withdraw(
                                user.getId(),
                                new WithdrawUserCommand(
                                        CURRENT_PASSWORD
                                )
                        );

                        return null;
                    });

            assertThat(
                    secondLockAttempt.await(
                            5,
                            TimeUnit.SECONDS
                    )
            ).isTrue();

            // 첫 번째 트랜잭션이 행 잠금을 보유한 동안
            // 두 번째 요청은 완료될 수 없습니다.
            assertThat(secondFuture.isDone())
                    .isFalse();

            allowFirstWithdrawal.countDown();

            firstFuture.get(
                    10,
                    TimeUnit.SECONDS
            );

            assertThatThrownBy(
                    () -> secondFuture.get(
                            10,
                            TimeUnit.SECONDS
                    )
            )
                    .isInstanceOfSatisfying(
                            ExecutionException.class,
                            exception ->
                                    assertThat(
                                            exception.getCause()
                                    ).isInstanceOfSatisfying(
                                            BusinessException.class,
                                            cause ->
                                                    assertThat(
                                                            cause.getErrorCode()
                                                    ).isEqualTo(
                                                            ErrorCode.USER_NOT_FOUND
                                                    )
                                    )
                    );
        } finally {
            allowFirstWithdrawal.countDown();
            executorService.shutdownNow();
        }

        assertThat(
                countSoftDeletedUsers(
                        user.getId()
                )
        ).isEqualTo(1);
    }

    @Test
    @DisplayName(
            "탈퇴가 User 행 잠금을 보유하는 동안 로그인 조회는 대기하고 "
                    + "탈퇴 커밋 이후 삭제된 사용자를 조회하지 못한다"
    )
    void loginLookupIsSerializedWithWithdrawal()
            throws Exception {
        // given
        String emailLookupHash =
                "g".repeat(64);

        User user = saveUser(
                emailLookupHash,
                "로그인경쟁사용자"
        );

        stubInvalidatedAt();

        CountDownLatch withdrawalHasLock =
                new CountDownLatch(1);

        CountDownLatch allowWithdrawal =
                new CountDownLatch(1);

        CountDownLatch loginLookupAttempt =
                new CountDownLatch(1);

        doAnswer(invocation -> {
            withdrawalHasLock.countDown();

            if (!allowWithdrawal.await(
                    5,
                    TimeUnit.SECONDS
            )) {
                throw new IllegalStateException(
                        "탈퇴 재개 대기 시간 초과"
                );
            }

            return true;
        }).when(passwordHasher)
                .matches(
                        CURRENT_PASSWORD,
                        CURRENT_PASSWORD_HASH
                );

        doAnswer(invocation -> {
            loginLookupAttempt.countDown();
            return invocation.callRealMethod();
        }).when(userRepository)
                .findByEmailLookupHashForUpdate(
                        emailLookupHash
                );

        ExecutorService executorService =
                Executors.newFixedThreadPool(2);

        try {
            Future<Void> withdrawalFuture =
                    executorService.submit(() -> {
                        withdrawUserService.withdraw(
                                user.getId(),
                                new WithdrawUserCommand(
                                        CURRENT_PASSWORD
                                )
                        );

                        return null;
                    });

            assertThat(
                    withdrawalHasLock.await(
                            5,
                            TimeUnit.SECONDS
                    )
            ).isTrue();

            Future<Optional<User>> loginLookupFuture =
                    executorService.submit(() -> {
                        TransactionTemplate transactionTemplate =
                                new TransactionTemplate(
                                        transactionManager
                                );

                        return transactionTemplate.execute(
                                status ->
                                        userRepository
                                                .findByEmailLookupHashForUpdate(
                                                        emailLookupHash
                                                )
                        );
                    });

            assertThat(
                    loginLookupAttempt.await(
                            5,
                            TimeUnit.SECONDS
                    )
            ).isTrue();

            // 탈퇴가 잠금을 잡은 상태이므로 로그인 조회는 끝날 수 없습니다.
            assertThat(loginLookupFuture.isDone())
                    .isFalse();

            allowWithdrawal.countDown();

            withdrawalFuture.get(
                    10,
                    TimeUnit.SECONDS
            );

            Optional<User> loginResult =
                    loginLookupFuture.get(
                            10,
                            TimeUnit.SECONDS
                    );

            // 탈퇴 커밋 후 @SQLRestriction에 의해 조회되지 않아야 합니다.
            assertThat(loginResult)
                    .isEmpty();
        } finally {
            allowWithdrawal.countDown();
            executorService.shutdownNow();
        }

        assertThat(
                countSoftDeletedUsers(
                        user.getId()
                )
        ).isEqualTo(1);
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
