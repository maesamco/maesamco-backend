package com.maesamco.content.curriculum.domain.repository;

import com.maesamco.content.curriculum.domain.entity.Curriculum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CurriculumSearchRepository {

    /** 삭제되지 않은 커리큘럼 목록 페이지 조회 */
    Page<Curriculum> searchCurriculums(Pageable pageable);
}