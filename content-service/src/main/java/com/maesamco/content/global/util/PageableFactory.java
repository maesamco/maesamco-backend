package com.maesamco.content.global.util;

import com.maesamco.content.global.common.pagination.PageQuery;
import com.maesamco.content.global.common.pagination.SortOrder;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * 목록 조회 API의 size/sort/direction 쿼리 파라미터가 잘못 들어와도
 * 400을 던지지 않고 기본값으로 조용히 대체한다(팀 컨벤션 10절).
 *
 * <p>기본값 보정 규칙은 {@link PageQueryFactory}에 위임합니다.
 * Problem 검색은 #230에서 {@link PageQuery}로 이전했으며, 아직 Spring Data {@link Pageable}을
 * 직접 전달하는 다른 목록 API(Tag, Unit, Lesson 등)가 이전되면 이 클래스는 제거합니다.</p>
 */
public final class PageableFactory {

    private PageableFactory() {
    }

    public static Pageable of(Integer page, Integer size, String sortProperty, String direction) {
        PageQuery pageQuery = PageQueryFactory.of(page, size, sortProperty, direction);

        return PageRequest.of(
                pageQuery.page(),
                pageQuery.size(),
                Sort.by(pageQuery.sortOrders().stream().map(PageableFactory::toOrder).toList())
        );
    }

    private static Sort.Order toOrder(SortOrder sortOrder) {
        return sortOrder.isAscending()
                ? Sort.Order.asc(sortOrder.property())
                : Sort.Order.desc(sortOrder.property());
    }
}
