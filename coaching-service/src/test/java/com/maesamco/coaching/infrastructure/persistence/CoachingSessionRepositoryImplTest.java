package com.maesamco.coaching.infrastructure.persistence;

import com.maesamco.coaching.domain.entity.CoachingSession;
import com.maesamco.coaching.domain.entity.CoachingSessionStatus;
import com.maesamco.coaching.global.exception.BusinessException;
import com.maesamco.coaching.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoachingSessionRepositoryImplTest extends AbstractCoachingRepositoryTest {

    @Autowired
    private SpringDataCoachingSessionRepository springDataCoachingSessionRepository;

    @Autowired
    private EntityManager entityManager;

    private CoachingSessionRepositoryImpl coachingSessionRepository;

    @BeforeEach
    void setUp() {
        coachingSessionRepository = new CoachingSessionRepositoryImpl(springDataCoachingSessionRepository);
    }

    @Test
    @DisplayName("코칭 세션을 저장하면 ID가 채번되고 IN_PROGRESS 상태·생성 시각이 채워진다")
    void save_assignsIdAndDefaults() {
        // given
        CoachingSession coachingSession = CoachingSession.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1);

        // when
        CoachingSession saved = coachingSessionRepository.save(coachingSession);

        // then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(CoachingSessionStatus.IN_PROGRESS);
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("submissionId로 코칭 세션을 조회할 수 있다")
    void findBySubmissionId_returnsSession() {
        // given
        UUID submissionId = UUID.randomUUID();
        coachingSessionRepository.save(CoachingSession.create(submissionId, UUID.randomUUID(), UUID.randomUUID(), 1));

        /*
         * flush로 INSERT를 실제로 반영하고, clear로 영속성 컨텍스트(1차 캐시)를 비운다.
         * 비우지 않으면 아래 조회가 방금 저장한 Java 객체를 그대로 돌려줄 수 있어서,
         * 컬럼 매핑이 실제로 잘못돼 있어도 테스트가 못 잡아낼 수 있다.
         */
        entityManager.flush();
        entityManager.clear();

        // when
        Optional<CoachingSession> found = coachingSessionRepository.findBySubmissionId(submissionId);

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getSubmissionId()).isEqualTo(submissionId);
    }

    @Test
    @DisplayName("존재하지 않는 submissionId로 조회하면 빈 결과를 반환한다")
    void findBySubmissionId_returnsEmpty_whenNotExists() {
        // when
        Optional<CoachingSession> found = coachingSessionRepository.findBySubmissionId(UUID.randomUUID());

        // then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("complete() 후 다시 저장하면 COMPLETED 상태·completedAt이 실제 DB에도 반영된다")
    void complete_persistsCompletedStatusAndTimestamp() {
        // given — 지금까지는 complete()가 도메인 단위 테스트로만 검증돼서, save()가 변경된
        // 상태를 실제로 갱신 저장하는지는 회귀 방지가 안 되고 있었다(WeakConcept.markImproved()
        // 리뷰와 같은 성격의 갭, PR #34 리뷰에서 발견).
        CoachingSession saved = coachingSessionRepository.save(
                CoachingSession.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1)
        );
        entityManager.flush();
        entityManager.clear();

        CoachingSession found = coachingSessionRepository.findById(saved.getId()).orElseThrow();

        // when
        found.complete();
        coachingSessionRepository.save(found);
        entityManager.flush();
        entityManager.clear();

        // then
        CoachingSession reloaded = coachingSessionRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(CoachingSessionStatus.COMPLETED);
        assertThat(reloaded.getCompletedAt()).isNotNull();
    }

    /**
     * PR #70 리뷰(용현님 P1) — advanceToSubmission()을 호출한
     * 뒤 save()해도, submission_id 컬럼이 @Column(updatable = false)였다면 Hibernate가
     * UPDATE SQL에서 이 컬럼을 통째로 제외해 실제 DB에는 반영되지 않는다. 이전 값은
     * 엔티티 객체(1차 캐시)에서만 보였을 뿐이라 Mockito 기반 Facade/QueryService 테스트로는
     * 이 문제를 잡을 수 없다 — entityManager.clear()로 1차 캐시를 비우고 완전히 새로
     * 조회해서 실제 DB 반영 여부를 확인한다. last_attempt_no(PR #88 리뷰, 용현님 P1)도
     * 같은 UPDATE 문에 실려야 갈아탄 값이 유지되므로 함께 검증한다.
     */
    @Test
    @DisplayName("advanceToSubmission() 후 다시 저장하면 최신 submissionId·lastAttemptNo가 실제 DB에도 반영된다")
    void advanceToSubmission_persistsNewSubmissionIdAndAttemptNoAfterReload() {
        // given
        UUID originalSubmissionId = UUID.randomUUID();
        CoachingSession saved = coachingSessionRepository.save(
                CoachingSession.create(originalSubmissionId, UUID.randomUUID(), UUID.randomUUID(), 1)
        );
        entityManager.flush();
        entityManager.clear();

        CoachingSession found = coachingSessionRepository.findById(saved.getId()).orElseThrow();
        UUID retrySubmissionId = UUID.randomUUID();

        // when
        found.advanceToSubmission(retrySubmissionId, 2);
        coachingSessionRepository.save(found);
        entityManager.flush();
        entityManager.clear();

        // then — 1차 캐시를 비운 뒤 완전히 새로 조회해서 실제 DB 값을 확인한다
        CoachingSession reloaded = coachingSessionRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getSubmissionId()).isEqualTo(retrySubmissionId);
        assertThat(reloaded.getSubmissionId()).isNotEqualTo(originalSubmissionId);
        assertThat(reloaded.getLastAttemptNo()).isEqualTo(2);
    }

    /**
     * PR #88 리뷰(용현님 P1) 대응 — advanceToSubmission()의 역행 방지 자체는
     * CoachingSessionTest(도메인 단위)에서 검증하지만, "역행 시도가 실제 DB에는 아무
     * 영향도 주지 않는다"는 건 실제 저장·재조회로만 증명된다.
     */
    @Test
    @DisplayName("attemptNo가 더 작은 제출로 advanceToSubmission()을 시도해도 DB의 submissionId는 그대로다")
    void advanceToSubmission_withOlderAttempt_doesNotPersistChange() {
        // given
        UUID originalSubmissionId = UUID.randomUUID();
        CoachingSession saved = coachingSessionRepository.save(
                CoachingSession.create(originalSubmissionId, UUID.randomUUID(), UUID.randomUUID(), 3)
        );
        entityManager.flush();
        entityManager.clear();

        CoachingSession found = coachingSessionRepository.findById(saved.getId()).orElseThrow();

        // when — attemptNo 2는 이미 저장된 3보다 오래된 시도이므로 무시돼야 한다
        boolean advanced = found.advanceToSubmission(UUID.randomUUID(), 2);
        coachingSessionRepository.save(found);
        entityManager.flush();
        entityManager.clear();

        // then
        assertThat(advanced).isFalse();
        CoachingSession reloaded = coachingSessionRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getSubmissionId()).isEqualTo(originalSubmissionId);
        assertThat(reloaded.getLastAttemptNo()).isEqualTo(3);
    }

    /**
     * 이슈 #84(V5) — 문제당 세션은 평생 최대 1개다. V4까지는 IN_PROGRESS인 세션만
     * 유일성을 강제해서 COMPLETED 후 재도전하면 새 세션이 만들어질 수 있었는데, 이제는
     * 완전한 UNIQUE(user_id, problem_id)라 COMPLETED 상태에서도 새 세션 생성이 막혀야 한다.
     * 이 시나리오를 검증하는 테스트가 없었어서(V4 부분 UNIQUE 인덱스 자체를 직접 재현하는
     * 테스트가 없었음) 새로 추가한다 — 실제 Postgres로 제약을 검증해야 실수로 부분 UNIQUE로
     * 되돌아가는 회귀를 잡을 수 있다.
     */
    @Test
    @DisplayName("완료된 세션이 있어도 같은 (user_id, problem_id)로 새 세션을 저장하면 COACHING_SESSION_ALREADY_EXISTS(409)로 실패한다")
    void save_throwsWhenSessionAlreadyExistsForSameUserAndProblem_evenAfterCompleted() {
        // given
        UUID userId = UUID.randomUUID();
        UUID problemId = UUID.randomUUID();

        CoachingSession completedSession = coachingSessionRepository.save(
                CoachingSession.create(UUID.randomUUID(), userId, problemId, 1)
        );
        completedSession.complete();
        coachingSessionRepository.save(completedSession);
        entityManager.flush();
        entityManager.clear();

        CoachingSession retrySession = CoachingSession.create(UUID.randomUUID(), userId, problemId, 1);

        // when & then
        assertThatThrownBy(() -> coachingSessionRepository.save(retrySession))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COACHING_SESSION_ALREADY_EXISTS)
                );
    }

    @Test
    @DisplayName("findByUserIdAndProblemId는 세션이 COMPLETED여도 그대로 조회한다")
    void findByUserIdAndProblemId_returnsSession_regardlessOfStatus() {
        // given
        UUID userId = UUID.randomUUID();
        UUID problemId = UUID.randomUUID();

        CoachingSession saved = coachingSessionRepository.save(
                CoachingSession.create(UUID.randomUUID(), userId, problemId, 1)
        );
        saved.complete();
        coachingSessionRepository.save(saved);
        entityManager.flush();
        entityManager.clear();

        // when
        Optional<CoachingSession> found = coachingSessionRepository.findByUserIdAndProblemId(userId, problemId);

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
        assertThat(found.get().getStatus()).isEqualTo(CoachingSessionStatus.COMPLETED);
    }

    @Test
    @DisplayName("동일한 submissionId로 두 번 저장하면 COACHING_SESSION_ALREADY_EXISTS(409)로 실패한다")
    void save_throwsWhenSubmissionIdAlreadyExists() {
        /*
         * CoachingSessionRepositoryImpl.save()가 saveAndFlush + try-catch로 UNIQUE 위반을
         * 직접 잡아 BusinessException으로 변환하므로(Spring 프록시가 아니라 메서드 내부의
         * 명시적 예외 처리), 이 테스트에서 new로 직접 만든 순수 객체(coachingSessionRepository)를
         * 그대로 호출해도 변환된 예외를 검증할 수 있다.
         */
        // given
        UUID submissionId = UUID.randomUUID();
        coachingSessionRepository.save(CoachingSession.create(submissionId, UUID.randomUUID(), UUID.randomUUID(), 1));

        CoachingSession duplicate = CoachingSession.create(submissionId, UUID.randomUUID(), UUID.randomUUID(), 1);

        // when & then
        assertThatThrownBy(() -> coachingSessionRepository.save(duplicate))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COACHING_SESSION_ALREADY_EXISTS)
                );
    }
}
