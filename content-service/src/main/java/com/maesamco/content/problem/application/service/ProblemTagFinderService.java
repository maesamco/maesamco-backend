package com.maesamco.content.problem.application.service;

import com.maesamco.content.problem.application.port.ProblemTagFinder;
import com.maesamco.content.problem.domain.entity.ProblemTag;
import com.maesamco.content.problem.domain.repository.ProblemTagRepository;
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
    public boolean existsProblemTag(UUID problemId, UUID tagId) {
        return problemTagRepository.existsByProblemIdAndTagId(problemId, tagId);
    }

    /** 특정 문제의 문제-태그 연결 목록 조회 */
    @Override
    @Transactional(readOnly = true)
    public List<ProblemTag> getProblemTags(UUID problemId) {
        return problemTagRepository.findAllByProblemId(problemId);
    }
}