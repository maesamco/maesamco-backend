package com.maesamco.judge.application.command;

import com.maesamco.judge.application.exception.InvalidProblemPublishedEventException;
import com.maesamco.judge.infrastructure.messaging.event.ProblemPublishedEvent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProblemExecutionSpecSaveCommand(
        UUID eventId,
        UUID problemId,
        UUID problemVersionId,
        String language,
        String starterCode,
        List<ProblemPublishedEvent.TestCaseItem> testCases,
        int timeLimitMs,
        int memoryLimitMb,
        Instant publishedAt
) {
    public static ProblemExecutionSpecSaveCommand from(ProblemPublishedEvent event) {
        if (event.publishedAt() == null) {
            throw new InvalidProblemPublishedEventException(
                    "publishedAt 누락. eventId=" + event.eventId());
        }
        if (event.timeLimit() <=0 || event.memoryLimit() <= 0) {
            throw new InvalidProblemPublishedEventException(
                    "timeLimit/memoryLimit이 유효하지 않음. eventId=" + event.eventId()
                            + ", timeLimit=" + event.timeLimit() + ", memoryLimit=" + event.memoryLimit());
        }
        return new ProblemExecutionSpecSaveCommand(
                event.eventId(),
                event.problemId(),
                event.problemVersionId(),
                event.language(),
                event.starterCode(),
                event.testCases(),
                event.timeLimit(),
                event.memoryLimit(),
                event.publishedAt()
        );
    }
}