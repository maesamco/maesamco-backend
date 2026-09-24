package com.maesamco.content.presentation.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * 내부 서비스용 개념(Concept) 일괄 검증 요청 DTO(이슈 #309).
 *
 * <p>User Service의 {@code ConceptValidationRequest}와 계약이 동일하다
 * (이슈 #199 "Content Service 연동 계약" 섹션). User Service는 관심 개념을 최대 10개로
 * 제한하지만, 이 API는 호출자와 무관하게 {@code IN (...)} 바인드 파라미터가 한도를 넘지 않도록
 * 여유를 두고 100개로 상한을 둔다.</p>
 */
public record ConceptValidationRequest(
        @NotEmpty(message = "conceptIds는 비어 있을 수 없습니다.")
        @Size(max = 100, message = "conceptIds는 최대 100개까지 검증할 수 있습니다.")
        List<@NotNull UUID> conceptIds
) {
}
