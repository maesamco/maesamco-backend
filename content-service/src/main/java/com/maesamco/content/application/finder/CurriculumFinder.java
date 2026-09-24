package com.maesamco.content.application.finder;

import com.maesamco.content.domain.entity.Curriculum;

import java.util.UUID;

/** 커리큘럼 조회 기능을 정의합니다. */
public interface CurriculumFinder {

    /** ID로 커리큘럼을 조회합니다. */
    Curriculum getById(
            UUID curriculumId
    );

    /** 동시성 제어가 필요한 경우 비관적 락으로 조회합니다. */
    Curriculum lockById(
            UUID curriculumId
    );
}
