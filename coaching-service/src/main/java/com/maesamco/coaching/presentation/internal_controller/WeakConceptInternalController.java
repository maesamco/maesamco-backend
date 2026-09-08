package com.maesamco.coaching.presentation.internal_controller;

import com.maesamco.coaching.application.query_service.WeakConceptQueryService;
import com.maesamco.coaching.global.response.SuccessResponse;
import com.maesamco.coaching.presentation.api_controller.WeakConceptResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 코칭 서비스 API 명세 8번 API — Content Service가 문제 추천에 취약 개념을 반영할 수
 * 있도록 제공하는 내부 전용 API. 실제 활용(추천 반영)은 MVP 이후 확장이지만, API 자체는
 * 확장에 대비해 MVP 때 먼저 제공한다(서비스 기능 요약 [4]-5절).
 *
 * `/internal/v1/**`은 HmacVerificationFilter(HmacFilterConfig)가 이미 걸려있어, 이 필터를
 * 통과했다는 건 유효한 서비스 간 서명이 확인됐다는 뜻이다 — 컨트롤러에서 별도 인증을
 * 하지 않는다. 호출 서비스가 이미 유효성을 확인한 userId를 넘긴다고 가정하므로(같은 API
 * 명세) 존재하지 않는 userId에 대한 소유권 검증도 하지 않는다 — 그런 사용자는 그냥 빈
 * 배열을 200으로 반환한다.
 *
 * /internal/v1/ 컨트롤러는 Swagger 노출 대상이 아니므로 ApiDocs 인터페이스 패턴(팀
 * 컨벤션 19절)을 적용하지 않는다.
 */
@RestController
@RequestMapping("/internal/v1/users/{userId}/weak-concepts")
public class WeakConceptInternalController {

    private final WeakConceptQueryService weakConceptQueryService;

    public WeakConceptInternalController(WeakConceptQueryService weakConceptQueryService) {
        this.weakConceptQueryService = weakConceptQueryService;
    }

    @GetMapping
    public ResponseEntity<SuccessResponse<List<WeakConceptResponse>>> getWeakConcepts(@PathVariable UUID userId) {
        List<WeakConceptResponse> weakConcepts = weakConceptQueryService.getWeakConcepts(userId).stream()
                .map(WeakConceptResponse::from)
                .toList();
        return ResponseEntity.ok(SuccessResponse.success(weakConcepts));
    }
}
