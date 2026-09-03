package com.maesamco.content.dailyquiz.domain.entity;

import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * 일일 퀴즈 문제은행의 특정 버전 한 건을 나타냅니다.
 *
 * 같은 문제의 버전은 {@code questionGroupId}로 묶습니다.
 * 새 버전은 Repository에서 조회한 최신 버전이 FLAGGED일 때만 생성합니다.
 */
@Entity
@Table(
        name = "p_daily_quiz_questions",
        uniqueConstraints = {
                @UniqueConstraint(
                        columnNames = {"quiz_question_group_id", "version_no"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class DailyQuizQuestion {

    private static final int MAX_RESPONSE_LENGTH = 200;
    private static final int MAX_CONCEPT_TAG_LENGTH = 50;
    private static final int MULTIPLE_CHOICE_OPTION_COUNT = 4;
    private static final String FILL_IN_BLANK_MARKER = "___";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "quiz_question_group_id", nullable = false, updatable = false)
    private UUID questionGroupId;

    @Column(name = "version_no", nullable = false, updatable = false)
    private int versionNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "problem_type", nullable = false, updatable = false, length = 20)
    private DailyQuizProblemType problemType;

    @Column(name = "question_text", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String questionText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "choices", updatable = false, columnDefinition = "jsonb")
    private List<String> choices;

    @Column(name = "answer", nullable = false, updatable = false, length = MAX_RESPONSE_LENGTH)
    private String answer;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allowed_answer_variants", updatable = false, columnDefinition = "jsonb")
    private List<String> allowedAnswerVariants;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "concept_tags", nullable = false, updatable = false, columnDefinition = "jsonb")
    private List<String> conceptTags;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DailyQuizQuestionStatus status;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    private DailyQuizQuestion(
            UUID questionGroupId,
            int versionNo,
            DailyQuizProblemType problemType,
            String questionText,
            List<String> choices,
            String answer,
            List<String> allowedAnswerVariants,
            List<String> conceptTags
    ) {
        this.questionGroupId = questionGroupId;
        this.versionNo = versionNo;
        this.problemType = problemType;
        this.questionText = questionText;
        this.choices = choices;
        this.answer = answer;
        this.allowedAnswerVariants = allowedAnswerVariants;
        this.conceptTags = conceptTags;
        this.status = DailyQuizQuestionStatus.ACTIVE;
    }

    public static DailyQuizQuestion createNew(
            DailyQuizProblemType problemType,
            String questionText,
            List<String> choices,
            String answer,
            List<String> allowedAnswerVariants,
            List<String> conceptTags
    ) {
        ValidatedContent content = validateContent(
                problemType,
                questionText,
                choices,
                answer,
                allowedAnswerVariants,
                conceptTags
        );

        return new DailyQuizQuestion(
                UUID.randomUUID(),
                1,
                content.problemType(),
                content.questionText(),
                content.choices(),
                content.answer(),
                content.allowedAnswerVariants(),
                content.conceptTags()
        );
    }

    public DailyQuizQuestion createNextVersion(
            String questionText,
            List<String> choices,
            String answer,
            List<String> allowedAnswerVariants,
            List<String> conceptTags
    ) {
        validateCanCreateNextVersion();

        ValidatedContent content = validateContent(
                this.problemType,
                questionText,
                choices,
                answer,
                allowedAnswerVariants,
                conceptTags
        );

        return new DailyQuizQuestion(
                this.questionGroupId,
                this.versionNo + 1,
                content.problemType(),
                content.questionText(),
                content.choices(),
                content.answer(),
                content.allowedAnswerVariants(),
                content.conceptTags()
        );
    }

    /**
     * 현재 활성 버전에 신고가 접수되면 신규 세트 배정에서 제외합니다.
     * 이미 FLAGGED 또는 DISABLED인 과거 버전은 상태를 변경하지 않고 신고 이력만 보존합니다.
     */
    public void flag() {
        if (this.status == DailyQuizQuestionStatus.ACTIVE) {
            this.status = DailyQuizQuestionStatus.FLAGGED;
        }
    }

    public boolean isActive() {
        return this.status == DailyQuizQuestionStatus.ACTIVE;
    }

    private static ValidatedContent validateContent(
            DailyQuizProblemType problemType,
            String questionText,
            List<String> choices,
            String answer,
            List<String> allowedAnswerVariants,
            List<String> conceptTags
    ) {
        DailyQuizProblemType validatedProblemType = requireProblemType(problemType);
        String validatedQuestionText = validateQuestionText(validatedProblemType, questionText);
        String validatedAnswer = requireText(answer, "정답", MAX_RESPONSE_LENGTH);
        List<String> validatedChoices = validateChoices(
                validatedProblemType,
                choices,
                validatedAnswer
        );
        List<String> validatedAllowedAnswers = validateAllowedAnswers(
                validatedProblemType,
                allowedAnswerVariants,
                validatedAnswer
        );
        List<String> validatedConceptTags = validateConceptTags(conceptTags);

        return new ValidatedContent(
                validatedProblemType,
                validatedQuestionText,
                validatedChoices,
                validatedAnswer,
                validatedAllowedAnswers,
                validatedConceptTags
        );
    }

    private static DailyQuizProblemType requireProblemType(DailyQuizProblemType problemType) {
        if (problemType == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "문제 유형은 필수입니다.");
        }
        return problemType;
    }

    private void validateCanCreateNextVersion() {
        if (this.status != DailyQuizQuestionStatus.FLAGGED) {
            throw new BusinessException(ErrorCode.LATEST_VERSION_NOT_FLAGGED);
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, fieldName + ": 필수입니다.");
        }
        return value.strip();
    }

    private static String requireText(String value, String fieldName, int maxLength) {
        String validatedValue = requireText(value, fieldName);
        if (validatedValue.length() > maxLength) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    fieldName + ": " + maxLength + "자를 초과할 수 없습니다.");
        }
        return validatedValue;
    }

    private static String validateQuestionText(
            DailyQuizProblemType problemType,
            String questionText
    ) {
        String validatedQuestionText = requireText(questionText, "문제 지문");

        if (problemType == DailyQuizProblemType.FILL_IN_BLANK
                && countOccurrences(validatedQuestionText, FILL_IN_BLANK_MARKER) != 1) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "빈칸형 문제에는 " + FILL_IN_BLANK_MARKER + "가 정확히 한 번 있어야 합니다."
            );
        }

        return validatedQuestionText;
    }

    private static List<String> validateChoices(
            DailyQuizProblemType problemType,
            List<String> choices,
            String answer
    ) {
        if (problemType != DailyQuizProblemType.MULTIPLE_CHOICE) {
            requireEmptyList(choices, "선택지는 객관식 문제에서만 사용할 수 있습니다.");
            return null;
        }

        if (choices == null || choices.size() != MULTIPLE_CHOICE_OPTION_COUNT) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "객관식 문제는 선택지가 정확히 " + MULTIPLE_CHOICE_OPTION_COUNT + "개 필요합니다.");
        }

        List<String> validatedChoices = validateTextList(choices, "선택지", MAX_RESPONSE_LENGTH);
        requireNoDuplicates(validatedChoices, "선택지는 중복될 수 없습니다.");

        if (!validatedChoices.contains(answer)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "객관식 정답은 선택지 중 하나여야 합니다.");
        }
        return validatedChoices;
    }

    private static List<String> validateAllowedAnswers(
            DailyQuizProblemType problemType,
            List<String> allowedAnswerVariants,
            String answer
    ) {
        if (problemType != DailyQuizProblemType.SHORT_ANSWER) {
            requireEmptyList(allowedAnswerVariants, "허용 답안 표현은 단답형 문제에서만 사용할 수 있습니다.");
            return null;
        }

        if (allowedAnswerVariants == null) {
            return null;
        }

        if (allowedAnswerVariants.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "허용 답안 표현이 없다면 null이어야 합니다.");
        }

        List<String> validatedVariants = validateTextList(
                allowedAnswerVariants,
                "허용 답안 표현",
                MAX_RESPONSE_LENGTH
        );
        requireNoDuplicates(validatedVariants, "허용 답안 표현은 중복될 수 없습니다.");

        if (validatedVariants.contains(answer)) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "대표 정답을 허용 답안 표현에 중복해서 넣을 수 없습니다."
            );
        }

        return validatedVariants;
    }

    private static int countOccurrences(String text, String target) {
        int count = 0;
        int fromIndex = 0;

        while ((fromIndex = text.indexOf(target, fromIndex)) >= 0) {
            count++;
            fromIndex += target.length();
        }

        return count;
    }

    private static List<String> validateConceptTags(List<String> conceptTags) {
        if (conceptTags == null || conceptTags.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "개념 태그는 하나 이상 필요합니다.");
        }

        List<String> validatedConceptTags = validateTextList(
                conceptTags,
                "개념 태그",
                MAX_CONCEPT_TAG_LENGTH
        );
        requireNoDuplicates(validatedConceptTags, "개념 태그는 중복될 수 없습니다.");
        return validatedConceptTags;
    }

    private static List<String> validateTextList(
            List<String> values,
            String fieldName,
            int maxLength
    ) {
        return IntStream.range(0, values.size())
                .mapToObj(index -> requireText(
                        values.get(index),
                        fieldName + "[" + (index + 1) + "]",
                        maxLength
                ))
                .toList();
    }

    private static void requireNoDuplicates(List<String> values, String message) {
        if (new HashSet<>(values).size() != values.size()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, message);
        }
    }

    private static void requireEmptyList(List<String> values, String message) {
        if (values != null && !values.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, message);
        }
    }

    private record ValidatedContent(
            DailyQuizProblemType problemType,
            String questionText,
            List<String> choices,
            String answer,
            List<String> allowedAnswerVariants,
            List<String> conceptTags
    ) {
    }
}
