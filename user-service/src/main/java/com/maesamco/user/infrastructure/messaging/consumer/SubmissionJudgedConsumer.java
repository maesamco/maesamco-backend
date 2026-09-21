package com.maesamco.user.infrastructure.messaging.consumer;

import com.maesamco.user.application.service.ApplyFirstCorrectRewardCommand;
import com.maesamco.user.application.service.ApplyFirstCorrectRewardService;
import com.maesamco.user.infrastructure.messaging.event.SubmissionJudgedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * SubmissionJudged 이벤트를 소비하여 최초 정답 XP를 반영합니다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SubmissionJudgedConsumer {

    private final ApplyFirstCorrectRewardService rewardService;

    @KafkaListener(
            topics = "${spring.kafka.topic.submission-judged:submission-judged-events}",
            groupId = "${spring.kafka.consumer.group.submission-judged:user-service-submission-judged}",
            containerFactory = "submissionJudgedKafkaListenerContainerFactory",
            autoStartup = "${spring.kafka.listener.auto-startup:true}"
    )
    public void consume(
            SubmissionJudgedEvent event,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long receivedTimestamp
    ) {
        if (!event.isCorrect()) {
            log.debug(
                    "[User] 정답이 아닌 SubmissionJudged 이벤트 — 보상 생략. submissionId={}, result={}",
                    event.submissionId(),
                    event.result()
            );
            return;
        }

        Instant judgedAt = event.judgedAt();
        if (judgedAt == null) {
            judgedAt = Instant.ofEpochMilli(receivedTimestamp);
            log.warn(
                    "[User] judgedAt이 없는 레거시 SubmissionJudged 이벤트 — Kafka timestamp 사용. submissionId={}",
                    event.submissionId()
            );
        }

        boolean applied = rewardService.apply(
                new ApplyFirstCorrectRewardCommand(
                        event.submissionId(),
                        event.userId(),
                        event.problemId(),
                        judgedAt
                )
        );

        if (applied) {
            log.info(
                    "[User] 최초 정답 보상 반영 완료. submissionId={}, userId={}, problemId={}",
                    event.submissionId(),
                    event.userId(),
                    event.problemId()
            );
            return;
        }

        log.info(
                "[User] 이미 지급한 최초 정답 보상 — 중복 생략. submissionId={}, userId={}, problemId={}",
                event.submissionId(),
                event.userId(),
                event.problemId()
        );
    }
}
