package com.maesamco.coaching.infrastructure.persistence;

import com.maesamco.coaching.domain.entity.FollowUpAnswer;
import com.maesamco.coaching.domain.repository.FollowUpAnswerRepository;
import com.maesamco.coaching.global.exception.BusinessException;
import com.maesamco.coaching.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class FollowUpAnswerRepositoryImpl implements FollowUpAnswerRepository {

    private final SpringDataFollowUpAnswerRepository springDataFollowUpAnswerRepository;

    /**
     * saveAndFlush로 즉시 flush해서 UNIQUE(follow_up_question_id) 위반을 이 메서드 안에서
     * 바로 잡아낸다 — CoachingSessionRepositoryImpl/HintRepositoryImpl/ExplanationRepositoryImpl/
     * FollowUpQuestionRepositoryImpl과 동일한 패턴(PR #8/#17/#30/#31). GlobalExceptionHandler의
     * 범용 DataIntegrityViolationException(400) 대신 이 케이스만 전용 409로 응답하기 위한
     * 변환이다.
     */
    @Override
    public FollowUpAnswer save(FollowUpAnswer followUpAnswer) {
        try {
            return springDataFollowUpAnswerRepository.saveAndFlush(followUpAnswer);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.FOLLOW_UP_ANSWER_ALREADY_EXISTS);
        }
    }

    @Override
    public Optional<FollowUpAnswer> findByFollowUpQuestionId(UUID followUpQuestionId) {
        return springDataFollowUpAnswerRepository.findByFollowUpQuestionId(followUpQuestionId);
    }
}
