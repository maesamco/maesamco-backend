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
 * 동시성: FollowUpAnswer에 UNIQUE(follow_up_question_id) 제약이 있어, 같은 역질문에
 * 대한 동시 요청 중 하나는 반드시 답변 저장 단계에서 UNIQUE 위반으로 이 트랜잭션 전체가
 * 롤백된다 — 세션 완료(complete())도 함께 롤백되므로, 별도의 낙관적 락(@Version) 없이도
 * 안전하다(CoachingSession#complete() Javadoc의 "역질문 답변 처리 Service/Facade 구현 시
 * 해결" TODO를 이 트랜잭션으로 해소한다).
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
     * @param submissionId Outbox 이벤트 payload용(서비스 기능 요약 [2]-7절 스키마)
     * @param problemId Outbux 이벤트 payload용
     */
    public FollowUpAnswerCompletionResult completeWithAnswer(
            UUID coachingSessionId, UUID followUpQuestionId, String answerText,
            UUID submissionId, UUID problemId
    ) {
        CoachingSession session = coachingSessionRepository.findById(coachingSessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COACHING_SESSION_NOT_FOUND));

        FollowUpAnswer answer = followUpAnswerRepository.save(
                FollowUpAnswer.create(followUpQuestionId, answerText)
        );

        session.complete();
        CoachingSession completedSession = coachingSessionRepository.save(session);

        coachingEventOutboxRepository.save(CoachingEventOutbox.create(
                completedSession.getId(),
                COACHING_COMPLETED_EVENT_TYPE,
                buildCoachingCompletedPayload(completedSession, submissionId, problemId)
        ));

        return new FollowUpAnswerCompletionResult(answer, completedSession);
    }

    /**
     * 서비스 기능 요약 [2]-7절 스키마 그대로 — coachingId/userId/submissionId/problemId/
     * completedAt. weakConcepts는 2026-09-02 정정으로 제외(피드백이 best-effort라 발행
     * 시점에 아직 확정 안 됐을 수 있음).
     */
    private JsonNode buildCoachingCompletedPayload(CoachingSession session, UUID submissionId, UUID problemId) {
        ObjectNode payload = JSON_MAPPER.createObjectNode();
        payload.put("coachingId", session.getId().toString());
        payload.put("userId", session.getUserId().toString());
        payload.put("submissionId", submissionId.toString());
        payload.put("problemId", problemId.toString());
        payload.put("completedAt", session.getCompletedAt().toString());
        return payload;
    }

    public record FollowUpAnswerCompletionResult(FollowUpAnswer followUpAnswer, CoachingSession coachingSession) {
    }
}
