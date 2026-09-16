package com.maesamco.user.application.service;

import com.maesamco.user.application.port.ConceptValidationPort;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;

/**
 * 관심 개념 전체 교체 서비스의 실제 PostgreSQL 연동을 검증합니다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@Import({
        JpaAuditingConfig.class,
        UserRepositoryImpl.class,
        UserInterestConceptRepositoryImpl.class,
        UpdateMyInterestsService.class
})
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class UpdateMyInterestsServiceIntegrationTest {

    private static final UUID CONCEPT_ID_1 =
            UUID.fromString(
                    "11111111-1111-1111-1111-111111111111"
            );

    private static final UUID CONCEPT_ID_2 =
            UUID.fromString(
                    "22222222-2222-2222-2222-222222222222"
            );

    private static final UUID CONCEPT_ID_3 =
            UUID.fromString(
                    "33333333-3333-3333-3333-333333333333"
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
    private UpdateMyInterestsService updateMyInterestsService;

    @Autowired
    private UserRepository userRepository;

    @MockitoSpyBean
    private UserInterestConceptRepository
            interestConceptRepository;

    @MockitoBean
    private ConceptValidationPort conceptValidationPort;

    @Test
    @DisplayName(
            "기존 관심 개념을 요청한 새로운 목록으로 전체 교체한다"
    )
    void replaceInterests() {
        // given
        User user = saveUser(
                "a".repeat(64),
                "관심교체사용자"
        );

        interestConceptRepository.saveAllAndFlush(
                List.of(
                        UserInterestConcept.create(
                                user.getId(),
                                CONCEPT_ID_1
                        ),
                        UserInterestConcept.create(
                                user.getId(),
                                CONCEPT_ID_2
                        )
                )
        );

        UpdateMyInterestsCommand command =
                new UpdateMyInterestsCommand(
                        List.of(
                                CONCEPT_ID_2,
                                CONCEPT_ID_3
                        )
                );

        // when
        UpdateMyInterestsResult result =
                updateMyInterestsService.updateMyInterests(
                        user.getId(),
                        command
                );

        // then
        Set<UUID> storedConceptIds =
                interestConceptRepository
                        .findAllByUserId(user.getId())
                        .stream()
                        .map(
                                UserInterestConcept::getConceptId
                        )
                        .collect(
                                Collectors.toSet()
                        );

        assertThat(storedConceptIds)
                .containsExactlyInAnyOrder(
                        CONCEPT_ID_2,
                        CONCEPT_ID_3
                );

        assertThat(result.interestConceptIds())
                .containsExactly(
                        CONCEPT_ID_2,
                        CONCEPT_ID_3
                );

        assertThat(result.count())
                .isEqualTo(2);

        assertThat(result.updatedAt())
                .isNotNull();

        verify(conceptValidationPort)
                .validateAll(
                        List.of(
                                CONCEPT_ID_2,
                                CONCEPT_ID_3
                        )
                );
    }

    @Test
    @DisplayName(
            "빈 배열을 전달하면 기존 관심 개념을 모두 해제한다"
    )
    void clearInterests() {
        // given
        User user = saveUser(
                "b".repeat(64),
                "관심해제사용자"
        );

        interestConceptRepository.saveAllAndFlush(
                List.of(
                        UserInterestConcept.create(
                                user.getId(),
                                CONCEPT_ID_1
                        ),
                        UserInterestConcept.create(
                                user.getId(),
                                CONCEPT_ID_2
                        )
                )
        );

        UpdateMyInterestsCommand command =
                new UpdateMyInterestsCommand(
                        List.of()
                );

        // when
        UpdateMyInterestsResult result =
                updateMyInterestsService.updateMyInterests(
                        user.getId(),
                        command
                );

        // then
        assertThat(
                interestConceptRepository.findAllByUserId(
                        user.getId()
                )
        ).isEmpty();

        assertThat(result.interestConceptIds())
                .isEmpty();

        assertThat(result.count())
                .isZero();

        verifyNoInteractions(
                conceptValidationPort
        );
    }

    @Test
    @DisplayName(
            "개념 검증에 실패하면 기존 관심 개념을 유지한다"
    )
    void preserveInterestsWhenValidationFails() {
        // given
        User user = saveUser(
                "c".repeat(64),
                "검증실패사용자"
        );

        interestConceptRepository.saveAllAndFlush(
                List.of(
                        UserInterestConcept.create(
                                user.getId(),
                                CONCEPT_ID_1
                        )
                )
        );

        UpdateMyInterestsCommand command =
                new UpdateMyInterestsCommand(
                        List.of(
                                CONCEPT_ID_2
                        )
                );

        doThrow(
                new BusinessException(
                        ErrorCode.CONTENT_SERVICE_UNAVAILABLE
                )
        ).when(
                conceptValidationPort
        ).validateAll(
                List.of(
                        CONCEPT_ID_2
                )
        );

        // when & then
        assertThatThrownBy(
                () -> updateMyInterestsService.updateMyInterests(
                        user.getId(),
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
                        ErrorCode.CONTENT_SERVICE_UNAVAILABLE
                );

        assertThat(
                interestConceptRepository
                        .findAllByUserId(user.getId())
                        .stream()
                        .map(
                                UserInterestConcept::getConceptId
                        )
                        .toList()
        ).containsExactly(
                CONCEPT_ID_1
        );
    }

    @Test
    @DisplayName(
            "활성 상태의 동일한 사용자와 개념은 "
                    + "V5 부분 UNIQUE 인덱스가 중복 저장을 차단한다"
    )
    void rejectDuplicatedActiveInterest() {
        // given
        User user = saveUser(
                "d".repeat(64),
                "중복검증사용자"
        );

        interestConceptRepository.saveAllAndFlush(
                List.of(
                        UserInterestConcept.create(
                                user.getId(),
                                CONCEPT_ID_1
                        )
                )
        );

        UserInterestConcept duplicated =
                UserInterestConcept.create(
                        user.getId(),
                        CONCEPT_ID_1
                );

        // when & then
        assertThatThrownBy(
                () -> interestConceptRepository
                        .saveAllAndFlush(
                                List.of(duplicated)
                        )
        ).isInstanceOf(
                DataIntegrityViolationException.class
        );
    }

    private User saveUser(
            String emailLookupHash,
            String nickname
    ) {
        return userRepository.save(
                User.create(
                        "encrypted-email",
                        emailLookupHash,
                        "password-hash",
                        nickname,
                        3,
                        LearningLevel.BEGINNER
                )
        );
    }

    @Test
    @DisplayName(
            "동일 사용자의 두 변경 요청이 동시에 실행되면 "
                    + "각 요청을 원자적으로 처리한다"
    )
    void concurrentRequestsAreSerialized() throws Exception {
        // given
        User user = saveUser(
                "e".repeat(64),
                "동시요청사용자"
        );

        UpdateMyInterestsCommand firstCommand =
                new UpdateMyInterestsCommand(
                        List.of(
                                CONCEPT_ID_1,
                                CONCEPT_ID_2
                        )
                );

        UpdateMyInterestsCommand secondCommand =
                new UpdateMyInterestsCommand(
                        List.of(
                                CONCEPT_ID_2,
                                CONCEPT_ID_3
                        )
                );

        CountDownLatch readyLatch =
                new CountDownLatch(2);

        CountDownLatch startLatch =
                new CountDownLatch(1);

        AtomicReference<List<UUID>> lastCompleted =
                new AtomicReference<>();

        ExecutorService executorService =
                Executors.newFixedThreadPool(2);

        Callable<Void> firstTask = () -> {
            readyLatch.countDown();
            startLatch.await();

            UpdateMyInterestsResult result =
                    updateMyInterestsService.updateMyInterests(
                            user.getId(),
                            firstCommand
                    );

            lastCompleted.set(
                    result.interestConceptIds()
            );

            return null;
        };

        Callable<Void> secondTask = () -> {
            readyLatch.countDown();
            startLatch.await();

            UpdateMyInterestsResult result =
                    updateMyInterestsService.updateMyInterests(
                            user.getId(),
                            secondCommand
                    );

            lastCompleted.set(
                    result.interestConceptIds()
            );

            return null;
        };

        try {
            Future<Void> firstFuture =
                    executorService.submit(firstTask);

            Future<Void> secondFuture =
                    executorService.submit(secondTask);

            assertThat(
                    readyLatch.await(
                            5,
                            SECONDS
                    )
            ).isTrue();

            startLatch.countDown();

            firstFuture.get(
                    15,
                    SECONDS
            );

            secondFuture.get(
                    15,
                    SECONDS
            );
        } finally {
            executorService.shutdownNow();
        }

        // then
        List<UUID> expectedConceptIds =
                lastCompleted.get();

        List<UUID> storedConceptIds =
                interestConceptRepository
                        .findAllByUserId(user.getId())
                        .stream()
                        .map(
                                UserInterestConcept::getConceptId
                        )
                        .toList();

        assertThat(expectedConceptIds)
                .isNotNull();

        assertThat(storedConceptIds)
                .containsExactlyInAnyOrderElementsOf(
                        expectedConceptIds
                )
                .hasSize(2);
    }

    @Test
    @DisplayName(
            "새 관심 개념 저장에 실패하면 "
                    + "기존 관심 개념 삭제까지 모두 롤백한다"
    )
    void rollbackWhenSavingNewInterestsFails() {
        // given
        User user = saveUser(
                "f".repeat(64),
                "롤백검증사용자"
        );

        interestConceptRepository.saveAllAndFlush(
                List.of(
                        UserInterestConcept.create(
                                user.getId(),
                                CONCEPT_ID_1
                        ),
                        UserInterestConcept.create(
                                user.getId(),
                                CONCEPT_ID_2
                        )
                )
        );

        AtomicInteger saveInvocationCount =
                new AtomicInteger();

        /*
         * 교체 과정의 첫 저장인 기존 관계 논리 삭제는 실행하고,
         * 두 번째 저장인 신규 관계 추가에서 DB 장애를 발생시킵니다.
         */
        doAnswer(invocation -> {
            if (saveInvocationCount.incrementAndGet() == 2) {
                throw new DataAccessResourceFailureException(
                        "관심 개념 저장 실패"
                );
            }

            return invocation.callRealMethod();
        }).when(
                interestConceptRepository
        ).saveAllAndFlush(
                anyList()
        );

        UpdateMyInterestsCommand command =
                new UpdateMyInterestsCommand(
                        List.of(
                                CONCEPT_ID_2,
                                CONCEPT_ID_3
                        )
                );

        // when & then
        assertThatThrownBy(
                () -> updateMyInterestsService.updateMyInterests(
                        user.getId(),
                        command
                )
        ).isInstanceOf(
                DataAccessResourceFailureException.class
        );

        List<UUID> storedConceptIds =
                interestConceptRepository
                        .findAllByUserId(user.getId())
                        .stream()
                        .map(
                                UserInterestConcept::getConceptId
                        )
                        .toList();

        /*
         * 신규 목록 일부가 남거나 기존 목록 일부가 삭제되지 않고
         * 트랜잭션 시작 전 목록이 그대로 복구되어야 합니다.
         */
        assertThat(storedConceptIds)
                .containsExactlyInAnyOrder(
                        CONCEPT_ID_1,
                        CONCEPT_ID_2
                );
    }
}
