package com.maesamco.content.domain.common.pagination;

import java.util.List;
import java.util.function.Function;

/**
 * 목록 조회의 페이징 결과 계약입니다.
 *
 * <p>Domain Repository 계약이 Spring Data의 {@code Page}에 의존하지 않도록 정의한 자체 타입입니다.
 * totalPages / hasNext 계산 규칙은 기존 API 응답과 동일하도록 Spring Data {@code PageImpl}과 같게 맞춥니다.</p>
 *
 * @param content 현재 페이지의 데이터
 * @param page 0부터 시작하는 페이지 번호
 * @param size 요청한 페이지 크기
 * @param totalElements 전체 데이터 개수
 */
public record PageResult<T>(
        List<T> content,
        int page,
        int size,
        long totalElements
) {

    public PageResult {
        if (page < 0) {
            throw new IllegalArgumentException("페이지 번호는 0 이상이어야 합니다.");
        }
        if (size < 1) {
            throw new IllegalArgumentException("페이지 크기는 1 이상이어야 합니다.");
        }
        if (totalElements < 0) {
            throw new IllegalArgumentException("전체 데이터 개수는 0 이상이어야 합니다.");
        }
        content = content == null ? List.of() : List.copyOf(content);
    }

    public static <T> PageResult<T> empty(PageQuery pageQuery) {
        return new PageResult<>(List.of(), pageQuery.page(), pageQuery.size(), 0);
    }

    /** 전체 페이지 수입니다. 데이터가 없으면 0입니다. */
    public int totalPages() {
        return (int) Math.ceil((double) totalElements / (double) size);
    }

    /** 다음 페이지 존재 여부입니다. */
    public boolean hasNext() {
        return page + 1 < totalPages();
    }

    /** 이전 페이지 존재 여부입니다. */
    public boolean hasPrevious() {
        return page > 0;
    }

    /** 페이징 정보는 유지한 채 content만 변환합니다. */
    public <R> PageResult<R> map(Function<? super T, ? extends R> mapper) {
        List<R> mapped = content.stream()
                .<R>map(mapper)
                .toList();

        return new PageResult<>(mapped, page, size, totalElements);
    }
}
