package com.maesamco.user.global.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * 목록 조회 API의 size/sort/direction 쿼리 파라미터가 잘못 들어와도
 * 400을 던지지 않고 기본값으로 조용히 대체한다(팀 컨벤션 10절).
 */
public final class PageableFactory {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final String DEFAULT_SORT_PROPERTY = "createdAt";

    private PageableFactory() {
    }

    public static Pageable of(Integer page, Integer size, String sortProperty, String direction) {
        int safePage = (page == null || page < 0) ? DEFAULT_PAGE : page;
        int safeSize = (size == null || size <= 0 || size > MAX_SIZE) ? DEFAULT_SIZE : size;
        String safeSortProperty = (sortProperty == null || sortProperty.isBlank())
                ? DEFAULT_SORT_PROPERTY : sortProperty;
        Sort.Direction safeDirection = parseDirection(direction);

        return PageRequest.of(safePage, safeSize, Sort.by(safeDirection, safeSortProperty));
    }

    private static Sort.Direction parseDirection(String direction) {
        if (direction == null) {
            return Sort.Direction.DESC;
        }
        try {
            return Sort.Direction.fromString(direction);
        } catch (IllegalArgumentException e) {
            return Sort.Direction.DESC;
        }
    }
}
