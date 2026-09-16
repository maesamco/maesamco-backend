package com.maesamco.judge.application.query_service;

import com.maesamco.judge.application.query.SubmissionGetQuery;
import com.maesamco.judge.application.result.SubmissionExternalGetResult;
import com.maesamco.judge.application.result.SubmissionInternalGetResult;
import com.maesamco.judge.domain.entity.Submission;
import com.maesamco.judge.domain.entity.SubmissionTestResult;
import com.maesamco.judge.domain.entity.SubmissionStatus;
import com.maesamco.judge.domain.repository.SubmissionRepository;
import com.maesamco.judge.domain.repository.SubmissionTestResultRepository;
import com.maesamco.judge.global.exception.BusinessException;
import com.maesamco.judge.global.exception.ErrorCode;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubmissionQueryService {

    private final SubmissionRepository submissionRepository;
    private final SubmissionTestResultRepository submissionTestResultRepository;

    public SubmissionInternalGetResult getSubmissionForInternal(SubmissionGetQuery query) {
        Submission submission = submissionRepository.findById(query.submissionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SUBMISSION_NOT_FOUND));

        List<SubmissionTestResult> failedResults = submission.getStatus() == SubmissionStatus.COMPLETED
                ? submissionTestResultRepository.findBySubmissionIdAndPassedFalse(query.submissionId())
                : Collections.emptyList();

        return SubmissionInternalGetResult.of(submission, failedResults);
    }

    public SubmissionExternalGetResult getSubmission(UUID submissionId, UUID requesterID) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SUBMISSION_NOT_FOUND));

        if (!submission.getUserId().equals(requesterID)) {
            throw new BusinessException(ErrorCode.SUBMISSION_NOT_FOUND);
        }

        List<SubmissionTestResult> testResults = submission.getStatus() == SubmissionStatus.COMPLETED
                ? submissionTestResultRepository.findBySubmissionIdOrderByCreatedAtAscIdAsc(submissionId)
                : Collections.emptyList();

        return SubmissionExternalGetResult.of(submission, testResults);
    }
}