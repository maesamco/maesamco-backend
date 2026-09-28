package com.maesamco.content.infrastructure.dailyquiz.ai;

import com.maesamco.content.domain.dailyquiz.entity.DailyQuizProblemType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * AI 응답의 questionText가 프론트(QuizPrompt) 렌더링과 어긋나지 않는지 검증하는 규칙을 확인한다.
 *
 * 실제로 발견된 사례(코드 펜스 누락, 리터럴 "\n")를 검증이 정상적으로 걸러내는지 확인한다.
 */
class AiGeneratedDailyQuizQuestionValidatorTest {

    @Test
    void 코드가_없는_평문_문항은_통과한다() {
        AiGeneratedDailyQuizQuestionResponse response = shortAnswerResponse(
                "Java에서 정수를 저장하는 기본 자료형은 무엇인가요?"
        );

        assertThatCode(() -> AiGeneratedDailyQuizQuestionValidator.validate(
                response,
                DailyQuizProblemType.SHORT_ANSWER
        )).doesNotThrowAnyException();
    }

    @Test
    void 코드_펜스로_제대로_감싼_문항은_통과한다() {
        AiGeneratedDailyQuizQuestionResponse response = shortAnswerResponse(
                """
                다음 코드의 출력값은 무엇인가요?

                ```java
                System.out.println(1 + 2);
                ```
                """
        );

        assertThatCode(() -> AiGeneratedDailyQuizQuestionValidator.validate(
                response,
                DailyQuizProblemType.SHORT_ANSWER
        )).doesNotThrowAnyException();
    }

    @Test
    void 리터럴_백슬래시_n이_포함되면_거부한다() {
        AiGeneratedDailyQuizQuestionResponse response = shortAnswerResponse(
                "다음 코드의 출력값은?\\n\\npublic class Main {\\n}"
        );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> AiGeneratedDailyQuizQuestionValidator.validate(
                        response,
                        DailyQuizProblemType.SHORT_ANSWER
                ))
                .withMessageContaining("리터럴");
    }

    @Test
    void 코드_펜스가_짝이_안_맞으면_거부한다() {
        AiGeneratedDailyQuizQuestionResponse response = shortAnswerResponse(
                """
                다음 코드의 출력값은 무엇인가요?

                ```java
                System.out.println(1 + 2);
                """
        );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> AiGeneratedDailyQuizQuestionValidator.validate(
                        response,
                        DailyQuizProblemType.SHORT_ANSWER
                ))
                .withMessageContaining("펜스");
    }

    @Test
    void 코드처럼_보이는데_펜스가_없으면_거부한다() {
        AiGeneratedDailyQuizQuestionResponse response = shortAnswerResponse(
                "다음 코드의 출력값은 무엇인가요? public class Main { public static void main(String[] args) { System.out.println(3); } }"
        );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> AiGeneratedDailyQuizQuestionValidator.validate(
                        response,
                        DailyQuizProblemType.SHORT_ANSWER
                ))
                .withMessageContaining("코드 펜스");
    }

    private static AiGeneratedDailyQuizQuestionResponse shortAnswerResponse(String questionText) {
        return new AiGeneratedDailyQuizQuestionResponse(
                DailyQuizProblemType.SHORT_ANSWER,
                questionText,
                null,
                "int",
                List.of()
        );
    }
}
