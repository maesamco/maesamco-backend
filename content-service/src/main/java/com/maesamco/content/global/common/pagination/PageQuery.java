package com.maesamco.content.global.common.pagination;

import java.util.List;

/**
 * 목록 조회의 페이징/정렬 요청 계약입니다.
 *
 * <p>Domain Repository 계약이 Spring Data의 {@code Pageable}에 의존하지 않도록 정의한 자체 타입입니다.
 * Presentation → Application → Domain Repository까지 이 타입으로 전달하고,
 * Persistence Framework 타입으로의 변환은 Infrastructure 계층에서만 수행합니다.</p>
 *
 * <p>요청 파라미터의 기본값 보정(잘못된 size, direction 등)은 이 타입의 책임이 아니며
 * {@code PageQueryFactory}에서 처리합니다. 이 타입은 항상 유효한 값만 가집니다.</p>
 *
 * @param page 0부터 시작하는 페이지 번호
 * @param size 페이지 크기 (1 이상)
 * @param sortOrders 우선순위 순서대로 나열한 정렬 조건 (비어 있으면 Repository 기본 정렬)
 */
public record PageQuery(
        int page,
        int size,
        List<SortOrder> sortOrders
) {

    public PageQuery {
        if (page < 0) {
            throw new IllegalArgumentException("페이지 번호는 0 이상이어야 합니다.");
        }
        if (size < 1) {
            throw new IllegalArgumentException("페이지 크기는 1 이상이어야 합니다.");
        }
        sortOrders = sortOrders == null ? List.of() : List.copyOf(sortOrders);
    }

    public static PageQuery of(int page, int size) {
        return new PageQuery(page, size, List.of());
    }

    public static PageQuery of(int page, int size, List<SortOrder> sortOrders) {
        return new PageQuery(page, size, sortOrders);
    }

    /** 조회 시작 위치입니다. */
    public long offset() {
        return (long) page * size;
    }

    public boolean isSorted() {
        return !sortOrders.isEmpty();
    }
}
