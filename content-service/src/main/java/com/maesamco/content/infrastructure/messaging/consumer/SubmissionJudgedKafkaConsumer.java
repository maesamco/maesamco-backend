package com.maesamco.content.infrastructure.messaging.consumer;

import com.maesamco.content.application.persistence_service.ProblemProgressService;
import com.maesamco.content.infrastructure.messaging.event.SubmissionJudgedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubmissionJudgedKafkaConsumer {

    private final ProblemProgressService problemProgressService;
    private final JsonMapper jsonMapper;

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
                event.submissionId(), event.userId(), event.problemId(),
                event.problemVersionId(), event.attemptNo(), event.status(),
                event.result(), event.judgedAt()
        );

        /* 복구하거나 보상하지 않고 예외를 그대로 위로 올려서, Kafka가 같은 메시지를 다시 처리하게 한다. */
        problemProgressService.sync(
                event.toCommand()
        );
    }

    private SubmissionJudgedEvent readEvent(String payload) {
        try {
            return jsonMapper.readValue(
                    payload,
                    SubmissionJudgedEvent.class
            );
        } catch (JacksonException e) {
            // 원인을 유지하면서 Consumer 실패
            throw new IllegalArgumentException(
                    "SubmissionJudged 이벤트 역직렬화에 실패했습니다.",
                    e
            );
        }
    }
}