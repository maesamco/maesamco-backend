package com.maesamco.content.global.response;

import com.maesamco.content.domain.common.pagination.PageResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageResponseTest {

    @Test
    @DisplayName("PageResult로 만든 응답은 같은 데이터의 Spring Page로 만든 응답과 동일하다 (응답 스펙 유지)")
    void fromPageResult_equalsFromSpringPage() {
        List<Integer> content = List.of(1, 2);

        PageResponse<String> fromPage =
                PageResponse.from(new PageImpl<>(content, PageRequest.of(1, 2), 5), String::valueOf);
        PageResponse<String> fromPageResult =
                PageResponse.from(new PageResult<>(content, 1, 2, 5), String::valueOf);

        assertThat(fromPageResult).isEqualTo(fromPage);
        assertThat(fromPageResult.content()).containsExactly("1", "2");
        assertThat(fromPageResult.totalPages()).isEqualTo(3);
        assertThat(fromPageResult.hasNext()).isTrue();
    }

    @Test
    @DisplayName("mapper 없이 PageResult를 그대로 응답으로 변환한다")
    void fromPageResult_withoutMapper() {
        PageResponse<String> response = PageResponse.from(new PageResult<>(List.of("a"), 0, 20, 1));

        assertThat(response).isEqualTo(new PageResponse<>(List.of("a"), 0, 20, 1, 1, false));
    }
}
