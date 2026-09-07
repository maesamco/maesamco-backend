package com.maesamco.content.tag.application.service;

import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import com.maesamco.content.global.response.PageResponse;
import com.maesamco.content.problem.application.port.ProblemFinder;
import com.maesamco.content.tag.application.port.TagFinder;
import com.maesamco.content.tag.domain.entity.ProblemTag;
import com.maesamco.content.tag.domain.entity.Tag;
import com.maesamco.content.tag.domain.repository.ProblemTagRepository;
import com.maesamco.content.tag.domain.repository.ProblemTagSearchRepository;
import com.maesamco.content.tag.presentation.dto.response.TagResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** 문제와 태그의 연결 등록, 조회, 제거를 담당하는 서비스 */
@Service
@RequiredArgsConstructor
public class ProblemTagService {

    private final ProblemTagRepository problemTagRepository;
    private final ProblemTagSearchRepository problemTagSearchRepository;
    private final ProblemFinder problemFinder;
    private final TagFinder tagFinder;

    /** 특정 문제의 태그 목록 조회 */
    @Transactional(readOnly = true)
    public PageResponse<TagResponse> searchProblemTags(UUID problemId, Pageable pageable) {
        problemFinder.getProblem(problemId);

        Page<Tag> tags = problemTagSearchRepository.searchTagsByProblemId(problemId, pageable);

        return PageResponse.from(tags, TagResponse::from);
    }

    /** 문제에 태그 등록 */
    @Transactional(rollbackFor = Exception.class)
    public void addTagToProblem(UUID problemId, UUID tagId) {

        problemFinder.getProblem(problemId);
        tagFinder.getTag(tagId);

        // 등록된 태그를 다시 등록할 수 없음.
        if (problemTagRepository.existsByProblemIdAndTagId(problemId, tagId)) {
            throw new BusinessException(ErrorCode.PROBLEM_TAG_ALREADY_EXISTS);
        }

        ProblemTag problemTag = ProblemTag.create(problemId, tagId);

        problemTagRepository.save(problemTag);
    }

    /** 문제에서 태그 제거 */
    @Transactional(rollbackFor = Exception.class)
    public void removeTagFromProblem(UUID problemId, UUID tagId) {

        problemFinder.getProblem(problemId);

        ProblemTag problemTag = problemTagRepository
                .findByProblemIdAndTagId(problemId, tagId)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.PROBLEM_TAG_NOT_FOUND)
                );

        problemTagRepository.delete(problemTag); // problem-tag 테이블은 Hard Delete 수행
    }
}