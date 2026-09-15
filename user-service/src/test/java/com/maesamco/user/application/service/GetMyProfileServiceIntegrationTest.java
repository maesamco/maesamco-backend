package com.maesamco.user.application.service;

import com.maesamco.user.application.port.EmailCipher;
import com.maesamco.user.domain.entity.LearningLevel;
import com.maesamco.user.domain.entity.User;
import com.maesamco.user.domain.entity.UserRole;
import com.maesamco.user.domain.entity.UserStatus;
import com.maesamco.user.domain.repository.UserRepository;
import com.maesamco.user.global.config.JpaAuditingConfig;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import com.maesamco.user.infrastructure.persistence.UserRepositoryImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.within;

/**
 * 로그인 사용자 정보 조회 서비스의 실제 PostgreSQL 연동을 검증합니다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@Import({
        JpaAuditingConfig.class,
        UserRepositoryImpl.class,
        GetMyProfileService.class
})
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GetMyProfileServiceIntegrationTest {

    private static final String ENCRYPTED_EMAIL =
            "encrypted-email";

    private static final String EMAIL =
            "learner@example.com";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                    DockerImageName.parse(
                            "postgres:16-alpine"
                    )
            );

    @Autowired
    private GetMyProfileService getMyProfileService;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private EmailCipher emailCipher;

    @Test
    @DisplayName(
            "PostgreSQL에 저장된 사용자의 기본 정보를 조회하고 "
                    + "이메일을 복호화하여 반환한다"
    )
    void getMyProfile_readsPersistedUser() {
        // given
        User savedUser = userRepository.save(
                User.create(
                        ENCRYPTED_EMAIL,
                        "a".repeat(64),
                        "password-hash",
                        "김티암",
                        3,
                        LearningLevel.BEGINNER
                )
        );

        when(emailCipher.decrypt(ENCRYPTED_EMAIL))
                .thenReturn(EMAIL);

        // when
        GetMyProfileResult result =
                getMyProfileService.getMyProfile(
                        savedUser.getId()
                );

        // then
        assertThat(result.userId())
                .isEqualTo(savedUser.getId());

        assertThat(result.email())
                .isEqualTo(EMAIL);

        assertThat(result.nickname())
                .isEqualTo("김티암");

        assertThat(result.role())
                .isEqualTo(UserRole.USER);

        assertThat(result.status())
                .isEqualTo(UserStatus.ACTIVE);

        assertThat(result.learningLevel())
                .isEqualTo(LearningLevel.BEGINNER);

        assertThat(result.javaExperienceMonths())
                .isEqualTo(3);

        assertThat(result.createdAt())
                .isNotNull()
                .isCloseTo(
                        savedUser.getCreatedAt(),
                        within(
                                1,
                                ChronoUnit.MICROS
                        )
                );

        verify(emailCipher)
                .decrypt(ENCRYPTED_EMAIL);
    }

    @Test
    @DisplayName(
            "PostgreSQL에 사용자가 존재하지 않으면 "
                    + "USER_NOT_FOUND를 반환한다"
    )
    void getMyProfile_throwsWhenUserDoesNotExist() {
        // given
        UUID unknownUserId =
                UUID.fromString(
                        "99999999-9999-9999-9999-999999999999"
                );

        // when & then
        assertThatThrownBy(
                () -> getMyProfileService.getMyProfile(
                        unknownUserId
                )
        )
                .isInstanceOf(BusinessException.class)
                .extracting(
                        exception ->
                                ((BusinessException) exception)
                                        .getErrorCode()
                )
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verifyNoInteractions(emailCipher);
    }
}
