package com.maesamco.coaching.presentation.api_controller;

import com.maesamco.coaching.application.query_service.WeakConceptQueryService;
import com.maesamco.coaching.global.exception.BusinessException;
import com.maesamco.coaching.global.exception.ErrorCode;
import com.maesamco.coaching.global.response.SuccessResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Weak Concept", description = "취약 개념 조회 API")
@RestController
@RequestMapping("/api/v1/coaching/weak-concepts")
public class WeakConceptApiController implements WeakConceptApiDocs {

    private final WeakConceptQueryService weakConceptQueryService;

    public WeakConceptApiController(WeakConceptQueryService weakConceptQueryService) {
        this.weakConceptQueryService = weakConceptQueryService;
    }

    @Override
    @GetMapping
    public ResponseEntity<SuccessResponse<List<WeakConceptResponse>>> getWeakConcepts(
            @AuthenticationPrincipal UUID userId
    ) {
        requireAuthenticated(userId);
        List<WeakConceptResponse> weakConcepts = weakConceptQueryService.getWeakConcepts(userId).stream()
                .map(WeakConceptResponse::from)
                .toList();
        return ResponseEntity.ok(SuccessResponse.success(weakConcepts));
    }

    /**
     * HintApiController와 동일한 이유(PR #70 리뷰, 용현님 P1) — SecurityConfig가
     * anyRequest().permitAll()이라 여기서 명시적으로 막지 않으면 userId가 null인 채로
     * 조회가 진행돼 빈 목록(200)으로 인증 실패가 새어나갈 수 있다.
     */
    private void requireAuthenticated(UUID userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
        }
    }
}
