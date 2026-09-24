package com.maesamco.content.global.common.pagination;

/**
 * 정렬 방향입니다.
 *
 * <p>Spring Data의 {@code Sort.Direction}에 의존하지 않기 위한 자체 타입이며,
 * Infrastructure 계층에서 Persistence Framework의 정렬 방향으로 변환합니다.</p>
 */
public enum SortDirection {

    ASC,
    DESC
}
