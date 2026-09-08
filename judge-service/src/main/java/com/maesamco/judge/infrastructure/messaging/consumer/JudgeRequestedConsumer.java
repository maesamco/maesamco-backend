package com.maesamco.judge.infrastructure.messaging.consumer;

import com.maesamco.judge.application.command_service.JudgeExecutionCommandService;
import com.maesamco.judge.infrastructure.messaging.event.JudgeRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class JudgeRequestedConsumer {

    private final JudgeExecutionCommandService judgeExecutionCommandService;

    @KafkaListener(
            topics = "${spring.kafka.topic.judge-requested}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(JudgeRequestedEvent event) {
        log.info("[Judge] JudgeRequested 수신 eventId={}, submissionId={}", event.eventId(), event.submissionId());
        judgeExecutionCommandService.execute(event.submissionId());
    }
}