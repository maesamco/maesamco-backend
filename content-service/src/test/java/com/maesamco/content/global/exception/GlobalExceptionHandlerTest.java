package com.maesamco.content.global.exception;

import com.maesamco.content.domain.entity.problem.Problem;
import com.maesamco.content.global.response.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler globalExceptionHandler;

    @BeforeEach
    void setUp() {
        globalExceptionHandler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("BusinessException은 ErrorCode의 상태 코드와 메시지로 응답한다")
    void handleBusinessException_returnsErrorCodeResponse() {
        // given
        BusinessException exception =
                new BusinessException(
                        ErrorCode.PROBLEM_NOT_FOUND,
                        "문제를 찾을 수 없습니다."
                );

        // when
        ResponseEntity<ErrorResponse> response =
                globalExceptionHandler.handleBusinessException(exception);

        // then
        assertErrorResponse(
                response,
                ErrorCode.PROBLEM_NOT_FOUND,
                "문제를 찾을 수 없습니다."
        );
    }

    @Test
    @DisplayName("BusinessException의 사용자 정의 메시지를 응답에 유지한다")
    void handleBusinessException_customMessage_preservesMessage() {
        // given
        String message = "커스텀 에러 메시지";

        BusinessException exception =
                new BusinessException(
                        ErrorCode.INVALID_INPUT_VALUE,
                        message
                );

        // when
        ResponseEntity<ErrorResponse> response =
                globalExceptionHandler.handleBusinessException(exception);

        // then
        assertErrorResponse(
                response,
                ErrorCode.INVALID_INPUT_VALUE,
                message
        );
    }

    @Test
    @DisplayName("Validation 실패 시 INVALID_INPUT_VALUE와 필드 에러 목록을 반환한다")
    void handleValidationException_returnsFieldErrors() {
        // given
        BindException exception =
                new BindException(
                        new Object(),
                        "request"
                );

        exception.addError(
                new FieldError(
                        "request",
                        "title",
                        "제목은 필수입니다."
                )
        );

        exception.addError(
                new FieldError(
                        "request",
                        "description",
                        "설명은 필수입니다."
                )
        );

        // when
        ResponseEntity<ErrorResponse> response =
                globalExceptionHandler.handleValidationException(exception);

        // then
        assertThat(response.getStatusCode())
                .isEqualTo(
                        ErrorCode.INVALID_INPUT_VALUE.getStatus()
                );

        ErrorResponse body = response.getBody();

        assertThat(body)
                .isNotNull();

        assertThat(body.success())
                .isFalse();

        assertThat(body.error().code())
                .isEqualTo(
                        ErrorCode.INVALID_INPUT_VALUE.name()
                );

        assertThat(body.error().message())
                .isEqualTo(
                        ErrorCode.INVALID_INPUT_VALUE.getMessage()
                );

        assertThat(body.error().fieldErrors())
                .containsExactly(
                        new ErrorResponse.FieldError(
                                "title",
                                "제목은 필수입니다."
                        ),
                        new ErrorResponse.FieldError(
                                "description",
                                "설명은 필수입니다."
                        )
                );

        assertThat(body.timestamp())
                .isNotNull();
    }

    @Test
    @DisplayName("Validation 필드 메시지가 null이면 기본 메시지를 반환한다")
    void handleValidationException_nullMessage_usesDefaultMessage() {
        // given
        BindException exception =
                new BindException(
                        new Object(),
                        "request"
                );

        exception.addError(
                new FieldError(
                        "request",
                        "title",
                        null
                )
        );

        // when
        ResponseEntity<ErrorResponse> response =
                globalExceptionHandler.handleValidationException(exception);

        // then
        assertThat(response.getBody())
                .isNotNull();

        assertThat(response.getBody()
                .error()
                .fieldErrors())
                .containsExactly(
                        new ErrorResponse.FieldError(
                                "title",
                                "유효하지 않은 값입니다."
                        )
                );
    }

    @Test
    @DisplayName("ConstraintViolationException은 INVALID_INPUT_VALUE로 응답한다")
    void handleConstraintViolation_returnsBadRequest() {
        // given
        ConstraintViolationException exception =
                new ConstraintViolationException(
                        "size must be greater than zero",
                        Set.of()
                );

        // when
        ResponseEntity<ErrorResponse> response =
                globalExceptionHandler.handleConstraintViolation(exception);

        // then
        assertErrorResponse(
                response,
                ErrorCode.INVALID_INPUT_VALUE,
                "size must be greater than zero"
        );
    }

    @Test
    @DisplayName("JSON 요청 본문을 읽을 수 없으면 원본 예외 메시지를 노출하지 않고 고정 메시지를 반환한다")
    void handleHttpMessageNotReadable_returnsSafeMessage() {
        // given
        HttpMessageNotReadableException exception =
                mock(HttpMessageNotReadableException.class);

        when(exception.getMessage())
                .thenReturn(
                        "secret invalid input value"
                );

        // when
        ResponseEntity<ErrorResponse> response =
                globalExceptionHandler.handleHttpMessageNotReadable(exception);

        // then
        assertErrorResponse(
                response,
                ErrorCode.INVALID_INPUT_VALUE,
                "요청 본문의 형식이 올바르지 않습니다."
        );

        assertThat(response.getBody()
                .error()
                .message())
                .doesNotContain(
                        "secret invalid input value"
                );
    }

    @Test
    @DisplayName("필수 요청 파라미터가 없으면 INVALID_INPUT_VALUE로 응답한다")
    void handleBadRequest_missingParameter_returnsBadRequest() {
        // given
        MissingServletRequestParameterException exception =
                new MissingServletRequestParameterException(
                        "page",
                        "Integer"
                );

        // when
        ResponseEntity<ErrorResponse> response =
                globalExceptionHandler.handleBadRequest(exception);

        // then
        assertThat(response.getStatusCode())
                .isEqualTo(
                        ErrorCode.INVALID_INPUT_VALUE.getStatus()
                );

        assertThat(response.getBody())
                .isNotNull();

        assertThat(response.getBody().success())
                .isFalse();

        assertThat(response.getBody()
                .error()
                .code())
                .isEqualTo(
                        ErrorCode.INVALID_INPUT_VALUE.name()
                );

        assertThat(response.getBody()
                .error()
                .message())
                .isEqualTo(
                        exception.getMessage()
                );
    }

    @Test
    @DisplayName("지원하지 않는 HTTP 메서드이면 METHOD_NOT_ALLOWED로 응답한다")
    void handleMethodNotAllowed_returnsMethodNotAllowed() {
        // given
        HttpRequestMethodNotSupportedException exception =
                new HttpRequestMethodNotSupportedException(
                        "DELETE"
                );

        // when
        ResponseEntity<ErrorResponse> response =
                globalExceptionHandler.handleMethodNotAllowed(exception);

        // then
        assertErrorResponse(
                response,
                ErrorCode.METHOD_NOT_ALLOWED,
                ErrorCode.METHOD_NOT_ALLOWED.getMessage()
        );
    }

    @Test
    @DisplayName("DB 제약 조건 위반은 사용자에게 내부 DB 정보를 노출하지 않는다")
    void handleDataIntegrityViolation_returnsSafeMessage() {
        // given
        DataIntegrityViolationException exception =
                new DataIntegrityViolationException(
                        "duplicate key value violates unique constraint uk_secret"
                );

        // when
        ResponseEntity<ErrorResponse> response =
                globalExceptionHandler.handleDataIntegrityViolation(
                        exception
                );

        // then
        assertErrorResponse(
                response,
                ErrorCode.INVALID_INPUT_VALUE,
                "이미 존재하거나 제약 조건에 위배되는 요청입니다."
        );

        assertThat(response.getBody()
                .error()
                .message())
                .doesNotContain(
                        "uk_secret"
                );
    }

    @Test
    @DisplayName("접근 권한이 없으면 AUTH_ACCESS_DENIED로 응답한다")
    void handleAccessDenied_returnsForbidden() {
        // given
        AccessDeniedException exception =
                new AccessDeniedException(
                        "access denied"
                );

        // when
        ResponseEntity<ErrorResponse> response =
                globalExceptionHandler.handleAccessDenied(exception);

        // then
        assertErrorResponse(
                response,
                ErrorCode.AUTH_ACCESS_DENIED,
                ErrorCode.AUTH_ACCESS_DENIED.getMessage()
        );
    }

    @Test
    @DisplayName("인증에 실패하면 AUTH_UNAUTHORIZED로 응답한다")
    void handleAuthenticationException_returnsUnauthorized() {
        // given
        BadCredentialsException exception =
                new BadCredentialsException(
                        "invalid credentials"
                );

        // when
        ResponseEntity<ErrorResponse> response =
                globalExceptionHandler.handleAuthenticationException(
                        exception
                );

        // then
        assertErrorResponse(
                response,
                ErrorCode.AUTH_UNAUTHORIZED,
                ErrorCode.AUTH_UNAUTHORIZED.getMessage()
        );
    }

    @Test
    @DisplayName("존재하지 않는 정적 리소스 요청은 ENTITY_NOT_FOUND로 응답한다")
    void handleNoResourceFound_returnsNotFound() {
        // given
        NoResourceFoundException exception =
                mock(NoResourceFoundException.class);

        when(exception.getMessage())
                .thenReturn(
                        "No static resource missing"
                );

        // when
        ResponseEntity<ErrorResponse> response =
                globalExceptionHandler.handleNoResourceFound(
                        exception
                );

        // then
        assertErrorResponse(
                response,
                ErrorCode.ENTITY_NOT_FOUND,
                ErrorCode.ENTITY_NOT_FOUND.getMessage()
        );
    }

    @Test
    @DisplayName("낙관적 락 충돌은 PROBLEM_MODIFIED_CONCURRENTLY로 응답한다")
    void handleOptimisticLockingFailure_returnsConflict() {
        // given
        ObjectOptimisticLockingFailureException exception =
                new ObjectOptimisticLockingFailureException(
                        Problem.class,
                        UUID.randomUUID()
                );

        // when
        ResponseEntity<ErrorResponse> response =
                globalExceptionHandler.handleOptimisticLockingFailure(
                        exception
                );

        // then
        assertErrorResponse(
                response,
                ErrorCode.PROBLEM_MODIFIED_CONCURRENTLY,
                ErrorCode.PROBLEM_MODIFIED_CONCURRENTLY.getMessage()
        );
    }

    @Test
    @DisplayName("처리되지 않은 예외는 INTERNAL_SERVER_ERROR로 응답하고 내부 메시지를 노출하지 않는다")
    void handleException_returnsInternalServerError() {
        // given
        Exception exception =
                new IllegalStateException(
                        "database password is secret"
                );

        // when
        ResponseEntity<ErrorResponse> response =
                globalExceptionHandler.handleException(exception);

        // then
        assertErrorResponse(
                response,
                ErrorCode.INTERNAL_SERVER_ERROR,
                ErrorCode.INTERNAL_SERVER_ERROR.getMessage()
        );

        assertThat(response.getBody()
                .error()
                .message())
                .doesNotContain(
                        "database password"
                );
    }

    private void assertErrorResponse(
            ResponseEntity<ErrorResponse> response,
            ErrorCode errorCode,
            String expectedMessage
    ) {
        assertThat(response.getStatusCode())
                .isEqualTo(
                        errorCode.getStatus()
                );

        ErrorResponse body =
                response.getBody();

        assertThat(body)
                .isNotNull();

        assertThat(body.success())
                .isFalse();

        assertThat(body.error())
                .isNotNull();

        assertThat(body.error().code())
                .isEqualTo(
                        errorCode.name()
                );

        assertThat(body.error().message())
                .isEqualTo(
                        expectedMessage
                );

        assertThat(body.error().fieldErrors())
                .isNull();

        assertThat(body.timestamp())
                .isNotNull();
    }
}