package com.maesamco.content.domain.repository;

import com.maesamco.content.domain.entity.Curriculum;
import com.maesamco.content.global.common.pagination.PageQuery;
import com.maesamco.content.global.common.pagination.PageResult;

import java.util.Optional;
import java.util.UUID;

public interface CurriculumRepository {

    Curriculum save(
            Curriculum curriculum
    );

    Optional<Curriculum> findById(
            UUID curriculumId
    );

    Optional<Curriculum> findByIdForUpdate(
            UUID curriculumId
    );

    PageResult<Curriculum> searchCurriculums(
            PageQuery pageQuery
    );

    /** 학습자에게 공개된(PUBLISHED) 커리큘럼 목록을 조회합니다(#359). */
    PageResult<Curriculum> searchPublishedCurriculums(
            PageQuery pageQuery
    );

    /**
     * 영속성 컨텍스트에 올라온 Curriculum을 DB의 최신 상태로 다시 읽습니다(#366 리뷰 P2).
     * 부모 락을 잡은 뒤 호출해, 락을 기다리는 사이 다른 트랜잭션이 커밋한 변경을 덮어쓰지 않도록 합니다.
     * 그 사이 삭제(soft delete)됐으면 CURRICULUM_NOT_FOUND를 던집니다.
     */
    void refresh(Curriculum curriculum);
}
