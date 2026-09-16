package com.maesamco.judge.presentation.internal_controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.maesamco.judge.application.query_service.SubmissionQueryService;
import com.maesamco.judge.application.result.SubmissionGetResult;
import com.maesamco.judge.domain.entity.SubmissionResult;
import com.maesamco.judge.domain.entity.SubmissionStatus;
import com.maesamco.judge.global.exception.BusinessException;
import com.maesamco.judge.global.exception.ErrorCode;
import com.maesamco.judge.global.security.hmac.InternalCallHeaders;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * ⚠️ 이슈 #175 반영 — SubmissionInternalController에 @AllowedInternalCallers가
 * 붙으면서, InternalCallerAuthorizationInterceptor가 이 @WebMvcTest 슬라이스에서도
 * 실제로 등록되어 동작한다는 게 CI로 확인됐다(addFilters = false는 서블릿 Filter만
 * 끄고 HandlerInterceptor는 끄지 않음). 그래서 X-Internal-Service 헤더 없이 보내던
 * 기존 요청들이 컨트롤러 도달 전에 403으로 막혀 테스트가 깨졌다.
 *
 * 이 클래스는 "조회 로직 자체가 맞는지"(성공/404)를 검증하는 게 목적이라, 인가
 * 자체를 검증하려는 게 아니다 — 허용된 호출자 헤더(content-service)를 추가해서
 * 인가 체크를 통과시키고 본래 목적(조회 로직)만 검증하도록 한다. 인가 체크 자체가
 * 실제로 강제되는지는 별도의 SubmissionInternalControllerAuthorizationTest에서
 * 검증한다.
 */
@WebMvcTest(SubmissionInternalController.class)
@AutoConfigureMockMvc(addFilters = false)
class SubmissionInternalControllerTest {

    private static final String ALLOWED_CALLER = "content-service";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SubmissionQueryService submissionQueryService;

    @Nested
    @DisplayName("GET /internal/v1/submissions/{submissionId}")
    class GetSubmission {

        @Test
        @DisplayName("조회에 성공하면 200과 success/data 포맷으로 응답한다")
        void returns200WithSubmission() throws Exception {
            UUID submissionId = UUID.randomUUID();
            SubmissionGetResult result = new SubmissionGetResult(
                    submissionId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "code",
                    SubmissionStatus.COMPLETED, SubmissionResult.WRONG, null,
                    List.of(new SubmissionGetResult.FailedTestItem(true, "WRONG_ANSWER")),
                    3
            );
            given(submissionQueryService.getSubmissionForInternal(any())).willReturn(result);

            mockMvc.perform(get("/internal/v1/submissions/{submissionId}", submissionId)
                            .header(InternalCallHeaders.SERVICE, ALLOWED_CALLER))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.data.problemVersionId").exists())
                    .andExpect(jsonPath("$.data.failedTestSummary[0].isPublic").value(true));
        }

        @Test
        @DisplayName("존재하지 않는 제출이면 404를 반환한다")
        void returns404WhenMissing() throws Exception {
            UUID submissionId = UUID.randomUUID();
            given(submissionQueryService.getSubmissionForInternal(any()))
                    .willThrow(new BusinessException(ErrorCode.SUBMISSION_NOT_FOUND));

            mockMvc.perform(get("/internal/v1/submissions/{submissionId}", submissionId)
                            .header(InternalCallHeaders.SERVICE, ALLOWED_CALLER))
                    .andExpect(status().isNotFound());
        }

        // ⚠️ P2 리뷰(이슈 #185) — 이 클래스는 @WebMvcTest + 실제 InternalCallerAuthorizationConfig가
        // 배선된 상태로 도는 judge-service의 유일한 테스트인데, 지금까지 허용된 호출자
        // 헤더만 보내고 거부 케이스가 없었다. SubmissionInternalControllerAuthorizationTest는
        // 인터셉터를 직접 새로 만들어 끼우는 방식이라, InternalCallerAuthorizationConfig
        // (실제 앱에서 인터셉터를 배선하는 진짜 설정 클래스) 자체가 judge-service에서
        // 거부 상황까지 실제로 관통되는지는 검증된 적이 없었다. 이 테스트로 그 공백을 메운다.
        @Test
        @DisplayName("허용되지 않은 호출자 헤더면 실제 Config 배선을 통해서도 403을 반환한다")
        void returns403WhenCallerNotAllowed() throws Exception {
            UUID submissionId = UUID.randomUUID();

            mockMvc.perform(get("/internal/v1/submissions/{submissionId}", submissionId)
                            .header(InternalCallHeaders.SERVICE, "some-other-service"))
                    .andExpect(status().isForbidden());
        }
    }
}
