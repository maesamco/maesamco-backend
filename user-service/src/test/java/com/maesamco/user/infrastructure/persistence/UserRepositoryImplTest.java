package com.maesamco.user.infrastructure.persistence;

import com.maesamco.user.domain.entity.LearningLevel;
import com.maesamco.user.domain.entity.User;
import com.maesamco.user.domain.repository.UserRepository;
import com.maesamco.user.global.config.JpaAuditingConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import org.springframework.test.context.jdbc.Sql;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UserRepository 구현체의 PostgreSQL 통합 테스트입니다.
 *
 * <p>H2가 아닌 실제 PostgreSQL Testcontainers를 사용하여
 * 엔티티 매핑, 저장, 조회 및 소프트 삭제 조건을 검증합니다.</p>
 *
 * <p>Flyway를 사용하지 않으며, 테스트 실행 중에만
 * user_schema와 p_users 테이블을 임시로 생성합니다.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@Import(JpaAuditingConfig.class)
@Sql(
        scripts = "/db/migration/V2__add_active_user_unique_indexes.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS
)
@Testcontainers
class UserRepositoryImplTest {

    /**
     * 테스트에서 사용할 임시 PostgreSQL 컨테이너입니다.
     */
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                    DockerImageName.parse("postgres:16-alpine")
            );

    @Autowired
    private SpringDataUserRepository springDataUserRepository;

    @Autowired
    private EntityManager entityManager;

    private UserRepository userRepository;

    /**
     * 도메인 Repository가 실제 JPA Repository를 사용하도록 구성합니다.
     */
    @BeforeEach
    void setUp() {
        userRepository =
                new UserRepositoryImpl(springDataUserRepository);
    }

    @Test
    @DisplayName("사용자를 저장한 뒤 ID로 조회할 수 있다")
    void saveAndFindById_returnsUser() {
        // given
        User user = createUser(
                "a".repeat(64),
                "테스트사용자"
        );

        // when
        User savedUser = userRepository.save(user);

        /*
         * INSERT와 JPA Auditing이 즉시 실행되도록 flush하고,
         * 1차 캐시가 아닌 실제 DB에서 다시 조회하도록 clear합니다.
         */
        springDataUserRepository.flush();
        entityManager.clear();

        User foundUser = userRepository.findById(savedUser.getId())
                .orElseThrow();

        // then
        assertThat(foundUser.getId()).isEqualTo(savedUser.getId());
        assertThat(foundUser.getNickname()).isEqualTo("테스트사용자");
        assertThat(foundUser.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("이메일 조회용 해시로 사용자를 조회할 수 있다")
    void findByEmailLookupHash_returnsUser() {
        // given
        String emailLookupHash = "b".repeat(64);

        User savedUser = userRepository.save(
                createUser(emailLookupHash, "이메일조회사용자")
        );

        springDataUserRepository.flush();
        entityManager.clear();

        // when
        Optional<User> foundUser =
                userRepository.findByEmailLookupHash(emailLookupHash);

        // then
        assertThat(foundUser).isPresent();
        assertThat(foundUser.get().getId())
                .isEqualTo(savedUser.getId());
    }

    @Test
    @DisplayName("이메일 해시와 대소문자를 무시한 닉네임의 존재 여부를 확인한다")
    void exists_returnsCorrectResult() {
        // given
        String emailLookupHash = "c".repeat(64);
        String nickname = "JavaLearner";

        userRepository.save(
                createUser(emailLookupHash, nickname)
        );

        springDataUserRepository.flush();
        entityManager.clear();

        // when
        boolean existingEmail =
                userRepository.existsByEmailLookupHash(emailLookupHash);

        boolean missingEmail =
                userRepository.existsByEmailLookupHash("d".repeat(64));

        boolean existingNickname =
                userRepository.existsByNicknameIgnoreCase(
                        "javalearner"
                );

        boolean missingNickname =
                userRepository.existsByNicknameIgnoreCase(
                        "존재하지않는닉네임"
                );

        // then
        assertThat(existingEmail).isTrue();
        assertThat(missingEmail).isFalse();
        assertThat(existingNickname).isTrue();
        assertThat(missingNickname).isFalse();
    }

    @Test
    @DisplayName("소프트 삭제된 사용자는 일반 조회 결과에서 제외된다")
    void findById_excludesSoftDeletedUser() {
        // given
        User savedUser = userRepository.save(
                createUser(
                        "e".repeat(64),
                        "탈퇴사용자"
                )
        );

        springDataUserRepository.flush();

        UUID userId = savedUser.getId();

        // when
        savedUser.softDelete(UUID.randomUUID());

        springDataUserRepository.flush();
        entityManager.clear();

        Optional<User> foundUser =
                userRepository.findById(userId);

        // then
        assertThat(foundUser).isEmpty();
    }

    /**
     * 테스트마다 동일한 생성 규칙을 사용하기 위한 사용자 생성 메서드입니다.
     */
    private User createUser(
            String emailLookupHash,
            String nickname
    ) {
        return User.create(
                "encrypted-email-" + nickname,
                emailLookupHash,
                "encoded-password",
                nickname,
                6,
                LearningLevel.BEGINNER
        );
    }

    @Test
    @DisplayName("미삭제 사용자의 이메일 해시가 중복되면 전용 예외가 발생한다")
    void save_throwsWhenActiveEmailLookupHashAlreadyExists() {
        // given
        String emailLookupHash = "f".repeat(64);

        userRepository.save(
                createUser(emailLookupHash, "EmailOwner")
        );

        User duplicateUser = createUser(
                emailLookupHash,
                "AnotherNickname"
        );

        // when & then
        assertThatThrownBy(() -> userRepository.save(duplicateUser))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(
                                exception.getErrorCode()
                        ).isEqualTo(
                                ErrorCode.USER_DUPLICATE_EMAIL
                        )
                );
    }

    @Test
    @DisplayName("미삭제 사용자의 닉네임이 대소문자만 다르면 전용 예외가 발생한다")
    void save_throwsWhenActiveNicknameAlreadyExistsIgnoringCase() {
        // given
        userRepository.save(
                createUser(
                        "g".repeat(64),
                        "JavaLearner"
                )
        );

        User duplicateUser = createUser(
                "h".repeat(64),
                "javalearner"
        );

        // when & then
        assertThatThrownBy(() -> userRepository.save(duplicateUser))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(
                                exception.getErrorCode()
                        ).isEqualTo(
                                ErrorCode.USER_DUPLICATE_NICKNAME
                        )
                );
    }

    @Test
    @DisplayName("탈퇴한 사용자의 이메일과 닉네임은 재가입에 사용할 수 있다")
    void save_allowsEmailAndNicknameOfSoftDeletedUser() {
        // given
        String emailLookupHash = "i".repeat(64);
        User withdrawnUser = userRepository.save(
                createUser(
                        emailLookupHash,
                        "ReusableNickname"
                )
        );

        withdrawnUser.softDelete(UUID.randomUUID());
        userRepository.save(withdrawnUser);
        entityManager.clear();

        // when
        User rejoinedUser = userRepository.save(
                createUser(
                        emailLookupHash,
                        "reusablenickname"
                )
        );

        // then
        assertThat(rejoinedUser.getId())
                .isNotEqualTo(withdrawnUser.getId());
        assertThat(
                userRepository.existsByEmailLookupHash(
                        emailLookupHash
                )
        ).isTrue();
        assertThat(
                userRepository.existsByNicknameIgnoreCase(
                        "REUSABLENICKNAME"
                )
        ).isTrue();
    }
}
