package com.maesamco.user.infrastructure.feign;

import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import com.maesamco.user.global.response.SuccessResponse;
import feign.FeignException;
import feign.Request;
import feign.Response;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Content Service 개념 검증 어댑터의 응답 및 오류 변환을 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class ContentConceptValidationAdapterTest {

    private static final UUID FIRST_CONCEPT_ID =
            UUID.fromString(
                    "11111111-1111-1111-1111-111111111111"
            );

    private static final UUID SECOND_CONCEPT_ID =
            UUID.fromString(
                    "22222222-2222-2222-2222-222222222222"
            );

    @Mock
    private ContentServiceFeignClient
            contentServiceFeignClient;

    private ContentConceptValidationAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter =
                new ContentConceptValidationAdapter(
                        contentServiceFeignClient,
                        new ObjectMapper()
                );
    }

    @Test
    @DisplayName(
            "요청한 모든 개념이 유효하면 검증을 통과한다"
    )
    void validatesAllConcepts() {
        List<UUID> conceptIds =
                List.of(
                        FIRST_CONCEPT_ID,
                        SECOND_CONCEPT_ID
                );

        when(
                contentServiceFeignClient.validateConcepts(
                        new ConceptValidationRequest(
                                conceptIds
                        )
                )
        ).thenReturn(
                SuccessResponse.success(
                        new ConceptValidationResponse(
                                true,
                                conceptIds,
                                List.of()
                        )
                )
        );

        assertThatCode(
                () -> adapter.validateAll(conceptIds)
        ).doesNotThrowAnyException();
    }

    @Test
    @DisplayName(
            "유효하지 않은 개념이 포함되면 CONCEPT_NOT_FOUND를 반환한다"
    )
    void rejectsInvalidConcept() {
        List<UUID> conceptIds =
                List.of(
                        FIRST_CONCEPT_ID,
                        SECOND_CONCEPT_ID
                );

        when(
                contentServiceFeignClient.validateConcepts(
                        new ConceptValidationRequest(
                                conceptIds
                        )
                )
        ).thenReturn(
                SuccessResponse.success(
                        new ConceptValidationResponse(
                                false,
                                List.of(FIRST_CONCEPT_ID),
                                List.of(SECOND_CONCEPT_ID)
                        )
                )
        );

        assertBusinessError(
                () -> adapter.validateAll(conceptIds),
                ErrorCode.CONCEPT_NOT_FOUND
        );
    }

    @Test
    @DisplayName(
            "정상 응답의 유효 개념 목록이 요청과 다르면 "
                    + "CONTENT_SERVICE_UNAVAILABLE을 반환한다"
    )
    void rejectsMismatchedResponse() {
        List<UUID> conceptIds =
                List.of(
                        FIRST_CONCEPT_ID,
                        SECOND_CONCEPT_ID
                );

        when(
                contentServiceFeignClient.validateConcepts(
                        new ConceptValidationRequest(
                                conceptIds
                        )
                )
        ).thenReturn(
                SuccessResponse.success(
                        new ConceptValidationResponse(
                                true,
                                List.of(FIRST_CONCEPT_ID),
                                List.of()
                        )
                )
        );

        assertBusinessError(
                () -> adapter.validateAll(conceptIds),
                ErrorCode.CONTENT_SERVICE_UNAVAILABLE
        );
    }

    @Test
    @DisplayName(
            "성공 응답이나 data가 없으면 "
                    + "CONTENT_SERVICE_UNAVAILABLE을 반환한다"
    )
    void rejectsInvalidSuccessResponse() {
        List<UUID> conceptIds =
                List.of(FIRST_CONCEPT_ID);

        when(
                contentServiceFeignClient.validateConcepts(
                        new ConceptValidationRequest(
                                conceptIds
                        )
                )
        ).thenReturn(
                new SuccessResponse<>(
                        false,
                        null
                )
        );

        assertBusinessError(
                () -> adapter.validateAll(conceptIds),
                ErrorCode.CONTENT_SERVICE_UNAVAILABLE
        );
    }

    @Test
    @DisplayName(
            "Content Service가 CONCEPT_NOT_FOUND를 반환하면 "
                    + "동일한 오류로 변환한다"
    )
    void mapsRemoteConceptNotFound() {
        List<UUID> conceptIds =
                List.of(FIRST_CONCEPT_ID);

        when(
                contentServiceFeignClient.validateConcepts(
                        new ConceptValidationRequest(
                                conceptIds
                        )
                )
        ).thenThrow(
                feignException(
                        404,
                        """
                        {
                          "success": false,
                          "error": {
                            "code": "CONCEPT_NOT_FOUND",
                            "message": "개념을 찾을 수 없습니다."
                          }
                        }
                        """
                )
        );

        assertBusinessError(
                () -> adapter.validateAll(conceptIds),
                ErrorCode.CONCEPT_NOT_FOUND
        );
    }

    @Test
    @DisplayName(
            "내부 API 경로가 없어 발생한 404는 "
                    + "CONTENT_SERVICE_UNAVAILABLE로 변환한다"
    )
    void mapsMissingRouteToUnavailable() {
        List<UUID> conceptIds =
                List.of(FIRST_CONCEPT_ID);

        when(
                contentServiceFeignClient.validateConcepts(
                        new ConceptValidationRequest(
                                conceptIds
                        )
                )
        ).thenThrow(
                feignException(
                        404,
                        """
                        {
                          "success": false,
                          "error": {
                            "code": "ENTITY_NOT_FOUND",
                            "message": "요청한 리소스를 찾을 수 없습니다."
                          }
                        }
                        """
                )
        );

        assertBusinessError(
                () -> adapter.validateAll(conceptIds),
                ErrorCode.CONTENT_SERVICE_UNAVAILABLE
        );
    }

    @Test
    @DisplayName(
            "Content Service 5xx 응답은 "
                    + "CONTENT_SERVICE_UNAVAILABLE로 변환한다"
    )
    void mapsServerErrorToUnavailable() {
        List<UUID> conceptIds =
                List.of(FIRST_CONCEPT_ID);

        when(
                contentServiceFeignClient.validateConcepts(
                        new ConceptValidationRequest(
                                conceptIds
                        )
                )
        ).thenThrow(
                feignException(
                        500,
                        "{}"
                )
        );

        assertBusinessError(
                () -> adapter.validateAll(conceptIds),
                ErrorCode.CONTENT_SERVICE_UNAVAILABLE
        );
    }

    @Test
    @DisplayName(
            "빈 목록이면 Content Service를 호출하지 않는다"
    )
    void skipsEmptyConceptIds() {
        assertThatCode(
                () -> adapter.validateAll(
                        List.of()
                )
        ).doesNotThrowAnyException();

        verifyNoInteractions(
                contentServiceFeignClient
        );
    }

    @Test
    @DisplayName(
            "개념 ID 목록이 null이면 호출하지 않는다"
    )
    void rejectsNullConceptIds() {
        assertThatNullPointerException()
                .isThrownBy(
                        () -> adapter.validateAll(null)
                )
                .withMessage(
                        "검증할 개념 ID 목록은 필수입니다."
                );

        verifyNoInteractions(
                contentServiceFeignClient
        );
    }

    private FeignException feignException(
            int status,
            String body
    ) {
        Request request =
                Request.create(
                        Request.HttpMethod.POST,
                        "/internal/v1/concepts/validate",
                        Collections.emptyMap(),
                        null,
                        StandardCharsets.UTF_8
                );

        Response response =
                Response.builder()
                        .status(status)
                        .reason(
                                status == 404
                                        ? "Not Found"
                                        : "Internal Server Error"
                        )
                        .request(request)
                        .headers(
                                Collections.emptyMap()
                        )
                        .body(
                                body,
                                StandardCharsets.UTF_8
                        )
                        .build();

        return FeignException.errorStatus(
                "ContentServiceFeignClient#validateConcepts",
                response
        );
    }

    private void assertBusinessError(
            ThrowingCallable callable,
            ErrorCode expectedErrorCode
    ) {
        assertThatThrownBy(callable)
                .isInstanceOf(BusinessException.class)
                .extracting(
                        exception ->
                                ((BusinessException) exception)
                                        .getErrorCode()
                )
                .isEqualTo(expectedErrorCode);
    }
}
