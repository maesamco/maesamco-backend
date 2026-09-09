package com.maesamco.judge.infrastructure.messaging.consumer;

import com.maesamco.judge.application.facade.JudgeExecutionFacade;
import com.maesamco.judge.infrastructure.messaging.event.JudgeRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class JudgeRequestedConsumer {

    private static final int SUPPORTED_EVENT_VERSION = 1;

    private final JudgeExecutionFacade judgeExecutionFacade;

    @KafkaListener(
            topics = "${spring.kafka.topic.judge-requested:judge-requested-events}",
            groupId = "${spring.kafka.consumer.group.judge-requested:judge-service-judge-requested}",
            containerFactory = "judgeRequestedKafkaListenerContainerFactory"
    )
    public void consume(JudgeRequestedEvent event) {
        if (event.eventVersion() != SUPPORTED_EVENT_VERSION) {
            log.warn("[Judge] 처리 불가능한 JudgeRequested eventVersion={} — 무시. eventId={}, submissionId={}",
                    event.eventVersion(), event.eventId(), event.submissionId());
            throw new UnsupportedJudgeRequestedEventVersionException(
                    "지원하지 않는 eventVersion=" + event.eventVersion() + ", eventId=" + event.eventId()
            );
        }
        log.info("[Judge] JudgeRequested 수신 eventId={}, submissionId={}", event.eventId(), event.submissionId());
        judgeExecutionFacade.execute(event.submissionId());
    }
    public static class UnsupportedJudgeRequestedEventVersionException extends RuntimeException {
        UnsupportedJudgeRequestedEventVersionException(String message) {
            super(message);
        }
    }
}