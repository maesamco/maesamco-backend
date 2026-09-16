package com.maesamco.content.infrastructure.persistence;

import com.maesamco.content.domain.entity.TestCase;
import com.maesamco.content.domain.entity.TestCaseStatus;
import com.maesamco.content.domain.repository.TestCaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    public Optional<TestCase> findById(UUID testCaseId) {
        return springDataTestCaseRepository
                .findByIdAndDeletedAtIsNull(testCaseId);
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
    public Page<TestCase> searchTestCases(
            UUID problemId,
            boolean isPublic,
            Pageable pageable
    ) {
        return springDataTestCaseRepository
                .findByProblemIdAndIsPublicAndTestCaseStatusOrderByTestCaseOrderAscIdAsc(
                        problemId,
                        isPublic,
                        TestCaseStatus.APPROVED,
                        pageable
                );
    }

    @Override
    public Page<TestCase> searchTestCasesAll(
            UUID problemId,
            Pageable pageable
    ) {
        return springDataTestCaseRepository
                .findByProblemIdAndTestCaseStatusOrderByIsPublicDescTestCaseOrderAscIdAsc(
                        problemId,
                        TestCaseStatus.APPROVED,
                        pageable
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