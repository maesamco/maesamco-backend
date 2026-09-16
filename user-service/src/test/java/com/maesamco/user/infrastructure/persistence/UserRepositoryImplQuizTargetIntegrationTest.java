package com.maesamco.user.infrastructure.persistence;

import com.maesamco.user.domain.entity.LearningLevel;
import com.maesamco.user.domain.entity.User;
import com.maesamco.user.domain.entity.UserRole;
import com.maesamco.user.domain.repository.UserRepository;
import com.maesamco.user.global.config.JpaAuditingConfig;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@Import(JpaAuditingConfig.class)
@Testcontainers
class UserRepositoryImplQuizTargetIntegrationTest {

    private static final UUID FIRST_USER_ID =
            UUID.fromString(
                    "00000000-0000-0000-0000-000000000001"
            );

    private static final UUID SECOND_USER_ID =
            UUID.fromString(
                    "00000000-0000-0000-0000-000000000002"
            );

    private static final UUID THIRD_USER_ID =
            UUID.fromString(
                    "00000000-0000-0000-0000-000000000003"
            );

    private static final UUID FOURTH_USER_ID =
            UUID.fromString(
                    "00000000-0000-0000-0000-000000000004"
            );

    private static final UUID FIFTH_USER_ID =
            UUID.fromString(
                    "00000000-0000-0000-0000-000000000005"
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
    private SpringDataUserRepository springDataUserRepository;

    @Autowired
    private EntityManager entityManager;

    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository =
                new UserRepositoryImpl(
                        springDataUserRepository
                );
    }

    @Test
    @DisplayName(
            "첫 페이지는 활성 일반 사용자만 "
                    + "UUID 오름차순으로 조회한다"
    )
    void findQuizTargetUserIds_filtersAndSortsFirstPage() {
        // given
        persistActiveUser(
                THIRD_USER_ID,
                3
        );

        persistActiveUser(
                FIRST_USER_ID,
                1
        );

        persistSuspendedUser(
                SECOND_USER_ID,
                2
        );

        persistNonUser(
                FOURTH_USER_ID,
                4
        );

        persistActiveUser(
                FIFTH_USER_ID,
                5
        );

        entityManager.flush();

        markDeleted(
                FIFTH_USER_ID
        );

        entityManager.clear();

        // when
        List<UUID> result =
                userRepository.findQuizTargetUserIds(
                        null,
                        10
                );

        // then
        assertThat(result)
                .containsExactly(
                        FIRST_USER_ID,
                        THIRD_USER_ID
                );

        assertThatThrownBy(
                () -> result.add(
                        UUID.randomUUID()
                )
        ).isInstanceOf(
                UnsupportedOperationException.class
        );
    }

    @Test
    @DisplayName(
            "커서 이후의 사용자만 조회하고 "
                    + "조회 제한 개수를 적용한다"
    )
    void findQuizTargetUserIds_appliesCursorAndLimit() {
        // given
        persistActiveUser(
                FIFTH_USER_ID,
                5
        );

        persistActiveUser(
                FIRST_USER_ID,
                1
        );

        persistActiveUser(
                FOURTH_USER_ID,
                4
        );

        persistActiveUser(
                SECOND_USER_ID,
                2
        );

        persistActiveUser(
                THIRD_USER_ID,
                3
        );

        entityManager.flush();
        entityManager.clear();

        // when
        List<UUID> result =
                userRepository.findQuizTargetUserIds(
                        SECOND_USER_ID,
                        2
                );

        // then
        assertThat(result)
                .containsExactly(
                        THIRD_USER_ID,
                        FOURTH_USER_ID
                );
    }

    @Test
    @DisplayName(
            "마지막 사용자 ID를 커서로 사용하면 "
                    + "빈 목록을 반환한다"
    )
    void findQuizTargetUserIds_afterLastCursorReturnsEmpty() {
        // given
        persistActiveUser(
                FIRST_USER_ID,
                1
        );

        persistActiveUser(
                SECOND_USER_ID,
                2
        );

        entityManager.flush();
        entityManager.clear();

        // when
        List<UUID> result =
                userRepository.findQuizTargetUserIds(
                        SECOND_USER_ID,
                        10
                );

        // then
        assertThat(result)
                .isEmpty();
    }

