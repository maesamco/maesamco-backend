package com.maesamco.content.infrastructure.persistence;

import com.maesamco.content.domain.entity.TestCase;
import com.maesamco.content.domain.entity.TestCaseStatus;
import com.maesamco.content.domain.repository.TestCaseRepository;
import com.maesamco.content.global.common.pagination.PageQuery;
import com.maesamco.content.global.common.pagination.PageResult;
import com.maesamco.content.infrastructure.persistence.support.SpringPageConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class TestCaseRepositoryImpl implements TestCaseRepository {

    private final SpringDataTestCaseRepository springDataTestCaseRepository;

    @Override
    public TestCase save(TestCase testCase) {
        return springDataTestCaseRepository.save(testCase);
    }

    @Override
    public List<TestCase> saveAll(List<TestCase> testCases) {
        return springDataTestCaseRepository.saveAll(testCases);
    }

    @Override
    public Optional<TestCase> findById(UUID testCaseId) {
        return springDataTestCaseRepository
                .findByIdAndDeletedAtIsNull(testCaseId);
    }

    @Override
    public void flush() {
        springDataTestCaseRepository.flush();
    }

    @Override
    public void delete(TestCase testCase) {
        springDataTestCaseRepository.delete(testCase);
    }

    @Override
    public int findMaxTestCaseOrderByProblemIdAndIsPublic(
            UUID problemId,
            boolean isPublic
    ) {
        return springDataTestCaseRepository
                .findMaxTestCaseOrderByProblemIdAndIsPublic(
                        problemId,
                        isPublic
                );
    }

    @Override
    public PageResult<TestCase> searchTestCases(
            UUID problemId,
            boolean isPublic,
            PageQuery pageQuery
    ) {
        return SpringPageConverter.toPageResult(
                springDataTestCaseRepository
                        .findByProblemIdAndIsPublicAndTestCaseStatusOrderByTestCaseOrderAscIdAsc(
                                problemId,
                                isPublic,
                                TestCaseStatus.APPROVED,
                                SpringPageConverter.toPageable(pageQuery)
                        )
        );
    }

    @Override
    public PageResult<TestCase> searchTestCasesAll(
            UUID problemId,
            PageQuery pageQuery
    ) {
        return SpringPageConverter.toPageResult(
                springDataTestCaseRepository
                        .findByProblemIdAndTestCaseStatusOrderByIsPublicDescTestCaseOrderAscIdAsc(
                                problemId,
                                TestCaseStatus.APPROVED,
                                SpringPageConverter.toPageable(pageQuery)
                        )
        );
    }

    @Override
    public List<TestCase>
    findAllByProblemIdAndTestCaseStatusOrderByIsPublicDescTestCaseOrderAscIdAsc(
            UUID problemId,
            TestCaseStatus testCaseStatus
    ) {
        return springDataTestCaseRepository
                .findAllByProblemIdAndTestCaseStatusOrderByIsPublicDescTestCaseOrderAscIdAsc(
                        problemId,
                        testCaseStatus
                );
    }
}