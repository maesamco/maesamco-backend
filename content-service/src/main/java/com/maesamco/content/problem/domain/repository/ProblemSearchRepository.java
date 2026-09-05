package com.maesamco.content.problem.domain.repository;

import com.maesamco.content.problem.domain.entity.Problem;
import com.maesamco.content.problem.presentation.dto.request.ProblemSearchRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 문제 Querydsl 동적 검색을 위한 Custom Repository
 * <p>searchProblems(...), 실제 구현은 ProblemRepositoryImpl에서 한다.</p>
 */
public interface ProblemSearchRepository {

    Page<Problem> searchProblems(ProblemSearchRequest request, Pageable pageable);
}