package com.maesamco.coaching.infrastructure.feign;

/**
 * 모든 내부 Feign Client(@FeignClient의 path 속성)가 공유하는 prefix.
 *
 * HmacSigningFeignInterceptor가 서명 대상 경로에도 같은 값을 붙여야 한다 — Feign은
 * 인터셉터 적용 후에야 이 prefix를 최종 URL에 합치기 때문에(SynchronousMethodHandler.
 * targetRequest()의 순서: 인터셉터 적용 → target.apply()), template.url()만으로는
 * 서명 시점에 이 prefix가 보이지 않는다. 두 곳이 어긋나지 않도록 상수 하나로 공유한다
 * (이슈 #161).
 */
public final class InternalApiPrefix {

    public static final String INTERNAL_API_PREFIX = "/internal/v1";

    private InternalApiPrefix() {
    }
}
