package com.maesamco.content.application.finder_service;

import com.maesamco.content.application.input_port.CurriculumFinder;
import com.maesamco.content.domain.entity.Curriculum;
import com.maesamco.content.domain.repository.CurriculumRepository;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** 커리큘럼 조회 서비스 */
@Service
@RequiredArgsConstructor
public class CurriculumFinderService implements CurriculumFinder {

    private final CurriculumRepository curriculumRepository;

    @Override
    @Transactional(readOnly = true)
    public Curriculum getById(UUID curriculumId) {
        return curriculumRepository.findById(curriculumId)
                .orElseThrow(
                        () -> new BusinessException(ErrorCode.CURRICULUM_NOT_FOUND)
                );
    }
}