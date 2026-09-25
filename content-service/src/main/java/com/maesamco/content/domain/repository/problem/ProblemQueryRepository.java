package com.maesamco.content.domain.repository.problem;

import com.maesamco.content.global.common.pagination.PageQuery;
import com.maesamco.content.global.common.pagination.PageResult;
import com.maesamco.content.domain.entity.problem.Problem;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProblemQueryRepository {

    Optional<Problem> findById(UUID problemId);

    /**
     * 검색 조건에 해당하는 문제 목록을 페이징하여 조회합니다.
     *
     * <p>Persistence Framework에 의존하지 않도록 자체 Pagination 계약({@link PageQuery}, {@link PageResult})을
     * 사용합니다(#230). 지원하지 않는 정렬 필드는 무시되며, 정렬 조건이 없으면 생성일 내림차순으로 조회합니다.</p>
     */
    PageResult<Problem> searchProblems(
            ProblemSearchCondition condition,
            PageQuery pageQuery
    );

    /**
     * 특정 레슨에 연결된, 삭제되지 않은 문제들의 ID 목록을 조회합니다(이슈 #291).
     * "이 레슨이 다루는 개념" 같은 파생값을 계산할 때 사용합니다.
     */
    List<UUID> findProblemIdsByLessonId(UUID lessonId);
}