package com.maesamco.content.infrastructure.persistence.support;

import com.maesamco.content.global.common.pagination.PageQuery;
import com.maesamco.content.global.common.pagination.PageResult;
import com.maesamco.content.global.common.pagination.SortOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Objects;

/**
 * 자체 Pagination 계약과 Spring Data Pagination 타입을 서로 변환합니다.
 *
 * <p>Spring Data의 {@link Pageable} / {@link Page}는 Infrastructure 계층 안에서만 사용하고,
 * Repository 구현체 밖으로는 {@link PageQuery} / {@link PageResult}만 노출합니다.</p>
 */
public final class SpringPageConverter {

    private SpringPageConverter() {
    }

    /** {@link PageQuery} → Spring {@link Pageable} */
    public static Pageable toPageable(PageQuery pageQuery) {
        Objects.requireNonNull(pageQuery, "pageQuery must not be null");

        return PageRequest.of(
                pageQuery.page(),
                pageQuery.size(),
                toSort(pageQuery)
        );
    }

    /** Spring {@link Page} → {@link PageResult} */
    public static <T> PageResult<T> toPageResult(Page<T> page) {
        Objects.requireNonNull(page, "page must not be null");

        return new PageResult<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements()
        );
    }

    private static Sort toSort(PageQuery pageQuery) {
        if (!pageQuery.isSorted()) {
            return Sort.unsorted();
        }

        return Sort.by(
                pageQuery.sortOrders()
                        .stream()
                        .map(SpringPageConverter::toOrder)
                        .toList()
        );
    }

    private static Sort.Order toOrder(SortOrder sortOrder) {
        return sortOrder.isAscending()
                ? Sort.Order.asc(sortOrder.property())
                : Sort.Order.desc(sortOrder.property());
    }
}