    @Test
    @DisplayName(
            "존재하지 않는 중간 커서를 사용해도 "
                    + "커서보다 큰 사용자만 조회한다"
    )
    void findQuizTargetUserIds_supportsNonExistingCursor() {
        // given
        UUID cursor =
                UUID.fromString(
                        "00000000-0000-0000-0000-000000000002"
                );

        persistActiveUser(
                FIRST_USER_ID,
                1
        );

        persistActiveUser(
                THIRD_USER_ID,
                3
        );

        persistActiveUser(
                FOURTH_USER_ID,
                4
        );

        entityManager.flush();
        entityManager.clear();

        // when
        List<UUID> result =
                userRepository.findQuizTargetUserIds(
                        cursor,
                        10
                );

        // then
        assertThat(result)
                .containsExactly(
                        THIRD_USER_ID,
                        FOURTH_USER_ID
                );
    }

    @Test
    @DisplayName(
            "조회 제한이 0이면 "
                    + "INVALID_INPUT_VALUE를 반환한다"
    )
    void findQuizTargetUserIds_rejectsZeroLimit() {
        assertInvalidLimit(
                0
        );
    }

    @Test
    @DisplayName(
            "조회 제한이 음수이면 "
                    + "INVALID_INPUT_VALUE를 반환한다"
    )
    void findQuizTargetUserIds_rejectsNegativeLimit() {
        assertInvalidLimit(
                -1
        );
    }

    private void persistActiveUser(
            UUID userId,
            int sequence
    ) {
        persistUser(
                userId,
                sequence
        );
    }

    private void persistSuspendedUser(
            UUID userId,
            int sequence
    ) {
        User user =
                createUser(
                        userId,
                        sequence
                );

        user.suspend();

        entityManager.persist(
                user
        );
    }

    private void persistNonUser(
            UUID userId,
            int sequence
    ) {
        User user =
                createUser(
                        userId,
                        sequence
                );

        ReflectionTestUtils.setField(
                user,
                "role",
                findNonUserRole()
        );

        entityManager.persist(
                user
        );
    }

    private void persistUser(
            UUID userId,
            int sequence
    ) {
        entityManager.persist(
                createUser(
                        userId,
                        sequence
                )
        );
    }

    private User createUser(
            UUID userId,
            int sequence
    ) {
        User user =
                User.create(
                        "encrypted-email-" + sequence,
                        "%064x".formatted(sequence),
                        "argon2-password-hash",
                        "QuizUser" + sequence,
                        3,
                        LearningLevel.BEGINNER
                );

        ReflectionTestUtils.setField(
                user,
                "id",
                userId
        );

        return user;
    }

    private UserRole findNonUserRole() {
        return Arrays.stream(
                        UserRole.values()
                )
                .filter(
                        role ->
                                role != UserRole.USER
                )
                .findFirst()
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "USER 이외의 역할이 필요합니다."
                                )
                );
    }

    private void markDeleted(
            UUID userId
    ) {
        int updatedRows =
                entityManager
                        .createNativeQuery(
                                """
                                UPDATE user_schema.p_users
                                SET deleted_at = CURRENT_TIMESTAMP
                                WHERE id = :userId
                                """
                        )
                        .setParameter(
                                "userId",
                                userId
                        )
                        .executeUpdate();

        assertThat(updatedRows)
                .isEqualTo(1);

        entityManager.flush();
    }

    private void assertInvalidLimit(
            int limit
    ) {
        assertThatThrownBy(
                () ->
                        userRepository
                                .findQuizTargetUserIds(
                                        null,
                                        limit
                                )
        )
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(
                                        exception.getErrorCode()
                                ).isEqualTo(
                                        ErrorCode.INVALID_INPUT_VALUE
                                )
                );
    }
}
