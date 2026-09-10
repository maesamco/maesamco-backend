package com.maesamco.coaching.application.persistence_service;

import com.maesamco.coaching.domain.entity.CoachingEventOutbox;
import com.maesamco.coaching.domain.entity.OutboxStatus;
import com.maesamco.coaching.domain.repository.CoachingEventOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Outbox Relay(Facade)가 Kafka 호출 사이사이에 배치하는 짧은 DB 트랜잭션 조각
 * (팀 컨벤션 2절 Facade — persistence_service). Judge Service의
 * `SubmissionEventOutboxPersistenceService`(이슈 #63)와 같은 설계지만, `CoachingSession`은
 * Outbox 행이 만들어지는 시점에 이미 COMPLETED로 확정돼 있어서(이슈 #51) Submission처럼
 * 발행 성공/실패에 따라 같이 전이시켜야 할 상태가 없다 — 그래서 여기는 Outbox 자체의
 * 상태만 다룬다.
 *
 * PR #120(Judge Service, 이슈 #63) 리뷰에서 나온 지적 두 가지를 참고했다(용현님, 2026-09-08).
 * 다만 (1)은 PR120과 동일한 결론을 낸 게 아니라 독자적으로 다른 정책을 택한 것이므로
 * 정정해둔다(PR #123 심층 재검토, 2026-09-09 — 처음엔 "PR120 지적을 반영했다"고 서술했으나
 * 부정확했다):
 * (1) 용현님의 실제 지적은 "Outbox 종료(FAILED)가 재시도 상한 로직을 공유하는 바람에 엉뚱한
 *     Submission까지 같이 실패 처리된다"는 **엔티티 간 정합성 문제**였다. Judge의 실제 수정은
 *     recordFailedAttempt와 recordPostPublishFailure의 카운터·종료 로직을 분리했을 뿐,
 *     recordPostPublishFailure도 여전히 상한(5회) 도달 시 Outbox를 FAILED로 종료한다
 *     (Submission만 안 건드림). 반면 Coaching은 recordPostPublishFailure를 상한 없는 무한
 *     재시도로 만들었다 — CoachingSession엔 Outbox 종료로 같이 잘못될 다른 엔티티가 없어서,
 *     "이벤트가 이미 Kafka에 전달됐을 수 있으니 FAILED로 잘못 표시하면 안 된다"는 논리만으로
 *     독자적으로 내린 결정이다. 두 서비스가 지금 같은 시나리오에 다른 정책을 갖고 있다는
 *     뜻이므로, Judge 쪽 정책이 나중에 바뀔 여지가 있으면 그때 다시 맞출지 논의가 필요하다.
 * (2) 복수 Relay 실행(또는 같은 폴링 배치의 뒤늦은 재시도)이 이미 COMPLETED/FAILED로 끝난
 *     Outbox를 다시 건드리지 않도록, 실패 기록 전에 현재 상태가 PENDING인지 먼저 확인한다 —
 *     이건 PR120의 실제 수정과 동일하다.
 *
 * PR #123 재검토 2차(용현님, 2026-09-10) 반영:
 * (3) 위 (2)의 "재조회 후 PENDING 확인"은 check-then-act라, 두 Relay 인스턴스가 거의 동시에
 *     같은 행을 PENDING으로 읽으면 막지 못한다. {@code CoachingEventOutbox}에 낙관적 락
 *     (`@Version`)을 추가하고, `CoachingEventOutboxRepositoryImpl.save()`가
 *     `saveAndFlush()`로 즉시 flush하게 만들어서, 나중에 flush되는 트랜잭션이
 *     `ObjectOptimisticLockingFailureException`을 이 메서드 안에서 바로 받게 했다 — status
 *     체크로 걸러지는 경우와 동일하게 "다른 Relay가 이미 처리함"으로 보고 무시한다.
 * (4) `recordPostPublishFailure()`의 무한 재시도가 Relay의 oldest-first LIMIT 100 폴링과
 *     결합되면 head-of-line blocking을 일으킬 수 있어, 실패마다
 *     {@code CoachingEventOutbox.scheduleNextAttempt()}로 지수 백오프 시각을 기록한다.
 *     Relay 조회(`findPollableByStatus`)는 이 시각이 지나지 않은 행을 제외한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CoachingEventOutboxPersistenceService {

    private static final int MAX_RELAY_ATTEMPTS = 5;

    private final CoachingEventOutboxRepository coachingEventOutboxRepository;

    // Kafka 발행 성공 후 호출 — Outbox를 COMPLETED로 표시.
    //
    // Facade가 relay() 루프 시작 시점에 조회해뒀던 outbox 객체를 그대로 넘기지 않고 id로
    // 다시 조회한다 — 그 시점 이후 다른 Relay 인스턴스(또는 이전 폴링 배치의 뒤늦은 재시도)가
    // 이미 이 Outbox를 COMPLETED/FAILED로 종료했을 수 있는데, 오래된 in-memory 객체를 그대로
    // save()하면 merge 과정에서 이미 반영된 최신 상태를 오래된 값으로 덮어쓴다(PR #123 심층
    // 재검토, 2026-09-09).
    @Transactional
    public void markPublished(UUID outboxId) {
        CoachingEventOutbox freshOutbox = coachingEventOutboxRepository.findById(outboxId)
                .orElseThrow(() -> new IllegalStateException(
                        "방금 발행 처리하던 Outbox를 다시 찾을 수 없습니다. outboxId=" + outboxId));

        if (freshOutbox.getStatus() != OutboxStatus.PENDING) {
            return; // 다른 Relay 실행이 이미 종료 처리한 Outbox — 멱등하게 무시
        }

        freshOutbox.incrementAttemptCount();
        freshOutbox.markPublished();
        try {
            coachingEventOutboxRepository.save(freshOutbox);
        } catch (ObjectOptimisticLockingFailureException e) {
            // 이 조회~저장 사이에 다른 Relay 인스턴스가 먼저 종료 처리했다 — status 체크로
            // 걸러지는 경우와 동일하게 멱등하게 무시한다(PR #123 재검토 2차).
            log.info("[Coaching] Outbox 낙관적 락 충돌 — 다른 Relay가 먼저 처리함. outboxId={}", outboxId);
        }
    }

    // Kafka 발행 자체가 실패했을 때 호출 — 이벤트가 아직 전달되지 않았으므로, 상한 소진 시
    // FAILED로 종료해도 안전하다(정말로 발행되지 않은 상태이기 때문).
    //
    // markPublished()와 같은 이유로 id로 다시 조회한 fresh entity를 쓴다 — 오래된 객체를
    // 그대로 쓰면 이미 다른 실행이 COMPLETED로 끝낸 Outbox를 FAILED로 되돌려버릴 수 있다.
    @Transactional
    public void recordFailedAttempt(UUID outboxId) {
        CoachingEventOutbox freshOutbox = coachingEventOutboxRepository.findById(outboxId)
                .orElseThrow(() -> new IllegalStateException(
                        "방금 발행 실패 처리하던 Outbox를 다시 찾을 수 없습니다. outboxId=" + outboxId));

        if (freshOutbox.getStatus() != OutboxStatus.PENDING) {
            return; // 다른 Relay 실행이 이미 종료 처리한 Outbox — 멱등하게 무시
        }

        freshOutbox.incrementAttemptCount();

        if (freshOutbox.getAttemptCount() >= MAX_RELAY_ATTEMPTS) {
            freshOutbox.markFailed();
            try {
                coachingEventOutboxRepository.save(freshOutbox);
            } catch (ObjectOptimisticLockingFailureException e) {
                log.info("[Coaching] Outbox 낙관적 락 충돌 — 다른 Relay가 먼저 처리함. outboxId={}", outboxId);
                return;
            }
            log.error("[Coaching] Outbox 재시도 상한({}) 도달 — FAILED 처리, User Service에 CoachingCompleted가 "
                            + "발행되지 않아 XP/스트릭 반영이 누락됩니다. 수동 확인 필요. outboxId={}, eventType={}",
                    MAX_RELAY_ATTEMPTS, freshOutbox.getId(), freshOutbox.getEventType());
            return;
        }
        freshOutbox.scheduleNextAttempt();
        try {
            coachingEventOutboxRepository.save(freshOutbox);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.info("[Coaching] Outbox 낙관적 락 충돌 — 다른 Relay가 먼저 처리함. outboxId={}", outboxId);
        }
    }

    // Kafka 발행은 성공했으나, markPublished()의 DB 후처리(완료 표시)가 실패했을 때 호출.
    // 전달받은 outbox 자바 객체를 쓰지 않고 id로 DB에서 다시 읽어온다(이미 메모리상
    // COMPLETED로 바뀐 객체를 그대로 재사용하면 저장될 상태가 오염된다).
    //
    // recordFailedAttempt()와 절대 같은 종료 경로를 타면 안 된다 — 이벤트는 이미 Kafka에
    // 전달됐을 수 있으므로, 상한을 두고 FAILED로 끝내버리면 "발행되지 않음"이라는 잘못된
    // 신호가 되고, 그사이 실제로는 User Service가 이 이벤트를 이미 정상 소비했을 수도 있다.
    // 그래서 이 경로는 attemptCount만 계속 늘리며 무한 재시도한다 — 재발행으로 인한 중복은
    // User Service 소비자 쪽 멱등 처리로 상쇄하는 게 이미 설계상 전제돼 있다(이슈 #89 문서).
    @Transactional
    public void recordPostPublishFailure(UUID outboxId) {
        CoachingEventOutbox freshOutbox = coachingEventOutboxRepository.findById(outboxId)
                .orElseThrow(() -> new IllegalStateException(
                        "방금 발행 처리하던 Outbox를 다시 찾을 수 없습니다. outboxId=" + outboxId));

        if (freshOutbox.getStatus() != OutboxStatus.PENDING) {
            return; // 다른 Relay 실행이 이미 COMPLETED로 끝냈다면 여기서 FAILED로 되돌리지 않는다
        }

        freshOutbox.incrementAttemptCount();
        freshOutbox.scheduleNextAttempt();
        try {
            coachingEventOutboxRepository.save(freshOutbox);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.info("[Coaching] Outbox 낙관적 락 충돌 — 다른 Relay가 먼저 처리함. outboxId={}", outboxId);
            return;
        }
        log.error("[Coaching] Kafka 발행은 성공했지만 완료 표시(DB 후처리)가 반복 실패 중입니다 — "
                        + "이벤트가 이미 전달됐을 수 있어 FAILED로 종료하지 않고 계속 재시도합니다. "
                        + "attemptCount={}, outboxId={}",
                freshOutbox.getAttemptCount(), outboxId);
    }
}
