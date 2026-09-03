package com.maesamco.user.presentation.api_controller;

import com.maesamco.user.application.port.IssuedTokens;
import com.maesamco.user.application.service.SignUpResult;
import com.maesamco.user.application.service.SignUpService;
import com.maesamco.user.domain.entity.LearningLevel;
import com.maesamco.user.domain.entity.UserRole;
import com.maesamco.user.domain.entity.UserStatus;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import com.maesamco.user.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AuthApiController의 회원가입 HTTP 계약을 검증합니다.
 *
 * <p>회원가입 성공 시 201 응답과 Access Token 응답 본문,
 * Refresh Token HttpOnly Cookie를 검증합니다.</p>
 *
 * <p>입력값 검증 실패 시 공통 오류 응답을 반환하고
 * 비밀번호 원문이 응답에 노출되지 않는지도 검증합니다.</p>
 *
 * <p>또한 회원가입 명령에 정의되지 않은 role, status 등의
 * 보호 필드가 JSON 요청에 포함되면 요청 자체를 거부하는지 검증합니다.</p>
 *
 * <p>이메일 또는 닉네임 중복이 발생하면
 * 각각의 전용 에러 코드와 409 Conflict 응답을 반환하는지 검증합니다.</p>
 */
@ExtendWith(MockitoExtension.class)
class AuthApiControllerTest {

    @Mock
    private SignUpService signUpService;

    @Mock
    private Clock clock;

    @InjectMocks
    private AuthApiController authApiController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        JsonMapper jsonMapper =
                JsonMapper.builder()
                        .findAndAddModules()
                        .enable(
                                DeserializationFeature
                                        .FAIL_ON_UNKNOWN_PROPERTIES
                        )
                        .build();

        JacksonJsonHttpMessageConverter
                messageConverter =
                new JacksonJsonHttpMessageConverter(
                        jsonMapper
                );

