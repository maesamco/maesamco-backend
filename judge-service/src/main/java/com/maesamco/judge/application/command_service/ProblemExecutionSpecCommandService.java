package com.maesamco.judge.application.command_service;

import com.maesamco.judge.application.command.ProblemExecutionSpecSaveCommand;
import com.maesamco.judge.domain.entity.ProblemExecutionSpec;
import com.maesamco.judge.domain.entity.SubmissionLanguage;
import com.maesamco.judge.domain.repository.ProblemExecutionSpecRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

// 단순 CRUD 도메인(저장만 하고 이후 수정 없음)이라
// 팀 컨벤션 2절 기준대로 Command/Query로 나누지 않고 Service 하나로 둔다.
@Service
@RequiredArgsConstructor
@Slf4j

public class ProblemExecutionSpecCommandService {

    private final ProblemExecutionSpecRepository problemExecutionSpecRepository;
    private final JsonMapper jsonMapper;

    @Transactional
    public void saveIfAbsent(ProblemExecutionSpecSaveCommand command) {
        if (problemExecutionSpecRepository.existsByProblemIdAndProblemVersionId(
                command.problemId(), command.problemVersionId())) {
            log.info("[Judge] ProblemPublished 중복 소비 감지 — 스킵. eventId={}, problemId={}, problemVersionId={}",
                    command.eventId(), command.problemId(), command.problemVersionId());
            return;
        }

        ProblemExecutionSpec spec = ProblemExecutionSpec.fromPublishedEvent(
                command.problemId(),
                command.problemVersionId(),
                toSubmissionLanguage(command.language()),
                command.starterCode(),
                writeTestCasesAsJson(command),
                command.timeLimitMs(),
                command.memoryLimitMb(),
                command.publishedAt()
        );

        try {
            problemExecutionSpecRepository.saveAndFlush(spec);
        } catch (DataIntegrityViolationException e) {
            log.info("[Judge] ProblemPublished 저장 경합으로 UNIQUE 충돌 — 이미 처리된 것으로 간주. "
                            + "eventId={}, problemId={}, problemVersionId={}",
                    command.eventId(), command.problemId(), command.problemVersionId());
        }
    }

    private SubmissionLanguage toSubmissionLanguage(String language) {
        return SubmissionLanguage.valueOf(language);
    }

    private String writeTestCasesAsJson(ProblemExecutionSpecSaveCommand command) {
        try {
            return jsonMapper.writeValueAsString(command.testCases());
        } catch (JacksonException e) {
            throw new IllegalStateException("testCases 직렬화 실패. eventId=" + command.eventId(), e);
        }
    }
}