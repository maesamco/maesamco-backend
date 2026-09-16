package com.maesamco.user.application.service;

import com.maesamco.user.application.port.EmailCipher;
import com.maesamco.user.domain.entity.LearningLevel;
import com.maesamco.user.domain.entity.User;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.UUID;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 로그인 사용자 정보 수정 서비스의 실제 PostgreSQL 연동을 검증합니다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@Import({
        JpaAuditingConfig.class,
        UserRepositoryImpl.class,
        UpdateMyProfileService.class
})
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class UpdateMyProfileServiceIntegrationTest {

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
    private UpdateMyProfileService updateMyProfileService;

    @MockitoSpyBean
    private UserRepository userRepository;

    @MockitoBean
    private EmailCipher emailCipher;

    @Test
    @DisplayName(
            "사용자 프로필을 수정하면 변경 사항을 PostgreSQL에 저장한다"
    )
    void updateMyProfile_persistsChanges() {
        // given
        User savedUser = userRepository.save(
                createUser(
                        "a".repeat(64),
                        "수정전닉네임"
                )
        );

        when(emailCipher.decrypt(ENCRYPTED_EMAIL))
                .thenReturn(EMAIL);

        UpdateMyProfileCommand command =
                new UpdateMyProfileCommand(
                        "수정후닉네임",
                        LearningLevel.BASIC,
                        6
                );

        // when
        UpdateMyProfileResult result =
                updateMyProfileService.updateMyProfile(
                        savedUser.getId(),
                        command
                );

        // then
        User updatedUser = userRepository
                .findById(savedUser.getId())
                .orElseThrow();

        assertThat(updatedUser.getNickname())
                .isEqualTo("수정후닉네임");

        assertThat(updatedUser.getLearningLevel())
                .isEqualTo(LearningLevel.BASIC);

        assertThat(updatedUser.getJavaExperienceMonths())
                .isEqualTo(6);

        assertThat(updatedUser.getStatus())
                .isEqualTo(UserStatus.ACTIVE);

        assertThat(result.userId())
                .isEqualTo(savedUser.getId());

        assertThat(result.email())
                .isEqualTo(EMAIL);

        assertThat(result.nickname())
                .isEqualTo("수정후닉네임");

        assertThat(result.learningLevel())
                .isEqualTo(LearningLevel.BASIC);

        assertThat(result.javaExperienceMonths())
                .isEqualTo(6);

        assertThat(result.createdAt())
                .isNotNull();

        verify(emailCipher)
                .decrypt(ENCRYPTED_EMAIL);
    }

    @Test
    @DisplayName(
            "다른 활성 사용자의 닉네임으로 변경하면 "
                    + "USER_DUPLICATE_NICKNAME을 반환하고 기존 정보를 유지한다"
    )
    void updateMyProfile_rejectsDuplicateNickname() {
        // given
        userRepository.save(
                createUser(
                        "b".repeat(64),
                        "이미사용중"
                )
        );

        User targetUser = userRepository.save(
                createUser(
                        "c".repeat(64),
                        "변경대상"
                )
        );

        UpdateMyProfileCommand command =
                new UpdateMyProfileCommand(
                        "이미사용중",
                        LearningLevel.BASIC,
                        10
                );

        // when & then
        assertThatThrownBy(
                () -> updateMyProfileService.updateMyProfile(
                        targetUser.getId(),
                        command
                )
        )
                .isInstanceOf(BusinessException.class)
                .extracting(
                        exception ->
                                ((BusinessException) exception)
                                        .getErrorCode()
                )
                .isEqualTo(
                        ErrorCode.USER_DUPLICATE_NICKNAME
                );

        User unchangedUser = userRepository
                .findById(targetUser.getId())
                .orElseThrow();

        assertThat(unchangedUser.getNickname())
                .isEqualTo("변경대상");

        assertThat(unchangedUser.getLearningLevel())
                .isEqualTo(LearningLevel.BEGINNER);

        assertThat(unchangedUser.getJavaExperienceMonths())
                .isEqualTo(3);

        verifyNoInteractions(emailCipher);
    }

    @Test
    @DisplayName(
            "서로 다른 사용자가 같은 닉네임으로 동시에 변경하면 "
                    + "DB UNIQUE 제약으로 한 요청만 성공한다"
    )
    void updateMyProfile_concurrentDuplicateNicknameIsRejected()
            throws Exception {
        // given
        User firstUser = userRepository.save(
                createUser(
                        "d".repeat(64),
                        "첫번째사용자"
                )
        );

        User secondUser = userRepository.save(
                createUser(
                        "e".repeat(64),
                        "두번째사용자"
                )
        );

        String duplicatedNickname = "동시닉네임";

        CyclicBarrier nicknameCheckBarrier =
                new CyclicBarrier(2);

        doAnswer(invocation -> {
            nicknameCheckBarrier.await(
                    5,
                    TimeUnit.SECONDS
            );

            return false;
        }).when(userRepository)
                .existsByNicknameIgnoreCase(
                        duplicatedNickname
                );

        when(emailCipher.decrypt(ENCRYPTED_EMAIL))
                .thenReturn(EMAIL);

        UpdateMyProfileCommand command =
                new UpdateMyProfileCommand(
                        duplicatedNickname,
                        LearningLevel.BASIC,
                        6
                );

        ExecutorService executorService =
                Executors.newFixedThreadPool(2);

        try {
            // when
            Future<Object> firstFuture =
                    executorService.submit(
                            () -> executeUpdate(
                                    firstUser.getId(),
                                    command
                            )
                    );

            Future<Object> secondFuture =
                    executorService.submit(
                            () -> executeUpdate(
                                    secondUser.getId(),
                                    command
                            )
                    );

            List<Object> results = List.of(
                    firstFuture.get(
                            10,
                            TimeUnit.SECONDS
                    ),
                    secondFuture.get(
                            10,
                            TimeUnit.SECONDS
                    )
            );

            // then
            assertThat(results)
                    .filteredOn(
                            UpdateMyProfileResult.class::isInstance
                    )
                    .hasSize(1);

            assertThat(results)
                    .filteredOn(
                            BusinessException.class::isInstance
                    )
                    .singleElement()
                    .satisfies(result ->
                            assertThat(
                                    ((BusinessException) result)
                                            .getErrorCode()
                            ).isEqualTo(
                                    ErrorCode.USER_DUPLICATE_NICKNAME
                            )
                    );
        } finally {
            executorService.shutdownNow();
        }
    }

    private Object executeUpdate(
            UUID userId,
            UpdateMyProfileCommand command
    ) {
        try {
            return updateMyProfileService.updateMyProfile(
                    userId,
                    command
            );
        } catch (RuntimeException exception) {
            return exception;
        }
    }

    private User createUser(
            String emailLookupHash,
            String nickname
    ) {
        return User.create(
                ENCRYPTED_EMAIL,
                emailLookupHash,
                "password-hash",
                nickname,
                3,
                LearningLevel.BEGINNER
        );
    }
}
