package com.maesamco.content.application.testcase.service;

import com.maesamco.content.application.testcase.port.TestCaseFinder;
import com.maesamco.content.domain.testcase.entity.TestCase;
import com.maesamco.content.domain.testcase.repository.TestCaseRepository;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** 테스트케이스 조회 서비스 */
@Service
@RequiredArgsConstructor
public class TestCaseFinderService implements TestCaseFinder {

    private final TestCaseRepository testCaseRepository;

    /** 테스트케이스를 조회합니다. */
    @Override
    @Transactional(readOnly = true)
    public TestCase getTestCase(UUID testCaseId) {
        return testCaseRepository.findById(testCaseId)
                .orElseThrow(
                        () -> new BusinessException(ErrorCode.TEST_CASE_NOT_FOUND)
                );
    }
}