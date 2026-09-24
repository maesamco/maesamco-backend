package com.maesamco.content.global.util;

import com.maesamco.content.domain.common.pagination.PageQuery;
import com.maesamco.content.domain.common.pagination.SortOrder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;

class PageQueryFactoryTest {

    @Test
    @DisplayName("파라미터가 없으면 page=0, size=20, createdAt DESC 기본값을 사용한다")
    void of_nullParameters_usesDefaults() {
        PageQuery pageQuery = PageQueryFactory.of(null, null, null, null);

        assertThat(pageQuery.page()).isZero();
        assertThat(pageQuery.size()).isEqualTo(20);
        assertThat(pageQuery.sortOrders()).containsExactly(SortOrder.desc("createdAt"));
    }

    @Test
    @DisplayName("정상 파라미터는 그대로 보존한다")
    void of_validParameters_preservesValues() {
        PageQuery pageQuery = PageQueryFactory.of(2, 50, "title", "asc");

        assertThat(pageQuery.page()).isEqualTo(2);
        assertThat(pageQuery.size()).isEqualTo(50);
        assertThat(pageQuery.sortOrders()).containsExactly(SortOrder.asc("title"));
    }

    @Test
    @DisplayName("잘못된 page, size, sort, direction은 400 대신 기본값으로 대체한다")
    void of_invalidParameters_fallsBackToDefaults() {
        PageQuery pageQuery = PageQueryFactory.of(-1, 101, " ", "sideways");

        assertThat(pageQuery.page()).isZero();
        assertThat(pageQuery.size()).isEqualTo(20);
        assertThat(pageQuery.sortOrders()).containsExactly(SortOrder.desc("createdAt"));
    }

    /**
     * PageableFactory → PageQueryFactory로 바꿔도 기존 목록 API의 페이징/정렬 결과가 같아야 한다.
     */
    @ParameterizedTest(name = "page={0}, size={1}, sort={2}, direction={3}")
    @CsvSource(value = {
            "NULL, NULL, NULL, NULL",
            "0, 20, title, asc",
            "3, 100, difficulty, DESC",
            "-5, 0, '', Asc",
            "1, 101, createdAt, invalid",
            "2, 7, updatedAt, ' asc'"
    }, nullValues = "NULL")
    @DisplayName("PageQueryFactory와 PageableFactory는 같은 기본값 보정 결과를 만든다")
    void of_matchesPageableFactory(Integer page, Integer size, String sort, String direction) {
        PageQuery pageQuery = PageQueryFactory.of(page, size, sort, direction);
        Pageable pageable = PageableFactory.of(page, size, sort, direction);

        assertThat(pageQuery.page()).isEqualTo(pageable.getPageNumber());
        assertThat(pageQuery.size()).isEqualTo(pageable.getPageSize());

        Sort.Order order = pageable.getSort().toList().get(0);
        SortOrder sortOrder = pageQuery.sortOrders().get(0);
        assertThat(sortOrder.property()).isEqualTo(order.getProperty());
        assertThat(sortOrder.isAscending()).isEqualTo(order.isAscending());
    }
}
