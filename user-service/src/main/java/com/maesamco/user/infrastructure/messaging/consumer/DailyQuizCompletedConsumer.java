package com.maesamco.user.infrastructure.messaging.consumer;

import com.maesamco.user.application.service.ApplyDailyQuizCompletedRewardCommand;
import com.maesamco.user.application.service.ApplyDailyQuizCompletedRewardService;
import com.maesamco.user.infrastructure.messaging.event.DailyQuizCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * DailyQuizCompleted 이벤트를 소비하여 XP와 학습 스트릭을 반영합니다.
 *
 * <p>Content Service는 이 Consumer보다 먼저 운영에서 이벤트를 발행하고 있습니다.
 * 이 Consumer 그룹이 처음 시작될 때 토픽에 남은 과거 완료 이벤트로 XP가 소급 지급되지
 * 않도록, 커밋된 오프셋이 없을 때는 {@code latest}부터 읽습니다(다른 Consumer의
 * 기본값 {@code earliest}와 다름). 한 번 오프셋을 커밋한 뒤에는 재시작해도 이어서
 * 읽으므로 이후 이벤트는 유실되지 않습니다.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DailyQuizCompletedConsumer {

    private final ApplyDailyQuizCompletedRewardService rewardService;

    @KafkaListener(
            topics = "${spring.kafka.topic.daily-quiz-completed:daily-quiz-completed-events}",
            groupId = "${spring.kafka.consumer.group.daily-quiz-completed:user-service-daily-quiz-completed}",
            containerFactory = "dailyQuizCompletedKafkaListenerContainerFactory",
            autoStartup = "${spring.kafka.listener.auto-startup:true}",
            properties = "auto.offset.reset=${spring.kafka.consumer.daily-quiz-completed-offset-reset:latest}"
    )
    public void consume(DailyQuizCompletedEvent event) {
        boolean applied = rewardService.apply(
                new ApplyDailyQuizCompletedRewardCommand(
                        event.quizAttemptId(),
                        event.userId(),
                        event.completedAt()
                )
        );

        if (applied) {
            log.info(
                    "[User] DailyQuizCompleted 보상 반영 완료. quizAttemptId={}, userId={}, correct={}/{}",
                    event.quizAttemptId(),
                    event.userId(),
                    event.correctCount(),
                    event.totalCount()
            );
            return;
        }

        log.info(
                "[User] 이미 처리한 DailyQuizCompleted 이벤트 — 중복 보상 생략. quizAttemptId={}, userId={}, eventId={}",
                event.quizAttemptId(),
                event.userId(),
                event.eventId()
        );
    }
}
