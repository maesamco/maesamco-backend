package com.maesamco.content.application.dailyquiz.port;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 정식 문제 풀이 이력에서 Daily Quiz 출제에 필요한 개념을 조회합니다.
 */
public interface ProblemProgressConceptPort {

    boolean existsByUserId(UUID userId);

    List<String> getWrongConceptTags(UUID userId);

    // 기준 시각 이전에 CORRECT 처리된 문제들의 개념 태그를 조회합니다.
    List<String> getCorrectConceptTagsBefore(UUID userId, Instant quizDateStart);
}
