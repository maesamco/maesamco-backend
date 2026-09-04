package com.maesamco.user.application.validation;

import com.maesamco.user.application.service.SignUpCommand;
import com.maesamco.user.domain.entity.LearningLevel;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 회원가입 비밀번호의 이메일/닉네임 동일값 검증 규칙을 테스트합니다.
 */
class SignUpPasswordValidatorTest {

    @Test
    @DisplayName(
            "비밀번호가 닉네임과 대소문자만 다르면 회원가입을 거부한다"
    )
    void validate_rejectsPasswordEqualToNicknameIgnoringCase() {
        // given
        SignUpCommand command =
                new SignUpCommand(
                        "learner@example.com",
                        "aBCD1234!",
                        "Abcd1234!",
                        3,
                        LearningLevel.BEGINNER
                );

        try (
                ValidatorFactory validatorFactory =
                        Validation.buildDefaultValidatorFactory()
        ) {
            Validator validator =
                    validatorFactory.getValidator();

            // when
            Set<ConstraintViolation<SignUpCommand>> violations =
                    validator.validate(command);

            // then
            assertThat(violations)
                    .anySatisfy(violation -> {
                        assertThat(
                                violation
                                        .getPropertyPath()
                                        .toString()
                        ).isEqualTo(
                                "password"
                        );

                        assertThat(
                                violation.getMessage()
                        ).isEqualTo(
                                "비밀번호는 이메일 또는 닉네임과 동일할 수 없습니다."
                        );
                    });
        }
    }
}
