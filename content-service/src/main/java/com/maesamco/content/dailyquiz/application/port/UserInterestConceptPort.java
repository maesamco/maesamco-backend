package com.maesamco.content.dailyquiz.application.port;

import java.util.List;
import java.util.UUID;

/**
 * 풀이 이력이 없는 사용자의 관심 개념을 User Service에서 조회하는 포트
 */
public interface UserInterestConceptPort {
    List<UUID> getInterestConceptIds(UUID userId);
}
