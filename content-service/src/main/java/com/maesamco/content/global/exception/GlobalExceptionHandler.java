package com.maesamco.content.global.exception;

import com.maesamco.content.global.response.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 전역 예외 처리기. 각 서비스에 그대로 복사해 사용한다(팀 컨벤션 13절).
 * Controller에서 try-catch를 지양하고 이 핸들러로 통일한다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 주 통로 — 도메인 로직에서 의도적으로 던지는 모든 예외
    //
    // ⚠️ 리뷰로 발견(PR #128 재리뷰) — 예전엔 ErrorCode가 뭐든 무조건 log.warn으로
    // 찍어서, 서비스 계층에서 아무리 log.debug로 로그 신호를 분리해도(예:
    // RefreshService의 "정상 grace window 동시 요청" vs "실제 재사용 탈취" 구분)
    // 여기서 다시 같은 텍스트의 WARN 하나로 뭉개버렸다. BusinessException 인스턴스에
    // 실린 logSeverity를 그대로 따라가도록 바꿔, 서비스 계층이 정한 로그 신호가
    // 핸들러까지 그대로 전달되게 한다.
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException e
    ) {
        if (e.getLogSeverity() == BusinessException.LogSeverity.DEBUG) {
            log.debug(
                    "BusinessException: {} - {}",
                    e.getErrorCode().name(),
                    e.getMessage()
            );
        } else {
            log.warn(
                    "BusinessException: {} - {}",
                    e.getErrorCode().name(),
                    e.getMessage()
            );
        }

        return ResponseEntity
                .status(e.getErrorCode().getStatus())
                .body(
                        ErrorResponse.from(
                                e.getErrorCode(),
                                e.getMessage()
                        )
                );
    }

    // Validation 실패 — 필드 에러 목록 포함
    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            BindException.class
    })
    public ResponseEntity<ErrorResponse> handleValidationException(
            BindException e
    ) {
        List<ErrorResponse.FieldError> fieldErrors =
                e.getBindingResult()
                        .getFieldErrors()
                        .stream()
                        .map(fe ->
                                new ErrorResponse.FieldError(
                                        fe.getField(),
                                        messageOf(fe)
                                )
                        )
                        .toList();

        log.warn(
                "ValidationException: {}",
                fieldErrors
        );

        return ResponseEntity
                .status(
                        ErrorCode.INVALID_INPUT_VALUE
                                .getStatus()
                )
                .body(
                        ErrorResponse.from(
                                ErrorCode.INVALID_INPUT_VALUE,
                                fieldErrors
                        )
                );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException e
    ) {
        log.warn(
                "ConstraintViolationException: {}",
                e.getMessage()
        );

        return ResponseEntity
                .status(
                        ErrorCode.INVALID_INPUT_VALUE
                                .getStatus()
                )
                .body(
                        ErrorResponse.from(
                                ErrorCode.INVALID_INPUT_VALUE,
                                e.getMessage()
                        )
                );
    }

    /**
     * JSON 요청 본문을 객체로 변환할 수 없는 경우 처리합니다.
     *
     * <p>잘못된 JSON 문법, Enum 변환 실패, 허용되지 않은 JSON 필드 등
     * 요청 본문 역직렬화 단계에서 발생한 오류를 400으로 반환합니다.</p>
     *
     * <p>예외 메시지에 사용자의 입력값이 포함될 수 있으므로
     * 원본 메시지는 로그나 응답에 노출하지 않습니다.</p>
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(
            HttpMessageNotReadableException e
    ) {
        log.warn(
                "HttpMessageNotReadableException: "
                        + "요청 본문을 역직렬화할 수 없습니다."
        );

        return ResponseEntity
                .status(
                        ErrorCode.INVALID_INPUT_VALUE
                                .getStatus()
                )
                .body(
                        ErrorResponse.from(
                                ErrorCode.INVALID_INPUT_VALUE,
                                "요청 본문의 형식이 올바르지 않습니다."
                        )
                );
    }

    // 파라미터 누락 / 타입 불일치 / 지원하지 않는 메서드
    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest(
            Exception e
    ) {
        log.warn(
                "Bad request: {}",
                e.getMessage()
        );

        return ResponseEntity
                .status(
                        ErrorCode.INVALID_INPUT_VALUE
                                .getStatus()
                )
                .body(
                        ErrorResponse.from(
                                ErrorCode.INVALID_INPUT_VALUE,
                                e.getMessage()
                        )
                );
    }

    @ExceptionHandler(
            HttpRequestMethodNotSupportedException.class
    )
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException e
    ) {
        log.warn(
                "Method not allowed: {}",
                e.getMessage()
        );

        return ResponseEntity
                .status(
                        ErrorCode.METHOD_NOT_ALLOWED
                                .getStatus()
                )
                .body(
                        ErrorResponse.from(
                                ErrorCode.METHOD_NOT_ALLOWED
                        )
                );
    }

    // DB 제약 위반 — 예: p_submissions.idempotency_key UNIQUE 충돌
    // 도메인에서 선제적으로 잡아 더 구체적인 ErrorCode로 던지지 못한
    // 경우의 최종 방어선. 가능하면 도메인 서비스에서 먼저 처리할 것을 권장.
    @ExceptionHandler(
            DataIntegrityViolationException.class
    )
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException e
    ) {
        log.warn(
                "DataIntegrityViolationException: {}",
                e.getMessage()
        );

        return ResponseEntity
                .status(
                        ErrorCode.INVALID_INPUT_VALUE
                                .getStatus()
                )
                .body(
                        ErrorResponse.from(
                                ErrorCode.INVALID_INPUT_VALUE,
                                "이미 존재하거나 제약 조건에 위배되는 요청입니다."
                        )
                );
    }

    // 인가 실패 — IDOR 방지 원칙 3에 따라
    // 리소스 존재 여부를 감추고 싶은 지점에서는
    // ENTITY_NOT_FOUND(404)를 직접 던질 것.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException e
    ) {
        log.warn(
                "AccessDeniedException: {}",
                e.getMessage()
        );

        return ResponseEntity
                .status(
                        ErrorCode.AUTH_ACCESS_DENIED
                                .getStatus()
                )
                .body(
                        ErrorResponse.from(
                                ErrorCode.AUTH_ACCESS_DENIED
                        )
                );
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(
            AuthenticationException e
    ) {
        log.warn(
                "AuthenticationException: {}",
                e.getMessage()
        );

        return ResponseEntity
                .status(
                        ErrorCode.AUTH_UNAUTHORIZED
                                .getStatus()
                )
                .body(
                        ErrorResponse.from(
                                ErrorCode.AUTH_UNAUTHORIZED
                        )
                );
    }

    // 반드시 별도 처리 — 안 잡으면 catch-all이 404를 500으로 바꿔버린다.
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(
            NoResourceFoundException e
    ) {
        log.warn(
                "NoResourceFoundException: {}",
                e.getMessage()
        );

        return ResponseEntity
                .status(
                        ErrorCode.ENTITY_NOT_FOUND
                                .getStatus()
                )
                .body(
                        ErrorResponse.from(
                                ErrorCode.ENTITY_NOT_FOUND
                        )
                );
    }

    // Enum 필드 역직렬화 실패 시 400 대신 500 응답
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("HttpMessageNotReadableException: {}", e.getMessage());

        return ResponseEntity.status(ErrorCode.INVALID_INPUT_VALUE.getStatus())
                .body(ErrorResponse.from(ErrorCode.INVALID_INPUT_VALUE, "요청 본문의 형식 또는 값이 올바르지 않습니다."));
    }

    // 낙관적 락 충돌 발생 시 409 응답
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLockingFailure(ObjectOptimisticLockingFailureException e) {
        log.warn("ObjectOptimisticLockingFailureException: {}", e.getMessage());

        return ResponseEntity.status(ErrorCode.PROBLEM_MODIFIED_CONCURRENTLY.getStatus())
                .body(ErrorResponse.from(ErrorCode.PROBLEM_MODIFIED_CONCURRENTLY));
    }

    // 최종 안전망
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(
            Exception e
    ) {
        log.error(
                "Unhandled exception",
                e
        );

        return ResponseEntity
                .status(
                        ErrorCode.INTERNAL_SERVER_ERROR
                                .getStatus()
                )
                .body(
                        ErrorResponse.from(
                                ErrorCode.INTERNAL_SERVER_ERROR
                        )
                );
    }

    private String messageOf(FieldError fe) {
        return fe.getDefaultMessage() == null
                ? "유효하지 않은 값입니다."
                : fe.getDefaultMessage();
    }
}