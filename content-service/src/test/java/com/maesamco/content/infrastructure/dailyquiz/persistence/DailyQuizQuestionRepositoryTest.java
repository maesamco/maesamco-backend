package com.maesamco.content.infrastructure.dailyquiz.persistence;

import com.maesamco.content.domain.dailyquiz.QuestionSlot;
import com.maesamco.content.domain.dailyquiz.QuestionSlots;
import com.maesamco.content.domain.dailyquiz.entity.DailyQuizProblemType;
import com.maesamco.content.domain.dailyquiz.entity.DailyQuizQuestion;
import com.maesamco.content.domain.dailyquiz.repository.DailyQuizQuestionRepository;
import com.maesamco.content.global.config.JpaAuditingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static com.maesamco.content.domain.dailyquiz.entity.DailyQuizProblemType.FILL_IN_BLANK;
import static com.maesamco.content.domain.dailyquiz.entity.DailyQuizProblemType.MULTIPLE_CHOICE;
import static com.maesamco.content.domain.dailyquiz.entity.DailyQuizProblemType.SHORT_ANSWER;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create",
        "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true",
        "spring.flyway.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({DailyQuizQuestionRepositoryImpl.class, JpaAuditingConfig.class})
@Testcontainers
class DailyQuizQuestionRepositoryTest {

    private static final String CONCEPT_TAG = "반복문";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("postgres:16-alpine")
    );

    @Autowired
    private DailyQuizQuestionRepository questionRepository;

    @Test
    void 문항_슬롯의_개념과_유형에_맞는_후보를_유형별로_조회한다() {
        saveQuestions(MULTIPLE_CHOICE, 5);
        saveQuestions(SHORT_ANSWER, 2);
        saveQuestions(FILL_IN_BLANK, 1);

        QuestionSlots questionSlots = new QuestionSlots(List.of(
                new QuestionSlot(CONCEPT_TAG, MULTIPLE_CHOICE),
                new QuestionSlot(CONCEPT_TAG, MULTIPLE_CHOICE),
                new QuestionSlot(CONCEPT_TAG, SHORT_ANSWER),
                new QuestionSlot(CONCEPT_TAG, SHORT_ANSWER),
                new QuestionSlot(CONCEPT_TAG, FILL_IN_BLANK)
        ));

        List<DailyQuizQuestion> result = questionRepository.findActiveByQuestionSlots(questionSlots);

        assertThat(result).hasSize(8);
        assertThat(result).filteredOn(question -> question.getProblemType() == MULTIPLE_CHOICE).hasSize(5);
        assertThat(result).filteredOn(question -> question.getProblemType() == SHORT_ANSWER).hasSize(2);
        assertThat(result).filteredOn(question -> question.getProblemType() == FILL_IN_BLANK).hasSize(1);
    }

    private void saveQuestions(DailyQuizProblemType problemType, int count) {
        for (int index = 1; index <= count; index++) {
            questionRepository.save(createQuestion(problemType, index));
        }
    }

    private DailyQuizQuestion createQuestion(DailyQuizProblemType problemType, int index) {
        String questionText = problemType == FILL_IN_BLANK
                ? "반복문 빈칸 문제 " + index + ": ___"
                : "반복문 문제 " + problemType + " " + index;
        List<String> choices = problemType == MULTIPLE_CHOICE
                ? List.of("정답", "오답 1", "오답 2", "오답 3")
                : null;

        return DailyQuizQuestion.createNew(
                problemType,
                questionText,
                choices,
                "정답",
                null,
                List.of(CONCEPT_TAG)
        );
    }
}
