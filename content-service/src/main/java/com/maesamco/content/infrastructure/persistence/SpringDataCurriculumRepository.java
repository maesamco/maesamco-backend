package com.maesamco.content.infrastructure.persistence;

import com.maesamco.content.domain.entity.Curriculum;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface SpringDataCurriculumRepository
        extends JpaRepository<Curriculum, UUID> {

    /** 삭제되지 않은 커리큘럼 단건 조회 */
    Optional<Curriculum> findByIdAndDeletedAtIsNull(
            UUID curriculumId
    );

    /** 커리큘럼을 비관적 쓰기 잠금 상태로 조회 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select curriculum
            from Curriculum curriculum
            where curriculum.id = :curriculumId
              and curriculum.deletedAt is null
            """)
    Optional<Curriculum> findByIdForUpdate(
            @Param("curriculumId")
            UUID curriculumId
    );

    /** 삭제되지 않은 커리큘럼 목록을 ID 오름차순으로 조회 */
    Page<Curriculum> findByDeletedAtIsNullOrderByIdAsc(
            Pageable pageable
    );
}
