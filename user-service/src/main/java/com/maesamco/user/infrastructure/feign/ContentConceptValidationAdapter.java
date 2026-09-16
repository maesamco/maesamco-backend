package com.maesamco.user.infrastructure.feign;

import com.maesamco.user.application.port.ConceptValidationPort;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import com.maesamco.user.global.response.ErrorResponse;
import com.maesamco.user.global.response.SuccessResponse;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Content Service 내부 API를 이용한 개념 검증 어댑터입니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContentConceptValidationAdapter
        implements ConceptValidationPort {

    private static final String REMOTE_CONCEPT_NOT_FOUND =
            ErrorCode.CONCEPT_NOT_FOUND.name();

    private final ContentServiceFeignClient contentServiceFeignClient;
    private final ObjectMapper objectMapper;

    /**
     * 요청된 모든 개념이 존재하며 활성 상태인지 검증합니다.
     */
    @Override
    public void validateAll(
            List<UUID> conceptIds
    ) {
        Objects.requireNonNull(
                conceptIds,
                "검증할 개념 ID 목록은 필수입니다."
        );

        if (conceptIds.isEmpty()) {
            return;
        }

        try {
            SuccessResponse<ConceptValidationResponse> response =
                    contentServiceFeignClient.validateConcepts(
                            new ConceptValidationRequest(
                                    conceptIds
                            )
                    );

            validateResponse(
                    conceptIds,
                    response
            );
        } catch (BusinessException e) {
            throw e;
        } catch (FeignException.NotFound e) {
            handleNotFound(e);
        } catch (FeignException e) {
            log.warn(
                    "Content Service 개념 검증 호출 실패: status={}",
                    e.status()
            );

            throw new BusinessException(
                    ErrorCode.CONTENT_SERVICE_UNAVAILABLE
            );
        } catch (RuntimeException e) {
            log.warn(
                    "Content Service 개념 검증 응답 처리 실패",
                    e
            );

            throw new BusinessException(
                    ErrorCode.CONTENT_SERVICE_UNAVAILABLE
            );
        }
    }

    /**
     * 정상 응답의 성공 여부와 검증 결과를 확인합니다.
     */
    private void validateResponse(
            List<UUID> requestedConceptIds,
            SuccessResponse<ConceptValidationResponse> response
    ) {
        if (
                response == null
                        || !response.success()
                        || response.data() == null
        ) {
            throw new BusinessException(
                    ErrorCode.CONTENT_SERVICE_UNAVAILABLE
            );
        }

        ConceptValidationResponse result =
                response.data();

        if (
                !result.valid()
                        || !result.invalidConceptIds().isEmpty()
        ) {
            throw new BusinessException(
                    ErrorCode.CONCEPT_NOT_FOUND
            );
        }

        Set<UUID> requestedConceptIdSet =
                new HashSet<>(requestedConceptIds);

        Set<UUID> validConceptIdSet =
                new HashSet<>(
                        result.validConceptIds()
                );

        if (!validConceptIdSet.equals(requestedConceptIdSet)) {
            log.warn(
                    "Content Service 개념 검증 응답 계약 불일치"
            );

            throw new BusinessException(
                    ErrorCode.CONTENT_SERVICE_UNAVAILABLE
            );
        }
    }

    /**
     * 실제 개념 미존재 응답과 내부 API 경로 미구현 등의 404를 구분합니다.
     */
    private void handleNotFound(
            FeignException.NotFound exception
    ) {
        String remoteErrorCode =
                readRemoteErrorCode(exception);

        if (REMOTE_CONCEPT_NOT_FOUND.equals(remoteErrorCode)) {
            throw new BusinessException(
                    ErrorCode.CONCEPT_NOT_FOUND
            );
        }

        log.warn(
                "Content Service 내부 API 404: remoteErrorCode={}",
                remoteErrorCode
        );

        throw new BusinessException(
                ErrorCode.CONTENT_SERVICE_UNAVAILABLE
        );
    }

    /**
     * Content Service 공통 오류 응답에서 오류 코드를 추출합니다.
     */
    private String readRemoteErrorCode(
            FeignException exception
    ) {
        try {
            ErrorResponse errorResponse =
                    objectMapper.readValue(
                            exception.contentUTF8(),
                            ErrorResponse.class
                    );

            if (
                    errorResponse.error() == null
                            || errorResponse.error().code() == null
            ) {
                return null;
            }

            return errorResponse.error().code();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
