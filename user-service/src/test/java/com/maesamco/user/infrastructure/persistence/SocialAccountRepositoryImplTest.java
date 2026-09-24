package com.maesamco.user.infrastructure.persistence;

import com.maesamco.user.domain.entity.LearningLevel;
import com.maesamco.user.domain.entity.SocialAccount;
import com.maesamco.user.domain.entity.SocialProvider;
import com.maesamco.user.domain.entity.User;
import com.maesamco.user.domain.repository.SocialAccountRepository;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SocialAccountRepository 구현체를 실제 PostgreSQL에서 검증합니다.
 *
 * <p>Provider 계정 식별, 사용자별 Provider 중복 방지,
 * 논리 삭제 후 재연결 정책까지 실제 DB 제약과 함께 검증합니다.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@Import(JpaAuditingConfig.class)
@Testcontainers
class SocialAccountRepositoryImplTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                    DockerImageName.parse(
                            "postgres:16-alpine"
                    )
            );

    @Autowired
    private SpringDataSocialAccountRepository
            springDataSocialAccountRepository;

    @Autowired
    private EntityManager entityManager;

    private SocialAccountRepository socialAccountRepository;

    @BeforeEach
    void setUp() {
        socialAccountRepository =
                new SocialAccountRepositoryImpl(
                        springDataSocialAccountRepository
                );
    }

    @Test
    @DisplayName(
            "Provider와 Provider 사용자 ID로 소셜 계정을 저장하고 조회한다"
    )
    void saveAndFindByProviderAndProviderUserId() {
        // given
        UUID userId =
                persistUser(
                        "a".repeat(64),
                        "SocialUserOne"
                );

        SocialAccount socialAccount =
                SocialAccount.create(
                        userId,
                        SocialProvider.GOOGLE,
                        "google-sub-001"
                );

        // when
        SocialAccount saved =
                socialAccountRepository.save(
                        socialAccount
                );

        entityManager.clear();

        Optional<SocialAccount> found =
                socialAccountRepository
                        .findByProviderAndProviderUserId(
                                SocialProvider.GOOGLE,
                                "google-sub-001"
                        );

        // then
        assertThat(found).isPresent();

        assertThat(found.get().getId())
                .isEqualTo(saved.getId());

        assertThat(found.get().getUserId())
                .isEqualTo(userId);

        assertThat(found.get().getProvider())
                .isEqualTo(SocialProvider.GOOGLE);

        assertThat(found.get().getProviderUserId())
                .isEqualTo("google-sub-001");

        assertThat(found.get().getCreatedAt())
                .isNotNull();
    }

    @Test
    @DisplayName(
            "사용자가 특정 Provider 계정을 가지고 있는지 확인한다"
    )
    void existsByUserIdAndProvider() {
        // given
        UUID userId =
                persistUser(
                        "b".repeat(64),
                        "SocialUserTwo"
                );

        socialAccountRepository.save(
                SocialAccount.create(
                        userId,
                        SocialProvider.GOOGLE,
                        "google-sub-002"
                )
        );

        entityManager.clear();

        // when
        boolean googleExists =
                socialAccountRepository
                        .existsByUserIdAndProvider(
                                userId,
                                SocialProvider.GOOGLE
                        );

        boolean kakaoExists =
                socialAccountRepository
                        .existsByUserIdAndProvider(
                                userId,
                                SocialProvider.KAKAO
                        );

        // then
        assertThat(googleExists).isTrue();
        assertThat(kakaoExists).isFalse();
    }

    @Test
    @DisplayName(
            "동일한 Provider 사용자 계정을 여러 사용자에게 연결할 수 없다"
    )
    void save_rejectsDuplicatedProviderUserId() {
        // given
        UUID firstUserId =
                persistUser(
                        "c".repeat(64),
                        "SocialUserThree"
                );

        UUID secondUserId =
                persistUser(
                        "d".repeat(64),
                        "SocialUserFour"
                );

        socialAccountRepository.save(
                SocialAccount.create(
                        firstUserId,
                        SocialProvider.GOOGLE,
                        "google-duplicate-sub"
                )
        );

        // when & then
        assertThatThrownBy(
                () -> socialAccountRepository.save(
                        SocialAccount.create(
                                secondUserId,
                                SocialProvider.GOOGLE,
                                "google-duplicate-sub"
                        )
                )
        )
                // 동시 가입 경쟁의 최종 방어선 — DB UNIQUE 위반을 도메인 오류로 변환한다(#308).
                .isInstanceOf(
                        BusinessException.class
                )
                .extracting(
                        exception ->
                                ((BusinessException) exception)
                                        .getErrorCode()
                )
                .isEqualTo(
                        ErrorCode.SOCIAL_ACCOUNT_ALREADY_LINKED
                );
    }

    @Test
    @DisplayName(
            "한 사용자는 동일 Provider 계정을 두 개 연결할 수 없다"
    )
    void save_rejectsDuplicatedUserProvider() {
        // given
        UUID userId =
                persistUser(
                        "e".repeat(64),
                        "SocialUserFive"
                );

        socialAccountRepository.save(
                SocialAccount.create(
                        userId,
                        SocialProvider.GOOGLE,
                        "google-sub-first"
                )
        );

        // when & then
        assertThatThrownBy(
                () -> socialAccountRepository.save(
                        SocialAccount.create(
                                userId,
                                SocialProvider.GOOGLE,
                                "google-sub-second"
                        )
                )
        )
                .isInstanceOf(
                        DataIntegrityViolationException.class
                );
    }

    @Test
    @DisplayName(
            "논리 삭제된 소셜 계정은 일반 조회에서 제외된다"
    )
    void softDeletedAccountIsExcluded() {
        // given
        UUID userId =
                persistUser(
                        "f".repeat(64),
                        "SocialUserSix"
                );

        SocialAccount socialAccount =
                socialAccountRepository.save(
                        SocialAccount.create(
                                userId,
                                SocialProvider.GOOGLE,
                                "google-sub-deleted"
                        )
                );

        // when
        socialAccount.softDelete(
                UUID.randomUUID()
        );

        springDataSocialAccountRepository.flush();
        entityManager.clear();

        // then
        assertThat(
                socialAccountRepository
                        .findByProviderAndProviderUserId(
                                SocialProvider.GOOGLE,
                                "google-sub-deleted"
                        )
        ).isEmpty();

        assertThat(
                socialAccountRepository
                        .existsByUserIdAndProvider(
                                userId,
                                SocialProvider.GOOGLE
                        )
        ).isFalse();
    }

    @Test
    @DisplayName(
            "논리 삭제 후 동일한 Provider 계정을 다시 연결할 수 있다"
    )
    void recreateAfterSoftDelete() {
        // given
        UUID userId =
                persistUser(
                        "g".repeat(64),
                        "SocialUserSeven"
                );

        SocialAccount deletedAccount =
                socialAccountRepository.save(
                        SocialAccount.create(
                                userId,
                                SocialProvider.GOOGLE,
                                "google-sub-reconnect"
                        )
                );

        deletedAccount.softDelete(
                UUID.randomUUID()
        );

        springDataSocialAccountRepository.flush();
        entityManager.clear();

        // when
        SocialAccount recreated =
                socialAccountRepository.save(
                        SocialAccount.create(
                                userId,
                                SocialProvider.GOOGLE,
                                "google-sub-reconnect"
                        )
                );

        entityManager.clear();

        // then
        SocialAccount found =
                socialAccountRepository
                        .findByProviderAndProviderUserId(
                                SocialProvider.GOOGLE,
                                "google-sub-reconnect"
                        )
                        .orElseThrow();

        assertThat(found.getId())
                .isEqualTo(recreated.getId());

        assertThat(found.getId())
                .isNotEqualTo(
                        deletedAccount.getId()
                );
    }

    /**
     * SocialAccount의 실제 FK 제약을 만족하도록
     * 테스트 User를 PostgreSQL에 먼저 저장합니다.
     */
    private UUID persistUser(
            String emailLookupHash,
            String nickname
    ) {
        User user =
                User.create(
                        "encrypted-email",
                        emailLookupHash,
                        "argon2-password-hash",
                        nickname,
                        3,
                        LearningLevel.BEGINNER
                );

        entityManager.persist(user);
        entityManager.flush();

        return user.getId();
    }
}
