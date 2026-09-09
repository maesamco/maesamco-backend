package com.maesamco.content.problem;

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
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@Transactional
class ProblemDBTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17-alpine")
                    .withDatabaseName("content_test")
                    .withUsername("test")
                    .withPassword("test");

    // JWT 임시키 생성
    private static final KeyPair KEY_PAIR = generateKeyPair();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add(
                "jwt.public-key",
                () -> Base64.getEncoder()
                        .encodeToString(KEY_PAIR.getPublic().getEncoded())
        );

        registry.add(
                "internal.hmac.keys.coaching-service",
                () -> "test-hmac-key-for-coaching-content"
        );

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

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Autowired
    private ProblemService problemService;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private EntityManager entityManager;

    private final UUID adminId = UUID.randomUUID();

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

    @Test
    @DisplayName(
            "문제를 생성하고 수정하면 실제 PostgreSQL에 변경 내용이 반영된다"
    )
    void createAndUpdateProblem_realPostgres_success()
            throws Exception {

        // given - 문제 생성 요청
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

        // when - 문제 생성
        ProblemCreateResponse createResponse =
                problemService.createProblem(
                        createRequest
                );

        UUID problemId =
                createResponse.getId();

        /*
         * INSERT SQL을 실제 PostgreSQL에 반영하고
         * 1차 캐시를 비운다.
         */
        entityManager.flush();
        entityManager.clear();

        // then - 실제 DB에서 다시 조회
        Problem createdProblem =
                problemRepository.findById(problemId)
                        .orElseThrow();

        assertThat(createdProblem.getTitle())
                .isEqualTo("두 수의 합");

        assertThat(createdProblem.getLanguage())
                .isEqualTo(ProgrammingLanguage.JAVA);

        assertThat(createdProblem.getDifficulty())
                .isEqualTo(ProblemDifficulty.EASY);

        assertThat(createdProblem.getStarterCode())
                .isEqualTo("public class Main {}");

        assertThat(createdProblem.getCurrentVersionNo())
                .isEqualTo(1);

        // given - 문제 수정 요청
        String updateJson = """
                {
                    "title": "수정된 문제",
                    "difficulty": "HARD",
                    "starterCode": null,
                    "timerPolicy": "APPLY300",
                    "source": "AI_ASSISTED"
                }
                """;

        ProblemUpdateRequest updateRequest =
                jsonMapper.readValue(
                        updateJson,
                        ProblemUpdateRequest.class
                );

        // when - 문제 수정
        problemService.updateProblem(
                problemId,
                updateRequest
        );

        /*
         * UPDATE SQL을 실제 PostgreSQL에 반영하고
         * 다시 1차 캐시를 비운다.
         */
        entityManager.flush();
        entityManager.clear();

        // then - PostgreSQL에서 다시 조회
        Problem updatedProblem =
                problemRepository.findById(problemId)
                        .orElseThrow();

        assertThat(updatedProblem.getTitle())
                .isEqualTo("수정된 문제");

        assertThat(updatedProblem.getDifficulty())
                .isEqualTo(ProblemDifficulty.HARD);

        assertThat(updatedProblem.getStarterCode())
                .isNull();

        assertThat(updatedProblem.getTimerPolicy())
                .isEqualTo(TimerPolicy.APPLY300);

        assertThat(updatedProblem.getSource())
                .isEqualTo(ProblemSource.AI_ASSISTED);

        assertThat(updatedProblem.getCurrentVersionNo())
                .isEqualTo(2);
    }

    @Test
    @DisplayName("같은 문제의 오래된 버전을 수정하면 낙관적 락 예외가 발생한다")
    void updateProblem_withStaleVersion_throwsOptimisticLockingFailure() {

        // given
        Problem problem = Problem.create(
                "동시성 테스트 문제",
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

        Problem savedProblem =
                problemRepository.saveAndFlush(problem);

        UUID problemId = savedProblem.getId();

        entityManager.clear();

        /*
         * 같은 DB row를 동일한 lockVersion으로 두 번 읽어
         * 서로 독립된 stale 객체를 만든다.
         */
        Problem firstProblem =
                problemRepository.findById(problemId)
                        .orElseThrow();

        entityManager.detach(firstProblem);

        Problem secondProblem =
                problemRepository.findById(problemId)
                        .orElseThrow();

        entityManager.detach(secondProblem);

        assertThat(firstProblem.getLockVersion())
                .isEqualTo(0L);

        assertThat(secondProblem.getLockVersion())
                .isEqualTo(0L);

        // 첫 번째 수정은 정상 반영
        firstProblem.changeTitle("첫 번째 수정");

        Problem firstUpdated =
                problemRepository.saveAndFlush(firstProblem);

        assertThat(firstUpdated.getLockVersion())
                .isEqualTo(1L);

        entityManager.clear();

        // 두 번째 객체는 여전히 lockVersion = 0인 stale 상태
        secondProblem.changeDescription("두 번째 수정");

        // when & then
        assertThatThrownBy(
                () -> problemRepository.saveAndFlush(secondProblem)
        )
                .isInstanceOf(
                        ObjectOptimisticLockingFailureException.class
                );
    }
}
