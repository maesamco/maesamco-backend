package com.maesamco.content.domain.common.pagination;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageQueryTest {

    @Test
    @DisplayName("page, size, 정렬 조건을 보존하고 offset을 계산한다")
    void of_preservesValuesAndCalculatesOffset() {
        PageQuery pageQuery = PageQuery.of(3, 20, List.of(SortOrder.asc("title"), SortOrder.desc("createdAt")));

        assertThat(pageQuery.page()).isEqualTo(3);
        assertThat(pageQuery.size()).isEqualTo(20);
        assertThat(pageQuery.offset()).isEqualTo(60L);
        assertThat(pageQuery.isSorted()).isTrue();
        assertThat(pageQuery.sortOrders())
                .containsExactly(SortOrder.asc("title"), SortOrder.desc("createdAt"));
    }

    @Test
    @DisplayName("정렬 조건 없이 생성하면 정렬되지 않은 상태다")
    void of_withoutSort_isUnsorted() {
        PageQuery pageQuery = PageQuery.of(0, 10);

        assertThat(pageQuery.isSorted()).isFalse();
        assertThat(pageQuery.sortOrders()).isEmpty();
        assertThat(new PageQuery(0, 10, null).sortOrders()).isEmpty();
    }

    @Test
    @DisplayName("큰 page 값에서도 offset이 int 범위를 넘지 않도록 long으로 계산한다")
    void offset_doesNotOverflow() {
        PageQuery pageQuery = PageQuery.of(Integer.MAX_VALUE, 100);

        assertThat(pageQuery.offset()).isEqualTo((long) Integer.MAX_VALUE * 100);
    }

    @Test
    @DisplayName("정렬 조건 목록은 외부에서 변경할 수 없다")
    void sortOrders_areImmutable() {
        List<SortOrder> source = new ArrayList<>(List.of(SortOrder.asc("title")));
        PageQuery pageQuery = PageQuery.of(0, 10, source);

        source.add(SortOrder.desc("createdAt"));

        assertThat(pageQuery.sortOrders()).containsExactly(SortOrder.asc("title"));
        assertThatThrownBy(() -> pageQuery.sortOrders().add(SortOrder.asc("x")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("page가 음수이거나 size가 1 미만이면 생성할 수 없다")
    void invalidValues_throw() {
        assertThatThrownBy(() -> PageQuery.of(-1, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PageQuery.of(0, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("정렬 필드가 비어 있으면 SortOrder를 생성할 수 없다")
    void sortOrder_blankProperty_throws() {
        assertThatThrownBy(() -> SortOrder.asc(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SortOrder("title", null)).isInstanceOf(NullPointerException.class);
    }
}
