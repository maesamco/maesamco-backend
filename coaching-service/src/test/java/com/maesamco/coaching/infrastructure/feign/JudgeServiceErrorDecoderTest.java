package com.maesamco.coaching.infrastructure.feign;

import com.maesamco.coaching.global.exception.BusinessException;
import com.maesamco.coaching.global.exception.ErrorCode;
import feign.FeignException;
import feign.Request;
import feign.Request.HttpMethod;
import feign.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PR #127 리뷰(용현님 지적) — ContentServiceAdapter에서 발견된 것과 동일한 구조적 결함
 * (모든 404를 SUBMISSION_NOT_FOUND로 단정)이 JudgeServiceAdapter에도 있어 이를 해소하는
 * JudgeServiceErrorDecoder를 검증한다. Judge Service의 표준 에러 포맷({success,
 * error:{code,...}})을 실제로 파싱해서 진짜 "제출 없음"과 라우팅/계약 실패
 * (ENTITY_NOT_FOUND, 파싱 불가, 본문 없음)를 구분하는지 확인한다.
 */
class JudgeServiceErrorDecoderTest {

    private final JudgeServiceErrorDecoder decoder =
            new JudgeServiceErrorDecoder(ErrorCode.SUBMISSION_NOT_FOUND, ErrorCode.FEIGN_CLIENT_ERROR);

    @Test
    @DisplayName("error.code가 ENTITY_NOT_FOUND가 아니면 SUBMISSION_NOT_FOUND로 변환한다")
    void mapsDomainSpecificNotFoundCode() {
        Exception decoded = decoder.decode("getSubmission", response(404, """
                {"success":false,"error":{"code":"SUBMISSION_NOT_FOUND","message":"제출을 찾을 수 없습니다."}}
                """));

        assertThat(decoded).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) decoded).getErrorCode()).isEqualTo(ErrorCode.SUBMISSION_NOT_FOUND);
    }

    @Test
    @DisplayName("error.code가 ENTITY_NOT_FOUND면(라우팅 실패) FEIGN_CLIENT_ERROR로 변환한다")
    void mapsRouteMissingAsContractFailure() {
        Exception decoded = decoder.decode("getSubmission", response(404, """
                {"success":false,"error":{"code":"ENTITY_NOT_FOUND","message":"요청한 리소스를 찾을 수 없습니다."}}
                """));

        assertThat(decoded).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) decoded).getErrorCode()).isEqualTo(ErrorCode.FEIGN_CLIENT_ERROR);
    }

    @Test
    @DisplayName("표준 에러 포맷이 아닌 본문(파싱 실패)은 FEIGN_CLIENT_ERROR로 처리한다")
    void treatsUnparseableBodyAsContractFailure() {
        Exception decoded = decoder.decode("getSubmission", response(404, "<html>404 Not Found</html>"));

        assertThat(decoded).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) decoded).getErrorCode()).isEqualTo(ErrorCode.FEIGN_CLIENT_ERROR);
    }

    @Test
    @DisplayName("본문이 없는 404도 FEIGN_CLIENT_ERROR로 처리한다")
    void treatsEmptyBodyAsContractFailure() {
        Request request = Request.create(HttpMethod.GET, "/internal/v1/submissions/1",
                Collections.emptyMap(), null, StandardCharsets.UTF_8);
        Response response = Response.builder()
                .status(404)
                .reason("Not Found")
                .request(request)
                .headers(Collections.emptyMap())
                .build();

        Exception decoded = decoder.decode("getSubmission", response);

        assertThat(decoded).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) decoded).getErrorCode()).isEqualTo(ErrorCode.FEIGN_CLIENT_ERROR);
    }

    @Test
    @DisplayName("404가 아닌 상태 코드는 Feign 기본 디코더에 위임한다")
    void delegatesNonNotFoundStatusToDefaultDecoder() {
        Exception decoded = decoder.decode("getSubmission", response(500, "{}"));

        assertThat(decoded).isInstanceOf(FeignException.class);
        assertThat(((FeignException) decoded).status()).isEqualTo(500);
    }

    private Response response(int status, String body) {
        Request request = Request.create(HttpMethod.GET, "/internal/v1/submissions/1",
                Collections.emptyMap(), null, StandardCharsets.UTF_8);
        return Response.builder()
                .status(status)
                .reason(status == 404 ? "Not Found" : "Internal Server Error")
                .request(request)
                .headers(Collections.emptyMap())
                .body(body, StandardCharsets.UTF_8)
                .build();
    }
}
