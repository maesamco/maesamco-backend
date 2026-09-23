package com.maesamco.user.infrastructure.messaging.consumer;

import com.maesamco.user.application.service.ApplyCoachingCompletedRewardCommand;
import com.maesamco.user.application.service.ApplyCoachingCompletedRewardService;
import com.maesamco.user.infrastructure.messaging.event.CoachingCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * CoachingCompleted 이벤트를 소비하여 XP와 학습 스트릭을 반영합니다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CoachingCompletedConsumer {

    private final ApplyCoachingCompletedRewardService rewardService;

    @KafkaListener(
            topics = "${spring.kafka.topic.coaching-completed:coaching-completed-events}",
            groupId = "${spring.kafka.consumer.group.coaching-completed:user-service-coaching-completed}",
            containerFactory = "coachingCompletedKafkaListenerContainerFactory",
            autoStartup = "${spring.kafka.listener.auto-startup:true}"
    )
    public void consume(CoachingCompletedEvent event) {
        boolean applied = rewardService.apply(
                new ApplyCoachingCompletedRewardCommand(
                        event.coachingId(),
                        event.userId(),
                        event.submissionId(),
                        event.problemId(),
                        event.completedAt()
                )
        );

        if (applied) {
            log.info(
                    "[User] CoachingCompleted 보상 반영 완료. coachingId={}, userId={}",
                    event.coachingId(),
                    event.userId()
            );
            return;
        }

        log.info(
                "[User] 이미 처리한 CoachingCompleted 이벤트 — 중복 보상 생략. coachingId={}, userId={}",
                event.coachingId(),
                event.userId()
        );
    }
}
