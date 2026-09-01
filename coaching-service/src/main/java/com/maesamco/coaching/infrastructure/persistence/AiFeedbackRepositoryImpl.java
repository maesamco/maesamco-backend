package com.maesamco.coaching.infrastructure.persistence;

import com.maesamco.coaching.domain.entity.AiFeedback;
import com.maesamco.coaching.domain.repository.AiFeedbackRepository;
import com.maesamco.coaching.global.exception.BusinessException;
import com.maesamco.coaching.global.exception.ErrorCode;
import com.maesamco.coaching.global.util.DataIntegrityViolations;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class AiFeedbackRepositoryImpl implements AiFeedbackRepository {

    private final SpringDataAiFeedbackRepository springDataAiFeedbackRepository;

    /**
     * saveAndFlush로 즉시 flush해서 UNIQUE(coaching_session_id) 위반을 이 메서드 안에서
     * 바로 잡아낸다 — 기존 Coaching 엔티티들과 동일한 패턴(PR #8/#17/#30/#31/#32).
     * GlobalExceptionHandler의 범용 DataIntegrityViolationException(400) 대신 이 케이스만
     * 전용 409로 응답하기 위한 변환이다. coaching_session_id는 실제 FK라 존재하지 않는
     * 부모로 저장하면 같은 예외가 나므로, UNIQUE 위반이 아니면 그대로 다시 던진다(PR #8 리뷰).
     */
    @Override
    public AiFeedback save(AiFeedback aiFeedback) {
        try {
            return springDataAiFeedbackRepository.saveAndFlush(aiFeedback);
        } catch (DataIntegrityViolationException e) {
            if (!DataIntegrityViolations.isUniqueViolation(e)) {
                throw e;
            }
            throw new BusinessException(ErrorCode.AI_FEEDBACK_ALREADY_EXISTS);
        }
    }

    @Override
    public Optional<AiFeedback> findByCoachingSessionId(UUID coachingSessionId) {
        return springDataAiFeedbackRepository.findByCoachingSessionId(coachingSessionId);
    }
}
