package com.maesamco.coaching.infrastructure.persistence;

import com.maesamco.coaching.domain.entity.WeakConcept;
import com.maesamco.coaching.domain.repository.WeakConceptRepository;
import com.maesamco.coaching.global.exception.BusinessException;
import com.maesamco.coaching.global.exception.ErrorCode;
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
     * 들어오는 경우에 대한 안전망으로 여기서도 409로 변환한다.
     */
    @Override
    public WeakConcept save(WeakConcept weakConcept) {
        try {
            return springDataWeakConceptRepository.saveAndFlush(weakConcept);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.WEAK_CONCEPT_ALREADY_EXISTS);
        }
    }

    @Override
    public Optional<WeakConcept> findByUserIdAndConceptTag(UUID userId, String conceptTag) {
        return springDataWeakConceptRepository.findByUserIdAndConceptTag(userId, conceptTag);
    }
}
