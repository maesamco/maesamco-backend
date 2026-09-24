package com.maesamco.content.domain.common.pagination;

import java.util.Objects;

/**
 * 단일 정렬 조건입니다.
 *
 * <p>property는 조회 대상의 정렬 가능한 필드명이며,
 * 실제 지원 여부와 컬럼 매핑은 각 Repository 구현체가 결정합니다.</p>
 *
 * @param property 정렬 기준 필드명
 * @param direction 정렬 방향
 */
public record SortOrder(
        String property,
        SortDirection direction
) {

    public SortOrder {
        if (property == null || property.isBlank()) {
            throw new IllegalArgumentException("정렬 필드는 비어 있을 수 없습니다.");
        }
        Objects.requireNonNull(direction, "정렬 방향은 필수입니다.");
    }

    public static SortOrder asc(String property) {
        return new SortOrder(property, SortDirection.ASC);
    }

    public static SortOrder desc(String property) {
        return new SortOrder(property, SortDirection.DESC);
    }

    public boolean isAscending() {
        return direction == SortDirection.ASC;
    }
}
