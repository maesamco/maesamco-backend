package com.maesamco.judge.domain.repository;

import com.maesamco.judge.domain.entity.SubmissionTestResult;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubmissionTestResultRepository extends JpaRepository<SubmissionTestResult, UUID> {

    List<SubmissionTestResult> findBySubmissionIdAndPassedFalse(UUID submissionId);
}