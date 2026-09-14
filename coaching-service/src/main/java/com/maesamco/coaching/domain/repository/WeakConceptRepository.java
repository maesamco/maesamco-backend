package com.maesamco.coaching.domain.repository;

import com.maesamco.coaching.domain.entity.WeakConcept;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WeakConceptRepository {

    WeakConcept save(WeakConcept weakConcept);

    Optional<WeakConcept> findByUserIdAndConceptTag(UUID userId, String conceptTag);

    /**
     * PR #166 리뷰(용현님 P1) 대응 — (userId, conceptTag) 최초 발견 시의 동시 생성 경합을
     * 원자적 upsert(INSERT ... ON CONFLICT DO UPDATE) 한 문장으로 해소한다. 기존
     * find-then-branch-then-save 방식은 UNIQUE 위반이 나면 PostgreSQL이 그 트랜잭션을
     * abort 상태로 만들어서, catch 블록의 재조회조차 실패하는 문제가 있었다(같은
     * 트랜잭션 안에서는 abort 이후 어떤 명령도 성공할 수 없음). 동시에 이미 있는 행에
     * 대한 갱신(occurrence_count 증가)도 이 한 문장 안에서 원자적으로 처리되므로,
     * WeakConcept.recordOccurrence()의 read-then-write 방식이 갖고 있던 lost-update
     * 위험(같은 개념이 거의 동시에 재발견되는 경우)도 함께 해소된다.
     */
    void recordOccurrence(UUID userId, String conceptTag, Instant detectedAt);

    /**
     * 이슈 #54 — 사용자의 취약 개념 목록을 우선순위(코칭 서비스 API 명세 7번 API — 개선
     * 안 된 것 우선, 그다음 발견 횟수 높은 순) 그대로 조회한다.
     *
     * {@code improved}/{@code occurrenceCount}가 둘 다 같은 행이 여러 개 있으면 그 사이의
     * 상대 순서를 DB가 보장하지 않아서(PR #124 리뷰, 용현님) {@code lastDetectedAt} 내림차순을
     * 최종 tie-breaker로 추가했다 — 같은 요청을 반복해도 동순위 항목의 순서가 흔들리지 않는다.
     */
    List<WeakConcept> findByUserIdOrderByImprovedAscOccurrenceCountDescLastDetectedAtDesc(UUID userId);
}
