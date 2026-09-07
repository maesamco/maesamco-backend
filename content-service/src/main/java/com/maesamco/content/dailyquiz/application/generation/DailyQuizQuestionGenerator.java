package com.maesamco.content.dailyquiz.application.generation;

/**
 * Daily Quiz용 문항을 개념 단위로 생성하는 AI 독립 계약입니다.
 *
 * MVP에서는 일부 문항 생성이 실패해도 나머지 문항 생성을 계속할 수 있도록
 * 개념 슬롯 하나당 문항 하나를 생성합니다. 여러 문항을 한 번에 생성하고 실패한
 * 문항만 재시도하는 최적화는 후속 단계에서 고려합니다.
 */
public interface DailyQuizQuestionGenerator {

    GeneratedDailyQuizQuestion generate(String conceptTag);
}
