package com.maesamco.content.curriculum.application.port;

import com.maesamco.content.curriculum.domain.entity.Curriculum;

import java.util.UUID;

/** 커리큘럼 조회 기능을 정의합니다. */
public interface CurriculumFinder {

    /** ID로 커리큘럼을 조회합니다. */
    Curriculum findById(UUID curriculumId);
}