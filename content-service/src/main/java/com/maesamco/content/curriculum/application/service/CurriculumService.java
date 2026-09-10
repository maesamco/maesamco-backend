package com.maesamco.content.curriculum.application.service;

import com.maesamco.content.curriculum.application.port.CurriculumFinder;
import com.maesamco.content.curriculum.domain.entity.Curriculum;
import com.maesamco.content.curriculum.domain.repository.CurriculumRepository;
import com.maesamco.content.curriculum.presentation.dto.request.CurriculumCreateRequest;
import com.maesamco.content.curriculum.presentation.dto.request.CurriculumUpdateRequest;
import com.maesamco.content.curriculum.presentation.dto.response.CurriculumCreateResponse;
import com.maesamco.content.curriculum.presentation.dto.response.CurriculumResponse;
import com.maesamco.content.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** 커리큘럼 생성, 조회, 수정, 삭제를 담당하는 서비스 */
@Service
@RequiredArgsConstructor
public class CurriculumService {

    private final CurriculumRepository curriculumRepository;
    private final CurriculumFinder curriculumFinder;

    /** 커리큘럼 생성 */
    @Transactional(rollbackFor = Exception.class)
    public CurriculumCreateResponse createCurriculum(CurriculumCreateRequest request) {

        int displayOrder = Math.toIntExact(curriculumRepository.count() + 1);

        Curriculum curriculum = Curriculum.create(
                request.getTitle(),
                request.getLanguage(),
                displayOrder
        );

        Curriculum savedCurriculum = curriculumRepository.save(curriculum);

        return CurriculumCreateResponse.from(savedCurriculum);
    }

    /** 커리큘럼 단건 조회 */
    @Transactional(readOnly = true)
    public CurriculumResponse getCurriculum(UUID curriculumId) {

        Curriculum curriculum = curriculumFinder.findById(curriculumId);

        return CurriculumResponse.from(curriculum);
    }

    /** 커리큘럼 목록 조회 */
    @Transactional(readOnly = true)
    public PageResponse<CurriculumResponse> searchCurriculums(Pageable pageable) {

        Page<Curriculum> curriculums = curriculumRepository.searchCurriculums(pageable);

        return PageResponse.from(curriculums, CurriculumResponse::from);
    }

    /** 커리큘럼 수정 */
    @Transactional(rollbackFor = Exception.class)
    public CurriculumResponse updateCurriculum(UUID curriculumId, CurriculumUpdateRequest request) {

        Curriculum curriculum = curriculumFinder.findById(curriculumId);

        if (request.getLanguage() != null) { curriculum.changeLanguage(request.getLanguage()); }
        if (request.getTitle() != null) { curriculum.changeTitle(request.getTitle()); }
        // 일단 현재 정책으로는 display_order를 수정하지 못하도록 한다.

        return CurriculumResponse.from(curriculum);
    }

    /** 커리큘럼 삭제 */
    @Transactional(rollbackFor = Exception.class)
    public void deleteCurriculum(UUID curriculumId, UUID userId) {

        Curriculum curriculum = curriculumFinder.findById(curriculumId);

        curriculum.softDelete(userId);
    }
}