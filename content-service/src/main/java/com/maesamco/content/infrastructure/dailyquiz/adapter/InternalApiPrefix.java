package com.maesamco.content.infrastructure.dailyquiz.adapter;

/**
 * {@code @FeignClient(path = ...)}와 {@code HmacSigningFeignInterceptor}의 basePath가
 * 서로 다른 문자열을 쓰다가 어긋나는 걸 막기 위한 단일 출처(single source of truth).
 *
 * 이슈 #163(#161과 동일 패턴) — PR #162(coaching-service)가 먼저 도입한 설계를
 * content-service에도 동일하게 적용한다. {@code @FeignClient}와 인터셉터 양쪽에서
 * 리터럴 문자열을 각자 따로 쓰면, 나중에 둘 중 하나만 바뀌었을 때(오타 등) 또
 * 조용히 어긋나서 정상 요청이 401 나는 문제가 재발할 수 있다.
 */
public final class InternalApiPrefix {

    public static final String INTERNAL_API_PREFIX = "/internal/v1";

    private InternalApiPrefix() {
    }
}