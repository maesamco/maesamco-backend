package com.maesamco.content.global.common.pagination;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageResultTest {

    /**
     * 기존 API 응답(PageResponse)은 Spring Data PageImpl의 계산 결과를 사용했으므로,
     * PageResult로 바꿔도 totalPages / hasNext / hasPrevious 값이 같아야 응답 스펙이 유지된다.
     */
    @ParameterizedTest(name = "page={0}, size={1}, total={2}")
    @CsvSource({
            "0, 20, 0",
            "0, 20, 1",
            "0, 20, 20",
            "0, 20, 21",
            "1, 2, 3",
            "1, 2, 4",
            "2, 2, 5",
            "5, 10, 3"
    })
    @DisplayName("totalPages, hasNext, hasPrevious 계산은 Spring Data PageImpl과 동일하다")
    void calculations_matchSpringPageImpl(int page, int size, long total) {
        List<String> content = Collections.nCopies((int) Math.max(0, Math.min(size, total - (long) page * size)), "x");

        PageImpl<String> springPage = new PageImpl<>(content, PageRequest.of(page, size), total);
        PageResult<String> pageResult = new PageResult<>(content, page, size, springPage.getTotalElements());

        assertThat(pageResult.totalPages()).isEqualTo(springPage.getTotalPages());
        assertThat(pageResult.hasNext()).isEqualTo(springPage.hasNext());
        assertThat(pageResult.hasPrevious()).isEqualTo(springPage.hasPrevious());
    }

    @Test
    @DisplayName("map은 content만 변환하고 페이징 정보는 유지한다")
    void map_keepsPagingInfo() {
        PageResult<Integer> source = new PageResult<>(List.of(1, 2), 1, 2, 5);

        PageResult<String> mapped = source.map(value -> "v" + value);

        assertThat(mapped.content()).containsExactly("v1", "v2");
        assertThat(mapped.page()).isEqualTo(1);
        assertThat(mapped.size()).isEqualTo(2);
        assertThat(mapped.totalElements()).isEqualTo(5);
        assertThat(mapped.totalPages()).isEqualTo(3);
        assertThat(mapped.hasNext()).isTrue();
    }

    @Test
    @DisplayName("empty는 요청한 page, size를 유지한 빈 결과를 만든다")
    void empty_keepsRequestedPage() {
        PageResult<String> empty = PageResult.empty(PageQuery.of(2, 10));

        assertThat(empty.content()).isEmpty();
        assertThat(empty.page()).isEqualTo(2);
        assertThat(empty.size()).isEqualTo(10);
        assertThat(empty.totalElements()).isZero();
        assertThat(empty.totalPages()).isZero();
        assertThat(empty.hasNext()).isFalse();
    }

    @Test
    @DisplayName("잘못된 페이징 값으로는 생성할 수 없다")
    void invalidValues_throw() {
        assertThatThrownBy(() -> new PageResult<>(List.of(), -1, 10, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PageResult<>(List.of(), 0, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PageResult<>(List.of(), 0, 10, -1)).isInstanceOf(IllegalArgumentException.class);
    }
}
