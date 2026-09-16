package com.maesamco.content.domain.repository;

import com.maesamco.content.domain.entity.Curriculum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface CurriculumRepository {

    Curriculum save(Curriculum curriculum);

    Optional<Curriculum> findById(UUID curriculumId);

    long count();

    Page<Curriculum> searchCurriculums(Pageable pageable);
}