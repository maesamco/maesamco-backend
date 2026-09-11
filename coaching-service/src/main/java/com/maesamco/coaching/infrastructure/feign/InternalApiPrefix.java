package com.maesamco.coaching.infrastructure.feign;

/**
 * 모든 내부 Feign Client(@FeignClient의 path 속성)가 공유하는 prefix.
 *
 * HmacSigningFeignInterceptor도 서명 대상 경로에 같은 값을 붙여야 한다 — 왜 그래야
 * 하는지는 HmacSigningFeignInterceptor의 클래스 Javadoc 참고(이슈 #161). 두 곳이
 * 어긋나지 않도록 상수 하나로 공유한다.
 */
public final class InternalApiPrefix {

    public static final String INTERNAL_API_PREFIX = "/internal/v1";

    private InternalApiPrefix() {
    }
}
