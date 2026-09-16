package com.maesamco.content.infrastructure.persistence;

import com.maesamco.content.domain.entity.Curriculum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface SpringDataCurriculumRepository extends JpaRepository<Curriculum, UUID> {

    /** 삭제되지 않은 커리큘럼 단건 조회 */
    Optional<Curriculum> findByIdAndDeletedAtIsNull(UUID curriculumId);

    /** 삭제되지 않은 커리큘럼 개수 조회 */
    long countByDeletedAtIsNull();

    /** 삭제되지 않은 커리큘럼 목록을 displayOrder, id 오름차순으로 페이지 조회 */
    Page<Curriculum> findByDeletedAtIsNullOrderByDisplayOrderAscIdAsc(
            Pageable pageable
    );
}