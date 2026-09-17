package com.maesamco.content.application.dailyquiz.port;

/**
 * 완료된 Daily Quiz 결과를 외부 이벤트로 전달하기 위한 출력 포트
 *
 * Application 계층은 이벤트 전달을 요청할 뿐이며, Outbox 저장과 JSON 직렬화 같은
 * 메시징 구현은 이 포트의 Infrastructure Adapter가 담당합니다.
 */
public interface DailyQuizCompletedEventPort {

    void publish(DailyQuizCompletedEventData eventData);
}
