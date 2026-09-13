package com.maesamco.coaching.infrastructure.feign;

import com.maesamco.coaching.global.exception.BusinessException;
import com.maesamco.coaching.global.exception.ErrorCode;
import com.maesamco.coaching.global.response.ErrorResponse;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * 404를 무조건 notFoundErrorCode로 단정하지 않는다(PR #127 리뷰, 용현님 지적 —
 * ContentServiceErrorDecoder와 동일한 구조적 결함이 JudgeServiceAdapter에도 있었음) —
 * Judge Service도 이 프로젝트 공통 에러 포맷(게이트웨이 및 인증 보안 설계 9절)을 쓰고,
 * 컨트롤러 자체가 없는 경우(경로 오타, 버전 불일치, 아직 배포 안 됨)엔
 * NoResourceFoundException이 잡혀서 ENTITY_NOT_FOUND로 내려온다 — 이 값과 다른 code가
 * 와야만 진짜 "리소스 없음"으로 본다. 응답 본문이 이 포맷이 아니거나(게이트웨이가 아예
 * 다른 형식의 404를 반환하는 경우 등) 파싱에 실패하면 통신/계약 실패로 취급한다.
 */
@Slf4j
public class JudgeServiceErrorDecoder implements ErrorDecoder {

    /**
     * Judge Service가 라우팅 실패(컨트롤러 미배포·경로 오타 등)에 쓰는 공용 코드다.
     * 이 서비스(coaching)도 같은 이름의 ENTITY_NOT_FOUND를 독립적으로 갖고 있어(서비스
     * 간 공유 코드 없음, 팀 컨벤션) 그 enum의 name()을 그대로 재사용한다 — 두 서비스가
     * 이 명명 규칙을 계속 지키는 한 문자열 리터럴보다 오타에 안전하다.
     */
    private static final String ROUTE_NOT_FOUND_CODE = ErrorCode.ENTITY_NOT_FOUND.name();

    private final ErrorDecoder defaultDecoder = new Default();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ErrorCode notFoundErrorCode;
    private final ErrorCode contractFailureErrorCode;

    public JudgeServiceErrorDecoder(ErrorCode notFoundErrorCode, ErrorCode contractFailureErrorCode) {
        this.notFoundErrorCode = notFoundErrorCode;
        this.contractFailureErrorCode = contractFailureErrorCode;
    }

    @Override
    public Exception decode(String methodKey, Response response) {
        if (response.status() != 404) {
            return defaultDecoder.decode(methodKey, response);
        }

        String remoteErrorCode = readErrorCode(methodKey, response);
        if (remoteErrorCode != null && !ROUTE_NOT_FOUND_CODE.equals(remoteErrorCode)) {
            return new BusinessException(notFoundErrorCode);
        }

        log.warn("[Coaching] Judge Service 404가 리소스 미존재가 아닌 라우팅/계약 실패로 판단됨 "
                        + "— endpoint 배포 여부·경로·버전을 확인 필요. methodKey={}, remoteErrorCode={}",
                methodKey, remoteErrorCode);
        return new BusinessException(contractFailureErrorCode);
    }

    private String readErrorCode(String methodKey, Response response) {
        if (response.body() == null) {
            return null;
        }
        try {
            byte[] bodyBytes = response.body().asInputStream().readAllBytes();
            ErrorResponse errorResponse = objectMapper.readValue(bodyBytes, ErrorResponse.class);
            return errorResponse.error() != null ? errorResponse.error().code() : null;
        } catch (IOException | RuntimeException e) {
            log.warn("[Coaching] Judge Service 404 응답 본문을 표준 에러 포맷으로 파싱하지 못함. methodKey={}",
                    methodKey, e);
            return null;
        }
    }
}
