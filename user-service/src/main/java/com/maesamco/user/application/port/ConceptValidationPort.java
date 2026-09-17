package com.maesamco.user.application.port;

import java.util.List;
import java.util.UUID;

/**
 * 관심 개념 ID의 유효성을 확인하는 외부 서비스 Port입니다.
 *
 * <p>구현체는 Content Service의 내부 API를 호출하여 요청된 개념이
 * 모두 존재하고 활성 상태인지 일괄 검증합니다.</p>
 */
public interface ConceptValidationPort {

    /**
     * 전달받은 모든 개념 ID가 사용 가능한지 검증합니다.
     *
     * <p>존재하지 않거나 비활성화된 개념이 포함되면
     * {@code CONCEPT_NOT_FOUND}, Content Service 호출에 실패하면
     * {@code CONTENT_SERVICE_UNAVAILABLE} 오류를 발생시킵니다.</p>
     *
     * @param conceptIds 검증할 개념 식별자 목록
     */
    void validateAll(
            List<UUID> conceptIds
    );
}
