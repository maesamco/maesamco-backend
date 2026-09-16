package com.maesamco.content.application.service.finder;

import com.maesamco.content.application.input_port.ProblemTagFinder;
import com.maesamco.content.domain.entity.problem.ProblemTag;
import com.maesamco.content.domain.repository.problem.ProblemTagRepository;
import com.maesamco.content.domain.entity.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** 문제-태그 연결 조회 기능을 구현하는 서비스 */
@Service
@RequiredArgsConstructor
public class ProblemTagFinderService implements ProblemTagFinder {

    private final ProblemTagRepository problemTagRepository;

    /** 문제-태그 연결 존재 여부 조회 */
    @Override
    @Transactional(readOnly = true)
    public boolean existsByProblemIdAndTagId(UUID problemId, UUID tagId) {
        return problemTagRepository.existsByProblemIdAndTagId(problemId, tagId);
    }

    /** 특정 문제의 문제-태그 연결 목록 조회 */
    @Override
    @Transactional(readOnly = true)
    public List<ProblemTag> getByProblemId(UUID problemId) {
        return problemTagRepository.findAllByProblemId(problemId);
    }

    /** 특정 문제에 연결된 태그 목록을 조회한다. */
    @Override
    @Transactional(readOnly = true)
    public List<Tag> getTagsByProblemId(UUID problemId) {
        return problemTagRepository.findAllTagsByProblemId(problemId);
    }
}