package com.maesamco.content.domain.repository.problem;

import com.maesamco.content.domain.entity.problem.Problem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 문제 Querydsl 동적 검색을 위한 Custom Repository입니다.
 *
 * <p>문제 검색 조건과 페이징 정보를 전달받아
 * 조건에 해당하는 문제 목록을 조회합니다.</p>
 *
 * <p>실제 구현은 Infrastructure 계층의
 * ProblemSearchRepositoryImpl에서 담당합니다.</p>
 */
public interface ProblemSearchRepository {

    /**
     * 전달된 검색 조건을 기준으로 문제 목록을 조회합니다.
     *
     * @param condition 문제 검색 조건
     * @param pageable 페이징 및 정렬 조건
     * @return 검색된 문제 페이지
     */
    Page<Problem> searchProblems(
            ProblemSearchCondition condition,
            Pageable pageable
    );
}