        mockMvc = MockMvcBuilders
                .standaloneSetup(authApiController)
                .setControllerAdvice(
                        new GlobalExceptionHandler()
                )
                .setMessageConverters(
                        messageConverter
                )
                .build();
    }

    @Test
    @DisplayName(
            "회원가입 성공 시 Access Token은 본문에, "
                    + "Refresh Token은 HttpOnly Cookie에 전달한다"
    )
    void signUp() throws Exception {
        // given
        UUID userId = UUID.randomUUID();

        Instant now =
                Instant.parse("2026-09-02T11:00:00Z");

        Instant accessTokenExpiresAt =
                now.plusSeconds(900);

        Instant refreshTokenExpiresAt =
                now.plusSeconds(
                        60L * 60 * 24 * 7
                );

        IssuedTokens issuedTokens =
                new IssuedTokens(
                        "access-token",
                        accessTokenExpiresAt,
                        "refresh-token",
                        refreshTokenExpiresAt
                );

        SignUpResult result =
                new SignUpResult(
                        userId,
                        "김티암",
                        UserRole.USER,
                        UserStatus.ACTIVE,
                        3,
                        LearningLevel.BEGINNER,
                        "access-token",
                        900,
                        issuedTokens
                );

        when(
                signUpService.signUp(any())
        ).thenReturn(result);

        when(clock.instant())
                .thenReturn(now);

        String requestBody = """
                {
                  "email": "learner@example.com",
                  "password": "Abcd1234!",
                  "nickname": "김티암",
                  "javaExperienceMonths": 3,
                  "learningLevel": "BEGINNER"
                }
                """;

        // when & then
        mockMvc.perform(
                        post("/api/v1/auth/signup")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(requestBody)
                )
                .andExpect(
                        status().isCreated()
                )
                .andExpect(
                        jsonPath("$.success")
                                .value(true)
                )
                .andExpect(
                        jsonPath("$.data.userId")
                                .value(userId.toString())
                )
                .andExpect(
                        jsonPath("$.data.nickname")
                                .value("김티암")
                )
                .andExpect(
                        jsonPath("$.data.role")
                                .value("USER")
                )
                .andExpect(
                        jsonPath("$.data.status")
                                .value("ACTIVE")
                )
                .andExpect(
                        jsonPath(
                                "$.data.javaExperienceMonths"
                        ).value(3)
                )
                .andExpect(
                        jsonPath("$.data.learningLevel")
                                .value("BEGINNER")
                )
                .andExpect(
                        jsonPath("$.data.accessToken")
                                .value("access-token")
                )
                .andExpect(
                        jsonPath(
                                "$.data.accessTokenExpiresIn"
                        ).value(900)
                )
                .andExpect(
                        jsonPath("$.data.issuedTokens")
                                .doesNotExist()
                )
                .andExpect(
                        jsonPath("$.data.refreshToken")
                                .doesNotExist()
                )
                .andExpect(
                        content().string(
                                not(
                                        containsString(
                                                "refresh-token"
                                        )
                                )
                        )
                )
                .andExpect(
                        header().string(
                                HttpHeaders.SET_COOKIE,
                                allOf(
                                        containsString(
                                                "refreshToken=refresh-token"
                                        ),
                                        containsString(
                                                "Path=/api/v1/auth"
                                        ),
                                        containsString(
                                                "Max-Age=604800"
                                        ),
                                        containsString(
                                                "Secure"
                                        ),
                                        containsString(
                                                "HttpOnly"
                                        ),
                                        containsString(
                                                "SameSite=Lax"
                                        )
                                )
                        )
                );
    }

    @Test
    @DisplayName(
            "비밀번호 검증 실패 시 400을 반환하고 "
                    + "비밀번호 원문은 노출하지 않는다"
    )
    void signUp_invalidPassword() throws Exception {
        // given
        String rawPassword = "short";

        String requestBody = """
                {
                  "email": "learner@example.com",
                  "password": "short",
                  "nickname": "김티암",
                  "javaExperienceMonths": 3,
                  "learningLevel": "BEGINNER"
                }
                """;

        // when & then
        mockMvc.perform(
                        post("/api/v1/auth/signup")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(requestBody)
                )
                .andExpect(
                        status().isBadRequest()
                )
                .andExpect(
                        jsonPath("$.success")
                                .value(false)
                )
                .andExpect(
                        jsonPath("$.error.code")
                                .value(
                                        "INVALID_INPUT_VALUE"
                                )
                )
                .andExpect(
                        jsonPath("$.error.message")
                                .value(
                                        "잘못된 입력입니다."
                                )
                )
                .andExpect(
                        jsonPath("$.error.fieldErrors")
                                .isArray()
                )
                .andExpect(
                        jsonPath(
                                "$.error.fieldErrors"
                                        + "[?(@.field == 'password')]"
                        ).exists()
                )
                .andExpect(
                        content().string(
                                not(
                                        containsString(
                                                rawPassword
                                        )
                                )
                        )
                );

        verify(
                signUpService,
                never()
        ).signUp(any());
    }

    @Test
    @DisplayName(
            "회원가입 요청에 role 또는 status가 포함되면 "
                    + "400을 반환한다"
    )
    void signUp_rejectsProtectedFields() throws Exception {
        // given
        String requestBody = """
                {
                  "email": "learner@example.com",
                  "password": "Abcd1234!",
                  "nickname": "김티암",
                  "javaExperienceMonths": 3,
                  "learningLevel": "BEGINNER",
                  "role": "ADMIN",
                  "status": "ACTIVE"
                }
                """;

        // when & then
        mockMvc.perform(
                        post("/api/v1/auth/signup")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(requestBody)
                )
                .andExpect(
                        status().isBadRequest()
                )
                .andExpect(
                        jsonPath("$.success")
                                .value(false)
                )
                .andExpect(
                        jsonPath("$.error.code")
                                .value(
                                        "INVALID_INPUT_VALUE"
                                )
                )
                .andExpect(
                        jsonPath("$.error.message")
                                .value(
                                        "요청 본문의 형식이 올바르지 않습니다."
                                )
                );

        verify(
                signUpService,
                never()
        ).signUp(any());
    }

    @Test
    @DisplayName(
            "이미 사용 중인 이메일로 회원가입하면 "
                    + "409를 반환한다"
    )
    void signUp_duplicateEmail() throws Exception {
        // given
        when(
                signUpService.signUp(any())
        ).thenThrow(
                new BusinessException(
                        ErrorCode.USER_DUPLICATE_EMAIL
                )
        );

        String requestBody = """
                {
                  "email": "learner@example.com",
                  "password": "Abcd1234!",
                  "nickname": "김티암",
                  "javaExperienceMonths": 3,
                  "learningLevel": "BEGINNER"
                }
                """;

        // when & then
        mockMvc.perform(
                        post("/api/v1/auth/signup")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(requestBody)
                )
                .andExpect(
                        status().isConflict()
                )
                .andExpect(
                        jsonPath("$.success")
                                .value(false)
                )
                .andExpect(
                        jsonPath("$.error.code")
                                .value(
                                        "USER_DUPLICATE_EMAIL"
                                )
                )
                .andExpect(
                        jsonPath("$.error.message")
                                .value(
                                        "이미 사용 중인 이메일입니다."
                                )
                )
                .andExpect(
                        header().doesNotExist(
                                HttpHeaders.SET_COOKIE
                        )
                );

        verify(signUpService)
                .signUp(any());
    }

    @Test
    @DisplayName(
            "이미 사용 중인 닉네임으로 회원가입하면 "
                    + "409를 반환한다"
    )
    void signUp_duplicateNickname() throws Exception {
        // given
        when(
                signUpService.signUp(any())
        ).thenThrow(
                new BusinessException(
                        ErrorCode.USER_DUPLICATE_NICKNAME
                )
        );

        String requestBody = """
                {
                  "email": "learner@example.com",
                  "password": "Abcd1234!",
                  "nickname": "김티암",
                  "javaExperienceMonths": 3,
                  "learningLevel": "BEGINNER"
                }
                """;

        // when & then
        mockMvc.perform(
                        post("/api/v1/auth/signup")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(requestBody)
                )
                .andExpect(
                        status().isConflict()
                )
                .andExpect(
                        jsonPath("$.success")
                                .value(false)
                )
                .andExpect(
                        jsonPath("$.error.code")
                                .value(
                                        "USER_DUPLICATE_NICKNAME"
                                )
                )
                .andExpect(
                        jsonPath("$.error.message")
                                .value(
                                        "이미 사용 중인 닉네임입니다."
                                )
                )
                .andExpect(
                        header().doesNotExist(
                                HttpHeaders.SET_COOKIE
                        )
                );

        verify(signUpService)
                .signUp(any());
    }
}
