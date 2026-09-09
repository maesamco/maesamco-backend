package com.maesamco.judge.application.query_service;

import com.maesamco.judge.application.query.SubmissionGetQuery;
import com.maesamco.judge.domain.entity.Submission;
import com.maesamco.judge.domain.entity.SubmissionTestResult;
import com.maesamco.judge.domain.entity.SubmissionStatus;
import com.maesamco.judge.domain.repository.SubmissionRepository;
import com.maesamco.judge.domain.repository.SubmissionTestResultRepository;
import com.maesamco.judge.global.exception.BusinessException;
import com.maesamco.judge.global.exception.ErrorCode;
import com.maesamco.judge.presentation.response.SubmissionInternalGetResponse;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubmissionQueryService {

    private final SubmissionRepository submissionRepository;
    private final SubmissionTestResultRepository submissionTestResultRepository;

    public SubmissionInternalGetResponse getSubmissionForInternal(SubmissionGetQuery query) {
        Submission submission = submissionRepository.findById(query.submissionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SUBMISSION_NOT_FOUND));

        List<SubmissionTestResult> failedResults = submission.getStatus() == SubmissionStatus.COMPLETED
                ? submissionTestResultRepository.findBySubmissionIdAndPassedFalse(query.submissionId())
                : Collections.emptyList();

        return SubmissionInternalGetResponse.of(submission, failedResults);
    }
}