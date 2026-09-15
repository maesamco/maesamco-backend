package com.maesamco.user.presentation.api_controller;

import com.maesamco.user.application.service.ChangePasswordCommand;
import com.maesamco.user.global.response.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * User API의 Swagger/OpenAPI 계약을 검증합니다.
 */
class UserApiDocsContractTest {

    @Test
    @DisplayName(
            "내 정보 조회 API의 OpenAPI 성공 및 오류 응답을 정의한다"
    )
    void getMyProfileContract()
            throws NoSuchMethodException {
        // given
        Method method = UserApiDocs.class.getMethod(
                "getMyProfile",
                Authentication.class
        );

        // then
        assertOperation(method);

        assertResponseCodes(
                method,
                "200",
                "401",
                "404",
                "500"
        );

        assertThat(
                findResponse(
                        method,
                        "200"
                ).useReturnTypeSchema()
        ).isTrue();

        assertErrorResponseSchema(
                method,
                "401"
        );

        assertErrorResponseSchema(
                method,
                "404"
        );

        assertErrorResponseSchema(
                method,
                "500"
        );
    }

    @Test
    @DisplayName(
            "비밀번호 변경 API의 OpenAPI 응답과 "
                    + "Refresh Token Cookie 삭제 계약을 정의한다"
    )
    void changePasswordContract()
            throws NoSuchMethodException {
        // given
        Method method = UserApiDocs.class.getMethod(
                "changePassword",
                Authentication.class,
                ChangePasswordCommand.class
        );

        // then
        assertOperation(method);

        assertResponseCodes(
                method,
                "204",
                "400",
                "401",
                "403",
                "404",
                "409",
                "500"
        );

        ApiResponse successResponse =
                findResponse(
                        method,
                        "204"
                );

        assertThat(successResponse.content())
                .isEmpty();

        assertThat(
                Arrays.stream(
                                successResponse.headers()
                        )
                        .map(header -> header.name())
                        .toList()
        ).contains("Set-Cookie");

        assertErrorResponseSchema(
                method,
                "400"
        );

        assertErrorResponseSchema(
                method,
                "401"
        );

        assertErrorResponseSchema(
                method,
                "403"
        );

        assertErrorResponseSchema(
                method,
                "404"
        );

        assertErrorResponseSchema(
                method,
                "409"
        );

        assertErrorResponseSchema(
                method,
                "500"
        );
    }

    /**
     * Swagger 작업 설명이 선언되어 있는지 확인합니다.
     */
    private void assertOperation(Method method) {
        Operation operation =
                method.getAnnotation(
                        Operation.class
                );

        assertThat(operation)
                .isNotNull();

        assertThat(operation.summary())
                .isNotBlank();

        assertThat(operation.description())
                .isNotBlank();
    }

    /**
     * 응답 코드가 정의된 순서와 일치하는지 확인합니다.
     */
    private void assertResponseCodes(
            Method method,
            String... expectedResponseCodes
    ) {
        ApiResponses apiResponses =
                method.getAnnotation(
                        ApiResponses.class
                );

        assertThat(apiResponses)
                .isNotNull();

        assertThat(
                Arrays.stream(
                                apiResponses.value()
                        )
                        .map(ApiResponse::responseCode)
                        .toList()
        ).containsExactly(
                expectedResponseCodes
        );
    }

    /**
     * 오류 응답이 공통 ErrorResponse 스키마를 사용하는지 확인합니다.
     */
    private void assertErrorResponseSchema(
            Method method,
            String responseCode
    ) {
        ApiResponse response =
                findResponse(
                        method,
                        responseCode
                );

        assertThat(response.content())
                .hasSize(1);

        assertThat(
                response.content()[0]
                        .schema()
                        .implementation()
        ).isEqualTo(
                ErrorResponse.class
        );
    }

    /**
     * 응답 코드에 해당하는 Swagger 응답 정의를 찾습니다.
     */
    private ApiResponse findResponse(
            Method method,
            String responseCode
    ) {
        ApiResponses apiResponses =
                method.getAnnotation(
                        ApiResponses.class
                );

        return Arrays.stream(
                        apiResponses.value()
                )
                .filter(response ->
                        response.responseCode()
                                .equals(responseCode)
                )
                .findFirst()
                .orElseThrow();
    }
}
