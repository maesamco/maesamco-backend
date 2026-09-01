package com.maesamco.coaching.infrastructure.persistence;

import com.maesamco.coaching.domain.entity.FollowUpQuestion;
import com.maesamco.coaching.domain.repository.FollowUpQuestionRepository;
import com.maesamco.coaching.global.exception.BusinessException;
import com.maesamco.coaching.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class FollowUpQuestionRepositoryImpl implements FollowUpQuestionRepository {

    private final SpringDataFollowUpQuestionRepository springDataFollowUpQuestionRepository;

    /**
     * saveAndFlush로 즉시 flush해서 UNIQUE(explanation_id) 위반을 이 메서드 안에서
     * 바로 잡아낸다 — CoachingSessionRepositoryImpl/HintRepositoryImpl/ExplanationRepositoryImpl과
     * 동일한 패턴(PR #8/#17/#30). GlobalExceptionHandler의 범용 DataIntegrityViolationException
     * (400) 대신 이 케이스만 전용 409로 응답하기 위한 변환이다.
     */
    @Override
    public FollowUpQuestion save(FollowUpQuestion followUpQuestion) {
        try {
            return springDataFollowUpQuestionRepository.saveAndFlush(followUpQuestion);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.FOLLOW_UP_QUESTION_ALREADY_EXISTS);
        }
    }

    @Override
    public Optional<FollowUpQuestion> findByExplanationId(UUID explanationId) {
        return springDataFollowUpQuestionRepository.findByExplanationId(explanationId);
    }
}
