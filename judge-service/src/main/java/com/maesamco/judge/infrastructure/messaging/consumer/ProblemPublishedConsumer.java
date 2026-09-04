package com.maesamco.judge.infrastructure.messaging.consumer;

import com.maesamco.judge.application.service.ProblemExecutionSpecService;
import com.maesamco.judge.infrastructure.messaging.event.ProblemPublishedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * ProblemPublished 토픽 컨슈머.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProblemPublishedConsumer {

    private static final int SUPPORTED_EVENT_VERSION = 1;

    private final ProblemExecutionSpecService problemExecutionSpecService;

    @KafkaListener(
            topics = "${spring.kafka.topic.problem-published:problem-published}",
            groupId = "${spring.kafka.consumer.group.problem-published:judge-service-problem-published}",
            containerFactory = "problemPublishedKafkaListenerContainerFactory"
    )
    public void consume(ProblemPublishedEvent event) {
        if (event.eventVersion() != SUPPORTED_EVENT_VERSION) {

            log.warn("[Judge] 처리 불가능한 ProblemPublished eventVersion={} — 무시. "
                            + "eventId={}, problemId={}, problemVersionId={}",
                    event.eventVersion(), event.eventId(), event.problemId(), event.problemVersionId());
            throw new UnsupportedProblemPublishedEventVersionException(
                    "지원하지 않는 eventVersion=" + event.eventVersion() + ", eventId=" + event.eventId()
            );
        }

        log.info("[Judge] ProblemPublished 수신. eventId={}, problemId={}, problemVersionId={}",
                event.eventId(), event.problemId(), event.problemVersionId());

        problemExecutionSpecService.saveIfAbsent(event);
    }

    public static class UnsupportedProblemPublishedEventVersionException extends RuntimeException {
        UnsupportedProblemPublishedEventVersionException(String message) {
            super(message);
        }
    }
}
