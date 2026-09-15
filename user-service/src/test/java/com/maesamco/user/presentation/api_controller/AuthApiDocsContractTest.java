package com.maesamco.user.presentation.api_controller;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.maesamco.user.application.service.ConfirmEmailVerificationCommand;
import com.maesamco.user.application.service.RequestEmailVerificationCommand;
import com.maesamco.user.application.service.SignUpCommand;
import com.maesamco.user.application.service.SignUpResult;
import com.maesamco.user.global.response.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class AuthApiDocsContractTest {

    @Test
    @DisplayName("이메일 인증 요청의 OpenAPI 응답 계약을 정의한다")
    void requestEmailVerificationContract()
            throws NoSuchMethodException {

        // given
        Method method = AuthApiDocs.class.getMethod(
                "requestEmailVerification",
                RequestEmailVerificationCommand.class
        );

        // then
        assertOperation(method);
        assertResponseCodes(method, "202", "400");
        assertSuccessResponseUsesReturnType(method, "202");
        assertErrorResponseSchema(method, "400");
    }

    @Test
    @DisplayName("이메일 인증 확인의 OpenAPI 응답 계약을 정의한다")
    void confirmEmailVerificationContract()
            throws NoSuchMethodException {

        // given
        Method method = AuthApiDocs.class.getMethod(
                "confirmEmailVerification",
                ConfirmEmailVerificationCommand.class
        );

        // then
        assertOperation(method);
        assertResponseCodes(method, "200", "400", "429");
        assertSuccessResponseUsesReturnType(method, "200");
        assertErrorResponseSchema(method, "400");
        assertErrorResponseSchema(method, "429");
    }

    @Test
    @DisplayName("회원가입의 OpenAPI 응답과 Refresh Token Cookie 계약을 정의한다")
    void signUpContract()
            throws NoSuchMethodException {

        // given
        Method method = AuthApiDocs.class.getMethod(
                "signUp",
                SignUpCommand.class
        );

        // then
        assertOperation(method);
        assertResponseCodes(
                method,
                "201",
                "400",
                "409",
                "429",
                "503"
        );
        assertSuccessResponseUsesReturnType(method, "201");
        assertErrorResponseSchema(method, "400");
        assertErrorResponseSchema(method, "409");
        assertNoResponseContent(method, "429");
        assertErrorResponseSchema(method, "503");

        ApiResponse rateLimitResponse =
                findResponse(method, "429");

        assertThat(rateLimitResponse.description())
                .contains("API Gateway")
                .contains("응답 본문 없이");

        ApiResponse createdResponse =
                findResponse(method, "201");

        assertThat(
                Arrays.stream(createdResponse.headers())
                        .map(header -> header.name())
                        .toList()
        ).contains("Set-Cookie");
    }

    @Test
    @DisplayName("Refresh Token을 포함한 내부 토큰 정보는 JSON과 Swagger에서 숨긴다")
    void issuedTokensIsHidden()
            throws NoSuchMethodException {

        // given
        Method accessor = SignUpResult.class.getMethod(
                "issuedTokens"
        );

        // when
        JsonIgnore jsonIgnore =
                accessor.getAnnotation(JsonIgnore.class);

        Schema schema =
                accessor.getAnnotation(Schema.class);

        // then
        assertThat(jsonIgnore).isNotNull();
        assertThat(schema).isNotNull();
        assertThat(schema.hidden()).isTrue();
    }

    private void assertOperation(Method method) {
        Operation operation =
                method.getAnnotation(Operation.class);

        assertThat(operation).isNotNull();
        assertThat(operation.summary()).isNotBlank();
        assertThat(operation.description()).isNotBlank();
    }

    private void assertResponseCodes(
            Method method,
            String... expectedResponseCodes
    ) {
        ApiResponses apiResponses =
                method.getAnnotation(ApiResponses.class);

        assertThat(apiResponses).isNotNull();

        assertThat(
                Arrays.stream(apiResponses.value())
                        .map(ApiResponse::responseCode)
                        .toList()
        ).containsExactly(expectedResponseCodes);
    }

    private void assertSuccessResponseUsesReturnType(
            Method method,
            String responseCode
    ) {
        assertThat(
                findResponse(
                        method,
                        responseCode
                ).useReturnTypeSchema()
        ).isTrue();
    }

    private void assertErrorResponseSchema(
            Method method,
            String responseCode
    ) {
        ApiResponse response =
                findResponse(method, responseCode);

        assertThat(response.content()).hasSize(1);

        assertThat(
                response.content()[0]
                        .schema()
                        .implementation()
        ).isEqualTo(ErrorResponse.class);
    }

    private void assertNoResponseContent(
            Method method,
            String responseCode
    ) {
        ApiResponse response =
                findResponse(method, responseCode);

        assertThat(response.content()).isEmpty();
    }

    private ApiResponse findResponse(
            Method method,
            String responseCode
    ) {
        ApiResponses apiResponses =
                method.getAnnotation(ApiResponses.class);

        return Arrays.stream(apiResponses.value())
                .filter(response ->
                        response.responseCode()
                                .equals(responseCode)
                )
                .findFirst()
                .orElseThrow();
    }
}
