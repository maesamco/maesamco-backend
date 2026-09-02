package com.maesamco.coaching.infrastructure.persistence;

import com.maesamco.coaching.domain.entity.WeakConcept;
import com.maesamco.coaching.domain.repository.WeakConceptRepository;
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
public class WeakConceptRepositoryImpl implements WeakConceptRepository {

    private final SpringDataWeakConceptRepository springDataWeakConceptRepository;

    /**
     * saveAndFlush로 즉시 flush해서 UNIQUE(user_id, concept_tag) 위반을 이 메서드 안에서
     * 바로 잡아낸다 — 기존 Coaching 엔티티들과 동일한 패턴(PR #8/#17/#30/#31/#32/#33).
     * 이 집계 엔티티는 원래 조회 후 없으면 생성/있으면 recordOccurrence()로 갱신하는 흐름을
     * Service/Facade가 맡지만, 그 조회-생성 사이의 동시성 경합으로 두 번째 생성 시도가 그대로
     * 들어오는 경우에 대한 안전망으로 여기서도 409로 변환한다. WeakConcept은 현재 물리 FK가
     * 없어 UNIQUE 위반만 발생할 수 있지만, occurrenceCount에 대한 CHECK 제약(PR #34 리뷰,
     * 버전 번호 조율 후 추가 예정)이 나중에 들어오면 그 위반도 같은 예외로 들어오므로,
     * CoachingSessionRepositoryImpl과 동일하게 UNIQUE 위반이 아니면 그대로 다시 던진다.
     */
    @Override
    public WeakConcept save(WeakConcept weakConcept) {
        try {
            return springDataWeakConceptRepository.saveAndFlush(weakConcept);
        } catch (DataIntegrityViolationException e) {
            if (!DataIntegrityViolations.isUniqueViolation(e)) {
                throw e;
            }
            throw new BusinessException(ErrorCode.WEAK_CONCEPT_ALREADY_EXISTS);
        }
    }

    /**
     * WeakConcept 생성자가 conceptTag를 trim해서 저장하므로(PR #34 리뷰), 조회도 동일하게
     * trim해서 전달한다 — 안 그러면 앞뒤 공백이 붙은 태그로 조회했을 때 저장된 행을 못 찾는다.
     */
    @Override
    public Optional<WeakConcept> findByUserIdAndConceptTag(UUID userId, String conceptTag) {
        String trimmedConceptTag = conceptTag == null ? null : conceptTag.trim();
        return springDataWeakConceptRepository.findByUserIdAndConceptTag(userId, trimmedConceptTag);
    }
}
