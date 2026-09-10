package com.maesamco.coaching.presentation.internal_controller;

import com.maesamco.coaching.application.query_service.WeakConceptQueryService;
import com.maesamco.coaching.global.exception.BusinessException;
import com.maesamco.coaching.global.exception.ErrorCode;
import com.maesamco.coaching.global.response.SuccessResponse;
import com.maesamco.coaching.global.security.hmac.InternalCallHeaders;
import com.maesamco.coaching.presentation.api_controller.WeakConceptResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
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
 * **2026-09-09 정정(용현님 리뷰, PR #124)**: 이전 버전은 이 문단에서 "서명 대상이
 * `serviceName:timestamp`뿐이라 경로·파라미터를 안 덮는다"고 설명했는데, 이 브랜치가
 * develop보다 뒤쳐진 상태에서 쓴 서술이었다. develop에 이미 병합된 PR #119 이후
 * `HmacSignatureUtil`의 서명 대상은 `serviceName:method:path:normalizedQuery:bodyHash:
 * nonce:timestamp`로 확장됐고, nonce를 Redis에 기록해 동일 서명 재사용(재전송)까지
 * 막는다 — 이슈 #40이 지적한 "시간 창만 검증하고 실제 재전송 방어가 없다"는 문제는
 * PR #119로 해소됐다.
 *
 * 다만 이 필터는 "유효하게 서명된 내부 호출인가"(인증)만 확인하고 "그중 Content
 * Service만 허용할 것인가"(인가)는 별도 문제다 — 아래 `getWeakConcepts()`에서
 * `X-Internal-Service` 헤더 값을 직접 확인해서 `content-service` 외의 호출자는
 * 거부한다(같은 리뷰에서 지적, 앞으로 다른 서비스의 inbound key가 추가돼도 이 API
 * 접근 범위가 자동으로 넓어지지 않도록).
 *
 * /internal/v1/ 컨트롤러는 Swagger 노출 대상이 아니므로 ApiDocs 인터페이스 패턴(팀
 * 컨벤션 19절)을 적용하지 않는다.
 */
@RestController
@RequestMapping("/internal/v1/users/{userId}/weak-concepts")
public class WeakConceptInternalController {

    private static final String ALLOWED_CALLER_SERVICE = "content-service";

    private final WeakConceptQueryService weakConceptQueryService;

    public WeakConceptInternalController(WeakConceptQueryService weakConceptQueryService) {
        this.weakConceptQueryService = weakConceptQueryService;
    }

    @GetMapping
    public ResponseEntity<SuccessResponse<List<WeakConceptResponse>>> getWeakConcepts(
            @PathVariable UUID userId,
            @RequestHeader(InternalCallHeaders.SERVICE) String callerService
    ) {
        // HmacVerificationFilter는 "서명이 유효한 내부 호출인가"만 확인한다 — 서명 검증을
        // 통과한 어떤 내부 서비스든 이 API를 호출할 수 있다는 뜻은 아니므로, 이 API의
        // 실제 호출 대상(Content Service)인지는 여기서 별도로 확인한다.
        if (!ALLOWED_CALLER_SERVICE.equals(callerService)) {
            throw new BusinessException(ErrorCode.INTERNAL_CALLER_NOT_ALLOWED);
        }

        List<WeakConceptResponse> weakConcepts = weakConceptQueryService.getWeakConcepts(userId).stream()
                .map(WeakConceptResponse::from)
                .toList();
        return ResponseEntity.ok(SuccessResponse.success(weakConcepts));
    }
}
