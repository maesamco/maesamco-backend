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
 * ⚠️ 이 서명은 "어느 서비스가, 언제 호출했는가"만 증명하고 "어떤 요청인가"는 증명하지
 * 않는다(`HmacSignatureUtil.sign()`의 서명 대상이 `serviceName:timestamp`뿐이라 경로·
 * 파라미터를 안 덮음) — 유효한 서명 헤더 세트를 가로챈 쪽은 300초 이내에 이 서비스의
 * `/internal/v1/**` 아래 어떤 `userId`에도 그대로 재사용할 수 있다. 이 서비스에서 응답
 * 내용을 경로변수(`{userId}`)로 결정하는 첫 내부 API라 이 한계가 실질적인 의미를 갖는다 —
 * 이슈 #40(HMAC 재전송 방어가 시간 창만 검증) 참고, 이 필터 자체를 고치는 건 이 PR 범위
 * 밖이다.
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
