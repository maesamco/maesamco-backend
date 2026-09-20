package com.maesamco.content.infrastructure.messaging.consumer;

import com.maesamco.content.application.service.ProblemProgressService;
import com.maesamco.content.infrastructure.messaging.event.SubmissionJudgedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubmissionJudgedKafkaConsumer {

    private final ProblemProgressService problemProgressService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${spring.kafka.topic.submission-judged}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(String payload) {
        SubmissionJudgedEvent event = readEvent(payload);

        log.info(
                "[Content] SubmissionJudged 이벤트 수신. "
                        + "submissionId={}, userId={}, problemId={}, "
                        + "problemVersionId={}, attemptNo={}, status={}, result={}, judgedAt={}",
                event.submissionId(),
                event.userId(),
                event.problemId(),
                event.problemVersionId(),
                event.attemptNo(),
                event.status(),
                event.result(),
                event.judgedAt()
        );

        problemProgressService.sync(
                event.toCommand()
        );
    }

    private SubmissionJudgedEvent readEvent(String payload) {
        try {
            return objectMapper.readValue(
                    payload,
                    SubmissionJudgedEvent.class
            );
        } catch (JacksonException e) {
            throw new IllegalArgumentException(
                    "SubmissionJudged 이벤트 역직렬화에 실패했습니다.",
                    e
            );
        }
    }
}