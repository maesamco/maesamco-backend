package com.maesamco.coaching.application.persistence_service;

import com.maesamco.coaching.domain.entity.CoachingEventOutbox;
import com.maesamco.coaching.domain.entity.CoachingSession;
import com.maesamco.coaching.domain.entity.FollowUpAnswer;
import com.maesamco.coaching.domain.repository.CoachingEventOutboxRepository;
import com.maesamco.coaching.domain.repository.CoachingSessionRepository;
import com.maesamco.coaching.domain.repository.FollowUpAnswerRepository;
import com.maesamco.coaching.global.exception.BusinessException;
import com.maesamco.coaching.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.UUID;

/**
 * 역질문 답변 등록(이슈 #51) — 답변 저장 + 코칭 세션 완료 + Outbox 이벤트 기록을 한
 * 트랜잭션으로 묶는 순수 DB 로직. 외부 서비스/LLM 호출이 전혀 없어 Facade가 아니라
 * PersistenceService다(팀 컨벤션 402행 — "외부 호출 없는 순수 DB 트랜잭션 격리").
 *
 * FollowUpAnswerFacade가 소유권 검증 등 읽기 전용 준비를 트랜잭션 밖에서 끝낸 뒤 이
 * 메서드를 호출한다. 팀 컨벤션 406행의 "엔티티 전달 규칙"에 따라, Facade가 이미 조회한
 * CoachingSession을 그대로 넘기지 않고 ID로 받아 이 새 트랜잭션 안에서 다시 조회한다.
 *
 * 동시성: FollowUpAnswer에 UNIQUE(follow_up_question_id) 제약이 있어, **같은** 역질문에
 * 대한 동시 요청 중 하나는 반드시 답변 저장 단계에서 UNIQUE 위반으로 이 트랜잭션 전체가
 * 롤백된다 — 세션 완료(complete())도 함께 롤백되므로 안전하다. 다만 이건 같은 역질문에
 * 한해서다 — 한 세션에 서로 다른 역질문이 여러 개 있을 수 있어서(재도전 시 새 설명이
 * 등록될 때마다 새 역질문이 쌓임, 이슈 #84), **서로 다른** 역질문 두 개를 순차적으로든
 * 동시에든 답하면 이 UNIQUE 제약만으로는 못 막는다(PR #98 자가 리뷰 반영, 용현님 P1) —
 * 아래 completeSessionIfNeeded()가 그 순차 재진입 케이스(가장 흔한 경우: 세션이 이미
 * COMPLETED인 상태에서 다른 역질문에 늦게 답하는 경우)를 별도로 처리한다. 두 요청이
 * 정말로 거의 동시에 들어와서 둘 다 세션을 IN_PROGRESS로 읽는 진짜 레이스까지 막으려면
 * CoachingSession에 낙관적 락(@Version)이 필요한데, 그건 advanceToSubmission() 쪽에도
 * 영향을 주는 더 큰 변경이라 이번엔 범위에서 뺐다(TODO로 남김).
 */
@Service
@Transactional
public class FollowUpAnswerPersistenceService {

    private static final String COACHING_COMPLETED_EVENT_TYPE = "CoachingCompleted";

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final FollowUpAnswerRepository followUpAnswerRepository;
    private final CoachingSessionRepository coachingSessionRepository;
    private final CoachingEventOutboxRepository coachingEventOutboxRepository;

    public FollowUpAnswerPersistenceService(
            FollowUpAnswerRepository followUpAnswerRepository,
            CoachingSessionRepository coachingSessionRepository,
            CoachingEventOutboxRepository coachingEventOutboxRepository
    ) {
        this.followUpAnswerRepository = followUpAnswerRepository;
        this.coachingSessionRepository = coachingSessionRepository;
        this.coachingEventOutboxRepository = coachingEventOutboxRepository;
    }

    /**
     * @param coachingSessionId 완료 처리할 세션 ID — Facade가 이미 조회했더라도 이 새
     *                          트랜잭션 안에서 다시 조회한다(팀 컨벤션 406행).
     * @param followUpQuestionId 답변 대상 역질문 ID
     * @param answerText 답변 내용
     */
    public FollowUpAnswerCompletionResult completeWithAnswer(
            UUID coachingSessionId, UUID followUpQuestionId, String answerText
    ) {
        CoachingSession session = coachingSessionRepository.findById(coachingSessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COACHING_SESSION_NOT_FOUND));

        FollowUpAnswer answer = followUpAnswerRepository.save(
                FollowUpAnswer.create(followUpQuestionId, answerText)
        );

        CoachingSession completedSession = completeSessionIfNeeded(session);

        return new FollowUpAnswerCompletionResult(answer, completedSession);
    }

    /**
     * 세션이 이미 COMPLETED면(한 세션의 다른 역질문이 먼저 완료 처리한 경우, PR #98 자가
     * 리뷰 반영) 방금 저장한 답변은 그대로 유효하게 두되, 세션 재완료·Outbox 재발행은
     * 건너뛴다 — CoachingSession.complete()를 다시 호출하면 COACHING_SESSION_ALREADY_
     * COMPLETED로 예외가 나서 이 트랜잭션 전체가 롤백되고, 방금 저장한 정당한 답변까지
     * 같이 사라지는 문제가 있었다.
     */
    private CoachingSession completeSessionIfNeeded(CoachingSession session) {
        if (session.isCompleted()) {
            return session;
        }

        session.complete();
        CoachingSession completedSession = coachingSessionRepository.save(session);

        coachingEventOutboxRepository.save(CoachingEventOutbox.create(
                completedSession.getId(),
                COACHING_COMPLETED_EVENT_TYPE,
                buildCoachingCompletedPayload(completedSession)
        ));

        return completedSession;
    }

    /**
     * 서비스 기능 요약 [2]-7절 스키마 그대로 — coachingId/userId/submissionId/problemId/
     * completedAt. weakConcepts는 2026-09-02 정정으로 제외(피드백이 best-effort라 발행
     * 시점에 아직 확정 안 됐을 수 있음).
     *
     * submissionId/problemId는 Facade가 트랜잭션 밖에서 미리 읽어둔 값이 아니라, 이
     * 트랜잭션 안에서 다시 조회한 completedSession에서 뽑는다 — problemId는
     * updatable=false라 어차피 안 바뀌지만, submissionId는 advanceToSubmission()으로
     * 갈아탈 수 있는 값이라 자칫 Facade의 낡은 스냅샷을 그대로 흘려보내면 이 트랜잭션이
     * 실제로 커밋하는 값과 어긋날 수 있다.
     */
    private JsonNode buildCoachingCompletedPayload(CoachingSession session) {
        ObjectNode payload = JSON_MAPPER.createObjectNode();
        payload.put("coachingId", session.getId().toString());
        payload.put("userId", session.getUserId().toString());
        payload.put("submissionId", session.getSubmissionId().toString());
        payload.put("problemId", session.getProblemId().toString());
        payload.put("completedAt", session.getCompletedAt().toString());
        return payload;
    }

    public record FollowUpAnswerCompletionResult(FollowUpAnswer followUpAnswer, CoachingSession coachingSession) {
    }
}
