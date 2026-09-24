package com.maesamco.content.global.response;

import com.maesamco.content.global.common.pagination.PageResult;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * 페이징 응답 공통 포맷. Spring Data의 Page 또는 자체 PageResult를 API 응답 규약에 맞춰 변환한다
 * (PageImpl 직렬화 형태에 그대로 의존하지 않기 위함).
 *
 * <p>두 변환 결과는 같은 필드 값을 가지므로, Page에서 PageResult로 이전해도 응답 스펙은 변하지 않는다(#230).</p>
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.hasNext()
        );
    }

    public static <T> PageResponse<T> from(PageResult<T> pageResult) {
        return new PageResponse<>(
                pageResult.content(), pageResult.page(), pageResult.size(),
                pageResult.totalElements(), pageResult.totalPages(), pageResult.hasNext()
        );
    }

    /** 자체 PageResult의 content를 응답 DTO로 변환하며 감싸야 할 때 */
    public static <S, T> PageResponse<T> from(PageResult<S> pageResult, Function<S, T> mapper) {
        return from(pageResult.map(mapper));
    }

    /** Entity Page를 DTO Page로 변환하며 감싸야 할 때 */
    public static <S, T> PageResponse<T> from(Page<S> page, Function<S, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.hasNext()
        );
    }
}
