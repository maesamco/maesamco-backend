package com.maesamco.coaching.application.persistence_service;

import com.maesamco.coaching.domain.repository.WeakConceptRepository;
import com.maesamco.coaching.global.util.Validate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

/**
 * 취약 개념 집계(WeakConcept) 갱신 — AI 종합 피드백(FeedbackPersistenceService, AI가 판단한
 * weakConcepts 태그)과 힌트 생성(HintGenerationFacade, 이슈 #62 — attemptNo>=8로
 * skipAvailable일 때 Content Service의 문제 개념 태그)이 서로 다른 출처의 태그로 같은
 * 원자적 upsert 갱신 로직을 필요로 해서 공통으로 뺐다.
 *
 * PR #166 리뷰(용현님 P1) 대응 — 원래는 조회 후 없으면 생성/있으면 recordOccurrence()로
 * 갱신하는 find-then-branch-then-save 방식이었는데, 최초 생성 시의 동시성 경합으로 UNIQUE
 * 위반이 나면 PostgreSQL이 그 트랜잭션 전체를 abort 상태로 만들어서 catch 블록의 재조회조차
 * 실패했다(같은 트랜잭션 안에서는 abort 이후 어떤 명령도 성공할 수 없음). 이 메서드는
 * FeedbackPersistenceService의 한 트랜잭션(AiCallHistory+AiFeedback+WeakConcept 원자성,
 * PR #98) 안에서 호출되므로, 여기서 트랜잭션이 abort되면 이미 성공한 LLM 피드백 저장까지
 * 함께 날아간다 — 단순 집계 경합치고는 손실이 너무 크다. WeakConceptRepository.
 * recordOccurrence()가 DB의 원자적 upsert(INSERT ... ON CONFLICT DO UPDATE)로 최초
 * 생성과 재발견 갱신을 한 문장에서 처리하므로, 애초에 이 예외 자체가 발생하지 않는다.
 */
@Service
@Transactional
public class WeakConceptPersistenceService {

    private final WeakConceptRepository weakConceptRepository;

    public WeakConceptPersistenceService(WeakConceptRepository weakConceptRepository) {
        this.weakConceptRepository = weakConceptRepository;
    }

    /**
     * WeakConcept.create() 생성자를 거치지 않고 바로 upsert하므로, 그 생성자가 하던
     * 길이 검증(Validate.requireText(conceptTag, 50, ...))을 여기서 대신 수행한다 — 안
     * 그러면 50자 초과 태그가 DB의 VARCHAR(50) 제약에 그대로 걸려 BusinessException이
     * 아닌 원본 DataIntegrityViolationException이 새어나간다.
     */
    public void recordOccurrences(UUID userId, Collection<String> conceptTags) {
        Instant detectedAt = Instant.now();
        for (String conceptTag : conceptTags) {
            if (conceptTag == null || conceptTag.isBlank()) {
                continue;
            }
            String validatedTag = Validate.requireText(conceptTag, 50, "개념 태그");
            weakConceptRepository.recordOccurrence(userId, validatedTag, detectedAt);
        }
    }
}
