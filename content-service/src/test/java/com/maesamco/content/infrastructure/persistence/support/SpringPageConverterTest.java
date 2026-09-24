package com.maesamco.content.infrastructure.persistence.support;

import com.maesamco.content.global.common.pagination.PageQuery;
import com.maesamco.content.global.common.pagination.PageResult;
import com.maesamco.content.global.common.pagination.SortOrder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpringPageConverterTest {

    @Test
    @DisplayName("PageQuery를 page, size, 다중 정렬 순서를 유지한 Pageable로 변환한다")
    void toPageable_preservesPageSizeAndSortOrder() {
        PageQuery pageQuery = PageQuery.of(2, 15, List.of(SortOrder.asc("title"), SortOrder.desc("difficulty")));

        Pageable pageable = SpringPageConverter.toPageable(pageQuery);

        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(15);
        assertThat(pageable.getOffset()).isEqualTo(pageQuery.offset());
        assertThat(pageable.getSort().toList())
                .containsExactly(Sort.Order.asc("title"), Sort.Order.desc("difficulty"));
    }

    @Test
    @DisplayName("정렬 조건이 없는 PageQuery는 unsorted Pageable로 변환한다")
    void toPageable_withoutSort_isUnsorted() {
        Pageable pageable = SpringPageConverter.toPageable(PageQuery.of(0, 20));

        assertThat(pageable.getSort().isUnsorted()).isTrue();
    }

    @Test
    @DisplayName("Spring Page를 content와 페이징 정보가 같은 PageResult로 변환한다")
    void toPageResult_preservesContentAndPagingInfo() {
        PageImpl<String> page = new PageImpl<>(List.of("a", "b"), PageRequest.of(1, 2), 5);

        PageResult<String> result = SpringPageConverter.toPageResult(page);

        assertThat(result.content()).containsExactly("a", "b");
        assertThat(result.page()).isEqualTo(page.getNumber());
        assertThat(result.size()).isEqualTo(page.getSize());
        assertThat(result.totalElements()).isEqualTo(page.getTotalElements());
        assertThat(result.totalPages()).isEqualTo(page.getTotalPages());
        assertThat(result.hasNext()).isEqualTo(page.hasNext());
    }
}
