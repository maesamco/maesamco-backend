package com.maesamco.content.infrastructure.persistence;

import com.maesamco.content.domain.entity.Curriculum;
import com.maesamco.content.domain.repository.CurriculumRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class CurriculumRepositoryImpl implements CurriculumRepository {

    private final SpringDataCurriculumRepository springDataCurriculumRepository;

    @Override
    public Curriculum save(Curriculum curriculum) {
        return springDataCurriculumRepository
                .save(curriculum);
    }

    @Override
    public Optional<Curriculum> findById(UUID curriculumId) {
        return springDataCurriculumRepository
                .findByIdAndDeletedAtIsNull(curriculumId);
    }

    @Override
    public long count() {
        return springDataCurriculumRepository
                .countByDeletedAtIsNull();
    }

    @Override
    public Page<Curriculum> searchCurriculums(Pageable pageable) {
        return springDataCurriculumRepository
                .findByDeletedAtIsNullOrderByDisplayOrderAscIdAsc(pageable);
    }
}