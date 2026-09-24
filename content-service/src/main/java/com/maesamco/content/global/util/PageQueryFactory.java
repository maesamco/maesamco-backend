package com.maesamco.content.global.util;

import com.maesamco.content.global.common.pagination.PageQuery;
import com.maesamco.content.global.common.pagination.SortDirection;
import com.maesamco.content.global.common.pagination.SortOrder;

import java.util.List;
import java.util.Locale;

/**
 * 목록 조회 API의 page/size/sort/direction 쿼리 파라미터를 {@link PageQuery}로 변환합니다.
 *
 * <p>잘못된 값이 들어와도 400을 던지지 않고 기본값으로 조용히 대체합니다(팀 컨벤션 10절).
 * 기본값 규칙은 기존 {@link PageableFactory}와 동일하며, {@link PageableFactory}도 이 규칙을 그대로 사용합니다.</p>
 */
public final class PageQueryFactory {

    static final int DEFAULT_PAGE = 0;
    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 100;
    static final String DEFAULT_SORT_PROPERTY = "createdAt";
    static final SortDirection DEFAULT_DIRECTION = SortDirection.DESC;

    private PageQueryFactory() {
    }

    public static PageQuery of(Integer page, Integer size, String sortProperty, String direction) {
        int safePage = (page == null || page < 0) ? DEFAULT_PAGE : page;
        int safeSize = (size == null || size <= 0 || size > MAX_SIZE) ? DEFAULT_SIZE : size;
        String safeSortProperty = (sortProperty == null || sortProperty.isBlank())
                ? DEFAULT_SORT_PROPERTY : sortProperty;
        SortDirection safeDirection = parseDirection(direction);

        return PageQuery.of(
                safePage,
                safeSize,
                List.of(new SortOrder(safeSortProperty, safeDirection))
        );
    }

    /** "asc" / "DESC" 등 대소문자를 구분하지 않으며, 그 외 값은 기본값(DESC)으로 대체합니다. */
    private static SortDirection parseDirection(String direction) {
        if (direction == null) {
            return DEFAULT_DIRECTION;
        }
        try {
            return SortDirection.valueOf(direction.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return DEFAULT_DIRECTION;
        }
    }
}
