package com.maesamco.judge.application.service;

import com.maesamco.judge.domain.entity.ProblemExecutionSpec;
import com.maesamco.judge.domain.entity.SubmissionLanguage;
import com.maesamco.judge.domain.repository.ProblemExecutionSpecRepository;
import com.maesamco.judge.infrastructure.messaging.event.ProblemPublishedEvent;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProblemExecutionSpecService {

    private final ProblemExecutionSpecRepository problemExecutionSpecRepository;
    private final JsonMapper jsonMapper;

    @Transactional
    public void saveIfAbsent(ProblemPublishedEvent event) {
        if (problemExecutionSpecRepository.existsByProblemIdAndProblemVersionId(
                event.problemId(), event.problemVersionId())) {
            log.info("[Judge] ProblemPublished 중복 소비 감지 — 스킵. eventId={}, problemId={}, problemVersionId={}",
                    event.eventId(), event.problemId(), event.problemVersionId());
            return;
        }

        ProblemExecutionSpec spec = ProblemExecutionSpec.fromPublishedEvent(
                event.problemId(),
                event.problemVersionId(),
                toSubmissionLanguage(event.language()),
                event.starterCode(),
                writeTestCasesAsJson(event),
                event.timeLimit(),
                event.memoryLimit(),
                event.publishedAt() != null ? event.publishedAt() : Instant.now()
        );

        try {
            problemExecutionSpecRepository.save(spec);
        } catch (DataIntegrityViolationException e) {
            log.info("[Judge] ProblemPublished 저장 경합으로 UNIQUE 충돌 — 이미 처리된 것으로 간주. "
                            + "eventId={}, problemId={}, problemVersionId={}",
                    event.eventId(), event.problemId(), event.problemVersionId());
        }
    }

    private SubmissionLanguage toSubmissionLanguage(String language) {
        return SubmissionLanguage.valueOf(language);
    }

    private String writeTestCasesAsJson(ProblemPublishedEvent event) {
        try {
            return jsonMapper.writeValueAsString(event.testCases());
        } catch (JacksonException e) {
            throw new IllegalStateException("testCases 직렬화 실패. eventId=" + event.eventId(), e);
        }
    }
}