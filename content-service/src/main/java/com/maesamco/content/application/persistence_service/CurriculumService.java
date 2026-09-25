package com.maesamco.content.application.persistence_service;

import com.maesamco.content.application.finder.CurriculumFinder;
import com.maesamco.content.application.command.CurriculumCreateCommand;
import com.maesamco.content.application.command.CurriculumUpdateCommand;
import com.maesamco.content.application.result.CurriculumResult;
import com.maesamco.content.domain.entity.Curriculum;
import com.maesamco.content.domain.repository.CurriculumRepository;
import com.maesamco.content.global.common.pagination.PageQuery;
import com.maesamco.content.global.common.pagination.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 커리큘럼 생성, 조회, 수정, 삭제를 담당하는 서비스입니다.
 *
 * <p>Curriculum 자체에는 표시 순서 정책을 두지 않습니다.
 * Unit과 Lesson의 순서는 각 부모 범위 안에서 별도로 관리합니다.</p>
 */
@Service
@RequiredArgsConstructor
public class CurriculumService {

    private final CurriculumRepository curriculumRepository;

    private final CurriculumFinder curriculumFinder;

    /**
     * 커리큘럼을 생성합니다.
     *
     * <p>Curriculum에는 displayOrder 개념이 없으므로
     * 제목과 프로그래밍 언어만 사용하여 생성합니다.</p>
     *
     * @param command 커리큘럼 생성 값
     * @return 생성된 커리큘럼 정보
     */
    @Transactional(rollbackFor = Exception.class)
    public CurriculumResult createCurriculum(
            CurriculumCreateCommand command
    ) {
        Curriculum curriculum =
                Curriculum.create(
                        command.getTitle(),
                        command.getLanguage()
                );

        Curriculum savedCurriculum =
                curriculumRepository.save(
                        curriculum
                );

        return CurriculumResult.from(
                savedCurriculum
        );
    }

    /**
     * 커리큘럼을 단건 조회합니다.
     *
     * @param curriculumId 조회할 커리큘럼 ID
     * @return 조회된 커리큘럼 정보
     */
    @Transactional(readOnly = true)
    public CurriculumResult getCurriculum(
            UUID curriculumId
    ) {
        Curriculum curriculum =
                curriculumFinder.getById(
                        curriculumId
                );

        return CurriculumResult.from(
                curriculum
        );
    }

    /**
     * 커리큘럼 목록을 조회합니다.
     *
     * <p>Curriculum에는 별도의 표시 순서가 없으며,
     * Repository에서 결정적인 정렬 기준을 적용합니다.</p>
     *
     * @param pageQuery 페이징 정보
     * @return 페이징된 커리큘럼 목록
     */
    @Transactional(readOnly = true)
    public PageResult<CurriculumResult> searchCurriculums(
            PageQuery pageQuery
    ) {
        PageResult<Curriculum> curriculums =
                curriculumRepository.searchCurriculums(
                        pageQuery
                );

        return curriculums.map(
                CurriculumResult::from
        );
    }

    /**
     * 학습자용 커리큘럼 단건 조회입니다(#359).
     *
     * <p>공개(PUBLISHED)되지 않은 커리큘럼은 삭제된 것과 같이 CURRICULUM_NOT_FOUND로 응답합니다.</p>
     *
     * @param curriculumId 조회할 커리큘럼 ID
     * @return 조회된 커리큘럼 정보
     */
    @Transactional(readOnly = true)
    public CurriculumResult getCurriculumForUser(
            UUID curriculumId
    ) {
        Curriculum curriculum =
                curriculumFinder.getPublishedById(
                        curriculumId
                );

        return CurriculumResult.from(
                curriculum
        );
    }

    /**
     * 학습자용 커리큘럼 목록 조회입니다(#359). 공개(PUBLISHED)된 커리큘럼만 반환합니다.
     *
     * @param pageQuery 페이징 정보
     * @return 페이징된 공개 커리큘럼 목록
     */
    @Transactional(readOnly = true)
    public PageResult<CurriculumResult> searchCurriculumsForUser(
            PageQuery pageQuery
    ) {
        PageResult<Curriculum> curriculums =
                curriculumRepository.searchPublishedCurriculums(
                        pageQuery
                );

        return curriculums.map(
                CurriculumResult::from
        );
    }

    /**
     * 커리큘럼 정보를 수정합니다.
     *
     * <p>PATCH 요청에서 전달된 필드만 변경합니다.</p>
     *
     * @param curriculumId 수정할 커리큘럼 ID
     * @param command 커리큘럼 수정 값
     * @return 수정된 커리큘럼 정보
     */
    @Transactional(rollbackFor = Exception.class)
    public CurriculumResult updateCurriculum(
            UUID curriculumId,
            CurriculumUpdateCommand command
    ) {
        Curriculum curriculum =
                curriculumFinder.getById(
                        curriculumId
                );

        if (command.getLanguage() != null) {
            curriculum.changeLanguage(
                    command.getLanguage()
            );
        }

        if (command.getTitle() != null) {
            curriculum.changeTitle(
                    command.getTitle()
            );
        }

        return CurriculumResult.from(
                curriculum
        );
    }

    /**
     * 커리큘럼을 soft delete 처리합니다.
     *
     * @param curriculumId 삭제할 커리큘럼 ID
     * @param userId 삭제를 요청한 사용자 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteCurriculum(
            UUID curriculumId,
            UUID userId
    ) {
        Curriculum curriculum =
                curriculumFinder.getById(
                        curriculumId
                );

        curriculum.softDelete(
                userId
        );
    }

    /**
     * 커리큘럼을 학습자에게 공개합니다(#359). 이미 공개 상태면 그대로 둡니다.
     *
     * <p>하위 유닛·레슨의 상태는 바꾸지 않습니다. 하위도 각각 공개돼 있어야 학습자에게 보입니다.</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public CurriculumResult publishCurriculum(
            UUID curriculumId
    ) {
        Curriculum curriculum =
                curriculumFinder.getById(
                        curriculumId
                );

        curriculum.publish();

        return CurriculumResult.from(
                curriculum
        );
    }

    /**
     * 커리큘럼을 학습자 조회에서 내립니다(#359). 이미 비공개 상태면 그대로 둡니다.
     *
     * <p>하위 유닛·레슨의 상태값은 바꾸지 않고 학습자 조회 시점에 함께 숨겨지며,
     * 다시 공개하면 하위 노출이 원래대로 돌아옵니다.</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public CurriculumResult unpublishCurriculum(
            UUID curriculumId
    ) {
        Curriculum curriculum =
                curriculumFinder.getById(
                        curriculumId
                );

        curriculum.unpublish();

        return CurriculumResult.from(
                curriculum
        );
    }
}
