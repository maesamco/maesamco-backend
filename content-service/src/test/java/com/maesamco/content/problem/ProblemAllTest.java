package com.maesamco.content.problem;

import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import com.maesamco.content.problem.application.service.ProblemService;
import com.maesamco.content.problem.domain.entity.Problem;
import com.maesamco.content.problem.domain.enums.*;
import com.maesamco.content.problem.domain.repository.ProblemRepository;
import com.maesamco.content.problem.presentation.dto.request.ProblemCreateRequest;
import com.maesamco.content.problem.presentation.dto.request.ProblemUpdateRequest;
import com.maesamco.content.problem.presentation.dto.response.ProblemCreateResponse;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@Transactional
class ProblemAllTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17-alpine")
                    .withDatabaseName("content_test")
                    .withUsername("test")
                    .withPassword("test");

    private static final KeyPair KEY_PAIR =
            generateKeyPair();

    @DynamicPropertySource
    static void properties(
            DynamicPropertyRegistry registry
    ) {
        registry.add(
                "jwt.public-key",
                () -> Base64.getEncoder()
                        .encodeToString(
                                KEY_PAIR.getPublic()
                                        .getEncoded()
                        )
        );

        registry.add(
                "internal.hmac.keys.coaching-service",
                () -> "test-hmac-key-for-coaching-content"
        );

        // @SpringBootTest 전체 Context 로딩 시
        // UserServiceFeignConfig Bean 생성에 필요한 테스트용 HMAC 키
        registry.add(
                "internal.hmac.outbound.user-service",
                () -> "test-hmac-key-for-content-user"
        );
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator =
                    KeyPairGenerator.getInstance("RSA");

            generator.initialize(2048);

            return generator.generateKeyPair();

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "테스트 RSA 키를 생성할 수 없습니다.",
                    exception
            );
        }
    }

    @Autowired
    private ProblemService problemService;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final UUID adminId =
            UUID.randomUUID();

    @BeforeEach
    void setUpAuthentication() {
        SecurityContext context =
                SecurityContextHolder.createEmptyContext();

        context.setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        adminId,
                        null,
                        List.of(
                                new SimpleGrantedAuthority(
                                        "ROLE_ADMIN"
                                )
                        )
                )
        );

        SecurityContextHolder.setContext(context);
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    // ============================================================
    // 1. CREATE + UPDATE
    // ============================================================

    @Test
    @DisplayName(
            "문제를 생성하고 수정하면 실제 PostgreSQL에 변경 내용과 lockVersion이 반영된다"
    )
    void createAndUpdateProblem_realPostgres_success()
            throws Exception {

        // given
        String createJson = """
                {
                    "title": "두 수의 합",
                    "language": "JAVA",
                    "difficulty": "EASY",
                    "type": "CODE",
                    "description": "두 정수를 입력받아 합을 출력하세요.",
                    "starterCode": "public class Main {}",
                    "runningTimeLimit": "%s",
                    "runningMemoryLimit": "%s",
                    "timerPolicy": "APPLY60",
                    "source": "HUMAN_AUTHORED"
                }
                """.formatted(
                RunningTimeLimit.values()[0].name(),
                RunningMemoryLimit.values()[0].name()
        );

        ProblemCreateRequest createRequest =
                jsonMapper.readValue(
                        createJson,
                        ProblemCreateRequest.class
                );

        // when - 생성
        ProblemCreateResponse createResponse =
                problemService.createProblem(
                        createRequest
                );

        UUID problemId =
                createResponse.getId();

        entityManager.flush();
        entityManager.clear();

        // then - 실제 DB에서 재조회
        Problem createdProblem =
                problemRepository.findById(problemId)
                        .orElseThrow();

        assertThat(createdProblem.getTitle())
                .isEqualTo("두 수의 합");

        assertThat(createdProblem.getLanguage())
                .isEqualTo(ProgrammingLanguage.JAVA);

        assertThat(createdProblem.getCurrentVersionNo())
                .isEqualTo(1);

        assertThat(createdProblem.getLockVersion())
                .isEqualTo(0L);

        Long currentLockVersion =
                createdProblem.getLockVersion();

        // given - 수정 요청
        String updateJson = """
                {
                    "lockVersion": %d,
                    "title": "수정된 문제",
                    "difficulty": "HARD",
                    "starterCode": null,
                    "timerPolicy": "APPLY300",
                    "source": "AI_ASSISTED"
                }
                """.formatted(
                currentLockVersion
        );

        ProblemUpdateRequest updateRequest =
                jsonMapper.readValue(
                        updateJson,
                        ProblemUpdateRequest.class
                );

        // when - 수정
        problemService.updateProblem(
                problemId,
                updateRequest
        );

        entityManager.flush();
        entityManager.clear();

        // then
        Problem updatedProblem =
                problemRepository.findById(problemId)
                        .orElseThrow();

        assertThat(updatedProblem.getTitle())
                .isEqualTo("수정된 문제");

        assertThat(updatedProblem.getDifficulty())
                .isEqualTo(ProblemDifficulty.HARD);

        assertThat(updatedProblem.getStarterCode())
                .isNull();

        assertThat(updatedProblem.getCurrentVersionNo())
                .isEqualTo(2);

        assertThat(updatedProblem.getLockVersion())
                .isEqualTo(
                        currentLockVersion + 1
                );
    }

    // ============================================================
    // 2. DELETE
    // ============================================================

    @Test
    @DisplayName(
            "문제를 삭제하면 실제 PostgreSQL에 deletedAt, deletedBy와 증가된 lockVersion이 반영된다"
    )
    void deleteProblem_realPostgres_success() {

        // given
        Problem problem =
                createProblem(
                        "삭제 테스트 문제"
                );

        Problem savedProblem =
                problemRepository.saveAndFlush(
                        problem
                );

        UUID problemId =
                savedProblem.getId();

        Long lockVersion =
                savedProblem.getLockVersion();

        entityManager.clear();

        // when
        problemService.deleteProblem(
                problemId,
                adminId
        );

        /*
         * softDelete 변경 내용을 실제 PostgreSQL에 반영한다.
         * 이때 @Version도 함께 증가한다.
         */
        problemRepository.flush();

        entityManager.clear();

        // then
        Map<String, Object> row =
                jdbcTemplate.queryForMap(
                        """
                        SELECT deleted_at,
                               deleted_by,
                               lock_version
                        FROM content_schema.p_problems
                        WHERE id = ?
                        """,
                        problemId
                );

        assertThat(row.get("deleted_at"))
                .isNotNull();

        assertThat(row.get("deleted_by"))
                .isEqualTo(adminId);

        assertThat(
                ((Number) row.get("lock_version"))
                        .longValue()
        )
                .isEqualTo(
                        lockVersion + 1
                );
    }

    // ============================================================
    // 3. Service + 실제 DB lockVersion 검증
    // ============================================================

    @Test
    @DisplayName(
            "오래된 lockVersion으로 문제를 수정하면 서비스가 동시성 충돌로 거부한다"
    )
    void updateProblem_withStaleLockVersion_throwsException() {

        // given
        Problem problem =
                createProblem(
                        "동시성 테스트 문제"
                );

        Problem savedProblem =
                problemRepository.saveAndFlush(
                        problem
                );

        UUID problemId =
                savedProblem.getId();

        Long staleLockVersion =
                savedProblem.getLockVersion(); // 0

        /*
         * 다른 관리자가 먼저 문제를 수정했다고 가정합니다.
         */
        savedProblem.changeTitle(
                "다른 관리자가 먼저 수정"
        );

        problemRepository.flush();

        assertThat(savedProblem.getLockVersion())
                .isEqualTo(1L);

        ProblemUpdateRequest request =
                new ProblemUpdateRequest();

        ReflectionTestUtils.setField(
                request,
                "title",
                "오래된 버전을 기반으로 한 수정"
        );

        ReflectionTestUtils.setField(
                request,
                "lockVersion",
                staleLockVersion
        );

        // when & then
        assertThatThrownBy(
                () -> problemService.updateProblem(
                        problemId,
                        request
                )
        )
                .isInstanceOf(
                        BusinessException.class
                )
                .satisfies(exception -> {

                    BusinessException businessException =
                            (BusinessException) exception;

                    assertThat(
                            businessException.getErrorCode()
                    )
                            .isEqualTo(
                                    ErrorCode.PROBLEM_MODIFIED_CONCURRENTLY
                            );
                });
    }

    // ============================================================
    // 4. 실제 JPA @Version 충돌
    // ============================================================

    @Test
    @DisplayName(
            "오래된 lockVersion을 가진 엔티티를 저장하면 실제 PostgreSQL에서 낙관적 락 충돌이 발생한다"
    )
    void saveAndFlush_withStaleEntity_throwsOptimisticLockingFailure() {

        // given
        Problem problem =
                createProblem(
                        "JPA 낙관적 락 테스트"
                );

        Problem savedProblem =
                problemRepository.saveAndFlush(
                        problem
                );

        UUID problemId =
                savedProblem.getId();

        entityManager.clear();

        /*
         * 같은 DB row를 각각 lockVersion 0 상태로 조회하고
         * 영속성 컨텍스트에서 분리합니다.
         */
        Problem firstProblem =
                problemRepository.findById(
                                problemId
                        )
                        .orElseThrow();

        entityManager.detach(
                firstProblem
        );

        Problem secondProblem =
                problemRepository.findById(
                                problemId
                        )
                        .orElseThrow();

        entityManager.detach(
                secondProblem
        );

        assertThat(firstProblem.getLockVersion())
                .isEqualTo(0L);

        assertThat(secondProblem.getLockVersion())
                .isEqualTo(0L);

        /*
         * 첫 번째 관리자가 먼저 수정한다.
         */
        firstProblem.changeTitle(
                "첫 번째 관리자 수정"
        );

        Problem firstUpdated =
                problemRepository.saveAndFlush(
                        firstProblem
                );

        assertThat(firstUpdated.getLockVersion())
                .isEqualTo(1L);

        entityManager.clear();

        /*
         * 두 번째 객체는 아직 lockVersion 0을 가지고 있다.
         */
        secondProblem.changeDescription(
                "두 번째 관리자 수정"
        );

        // when & then
        assertThatThrownBy(
                () -> problemRepository.saveAndFlush(
                        secondProblem
                )
        )
                .isInstanceOf(
                        ObjectOptimisticLockingFailureException.class
                );
    }

    // ============================================================
    // Fixture
    // ============================================================

    private Problem createProblem(
            String title
    ) {
        return Problem.create(
                title,
                ProgrammingLanguage.JAVA,
                ProblemDifficulty.EASY,
                ProblemType.CODE,
                "문제 설명",
                "public class Main {}",
                RunningTimeLimit.values()[0],
                RunningMemoryLimit.values()[0],
                TimerPolicy.APPLY60,
                ProblemSource.HUMAN_AUTHORED,
                ProblemStatus.REVIEW_PENDING
        );
    }
}