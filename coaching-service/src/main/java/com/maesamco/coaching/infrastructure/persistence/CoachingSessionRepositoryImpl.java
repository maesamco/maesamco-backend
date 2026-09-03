package com.maesamco.coaching.infrastructure.persistence;

import com.maesamco.coaching.domain.entity.CoachingSession;
import com.maesamco.coaching.domain.repository.CoachingSessionRepository;
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
public class CoachingSessionRepositoryImpl implements CoachingSessionRepository {

    private final SpringDataCoachingSessionRepository springDataCoachingSessionRepository;

    /**
     * saveAndFlush로 즉시 flush해서 UNIQUE(submission_id) 위반을 이 메서드 안에서
     * 바로 잡아낸다 — save()만 쓰면 실제 INSERT가 트랜잭션 커밋/다음 flush 시점까지
     * 지연될 수 있어 여기서 예외를 못 잡는다. GlobalExceptionHandler의 범용
     * DataIntegrityViolationException(400) 대신 이 케이스만 전용 409로 응답하기 위한 변환이다.
     * UNIQUE 위반이 아닌 다른 무결성 위반은 그대로 다시 던져서 GlobalExceptionHandler가
     * 처리하게 한다(PR #8 리뷰).
     */
    @Override
    public CoachingSession save(CoachingSession coachingSession) {
        try {
            return springDataCoachingSessionRepository.saveAndFlush(coachingSession);
        } catch (DataIntegrityViolationException e) {
            if (!DataIntegrityViolations.isUniqueViolation(e)) {
                throw e;
            }
            throw new BusinessException(ErrorCode.COACHING_SESSION_ALREADY_EXISTS);
        }
    }

    @Override
    public Optional<CoachingSession> findById(UUID id) {
        return springDataCoachingSessionRepository.findById(id);
    }

    @Override
    public Optional<CoachingSession> findBySubmissionId(UUID submissionId) {
        return springDataCoachingSessionRepository.findBySubmissionId(submissionId);
    }

    @Override
    public Optional<CoachingSession> findByUserIdAndProblemId(UUID userId, UUID problemId) {
        return springDataCoachingSessionRepository.findByUserIdAndProblemId(userId, problemId);
    }
}
