package com.maesamco.content.problem.domain.repository;

import com.maesamco.content.problem.domain.entity.TestCase;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * 문제 테스트케이스의 저장과 조회를 담당하는 JPA Repository입니다.
 */
public interface TestCaseRepository
        extends JpaRepository<TestCase, UUID> {

    /**
     * 특정 문제의 테스트케이스를 표시 순서대로 조회합니다.
     *
     * <p>TestCase가 BaseEntity를 상속하므로
     * {@code @SQLRestriction("deleted_at IS NULL")}에 의해
     * 소프트 삭제된 테스트케이스는 자동으로 제외됩니다.</p>
     *
     * @param problemId 문제 식별자
     * @return displayOrder 오름차순으로 정렬된 테스트케이스 목록
     */
    @NonNull
    List<TestCase> findAllByProblemIdOrderByDisplayOrderAsc(
            @NonNull UUID problemId
    );
}
