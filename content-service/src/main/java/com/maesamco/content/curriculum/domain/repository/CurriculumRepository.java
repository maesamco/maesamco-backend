package com.maesamco.content.curriculum.domain.repository;

import com.maesamco.content.curriculum.domain.entity.Curriculum;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CurriculumRepository extends JpaRepository<Curriculum, UUID>, CurriculumSearchRepository {

    /** 삭제되지 않은 커리큘럼 단건 조회 */
    Optional<Curriculum> findByIdAndDeletedAtIsNull(UUID curriculumId);
}