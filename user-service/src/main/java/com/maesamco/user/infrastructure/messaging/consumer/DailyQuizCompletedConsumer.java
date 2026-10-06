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
 * <p>커밋된 오프셋이 없을 때(최초 기동, 오프셋 만료)는 {@code earliest}부터 읽습니다(#376).
 * 오프셋은 이벤트를 처리한 뒤에만 커밋되므로, {@code latest}였다면 첫 이벤트 처리 전에
 * 재시작될 때 시작 위치가 다시 계산되어 그 사이 이벤트를 건너뛸 수 있습니다.
 * 다시 읽은 이벤트는 {@code quizAttemptId} 기준 중복 확인으로 XP·스트릭이 한 번만
 * 반영되므로, 처음부터 읽어도 중복 지급은 없습니다.</p>
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
            properties = "auto.offset.reset=${spring.kafka.consumer.daily-quiz-completed-offset-reset:earliest}"
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
