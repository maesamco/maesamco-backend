package com.maesamco.content.infrastructure.messaging.consumer;

import com.maesamco.content.application.command_service.ProblemProgressCommandService;
import com.maesamco.content.infrastructure.messaging.event.SubmissionJudgedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubmissionJudgedKafkaConsumer {

    private final ProblemProgressCommandService problemProgressCommandService;

    @KafkaListener(
            topics = "${spring.kafka.topic.submission-judged}",
            groupId = "${spring.kafka.consumer.group.submission-judged}",
            containerFactory = "submissionJudgedKafkaListenerContainerFactory"
    )
    public void consume(SubmissionJudgedEvent event) {
        log.info(
                "[Content] SubmissionJudged 이벤트 수신. "
                        + "submissionId={}, userId={}, problemId={}, "
                        + "problemVersionId={}, attemptNo={}, status={}, result={}, judgedAt={}",
                event.submissionId(), event.userId(), event.problemId(),
                event.problemVersionId(), event.attemptNo(), event.status(),
                event.result(), event.judgedAt()
        );

        problemProgressCommandService.sync(event.toCommand());
    }
}