package com.maesamco.content.problem.domain.repository;

import com.maesamco.content.problem.domain.entity.ProblemVersion;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * 문제 발행 버전 스냅샷의 저장과 조회를 담당하는 JPA Repository입니다.
 */
public interface ProblemVersionRepository
        extends JpaRepository<ProblemVersion, UUID> {

    @NonNull
    Optional<ProblemVersion> findById(
            @NonNull UUID id
    );
}
