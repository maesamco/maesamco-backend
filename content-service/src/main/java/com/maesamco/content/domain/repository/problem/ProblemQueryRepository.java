package com.maesamco.content.domain.repository.problem;

import com.maesamco.content.domain.entity.problem.Problem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProblemQueryRepository {

    Optional<Problem> findById(UUID problemId);

    Page<Problem> searchProblems(
            ProblemSearchCondition condition,
            Pageable pageable
    );

    /**
     * 특정 레슨에 연결된, 삭제되지 않은 문제들의 ID 목록을 조회합니다(이슈 #291).
     * "이 레슨이 다루는 개념" 같은 파생값을 계산할 때 사용합니다.
     */
    List<UUID> findProblemIdsByLessonId(UUID lessonId);
}