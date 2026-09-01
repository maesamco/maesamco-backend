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
        CoachingSession coachingSession = CoachingSession.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

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
        coachingSessionRepository.save(CoachingSession.create(submissionId, UUID.randomUUID(), UUID.randomUUID()));

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
                CoachingSession.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
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
        coachingSessionRepository.save(CoachingSession.create(submissionId, UUID.randomUUID(), UUID.randomUUID()));

        CoachingSession duplicate = CoachingSession.create(submissionId, UUID.randomUUID(), UUID.randomUUID());

        // when & then
        assertThatThrownBy(() -> coachingSessionRepository.save(duplicate))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COACHING_SESSION_ALREADY_EXISTS)
                );
    }
}
