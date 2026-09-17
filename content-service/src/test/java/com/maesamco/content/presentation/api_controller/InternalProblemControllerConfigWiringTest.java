package com.maesamco.content.problem.presentation.controller;

import com.maesamco.content.application.problem.service.ProblemInternalService;
import com.maesamco.content.global.security.hmac.InternalCallHeaders;
import com.maesamco.content.presentation.problem.controller.InternalProblemController;

import com.maesamco.content.presentation.problem.dto.response.InternalProblemResponse;
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

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code InternalProblemController}에 붙은 {@code @AllowedInternalCallers({"coaching-service"})}가
 * 실제 프로덕션 {@code InternalCallerAuthorizationConfig} 빈(컴포넌트 스캔으로 등록)을
 * 통해 관통되는지 검증한다(이슈 #175 P2 재리뷰 — coaching의 WeakConceptInternalControllerTest,
 * judge의 SubmissionInternalControllerTest와 동일 패턴).
 *
 * {@code InternalProblemControllerAuthorizationTest}(Testcontainers 기반,
 * standaloneSetup으로 인터셉터를 직접 등록)는 HMAC 서명 검증까지 end-to-end로 확인한다는
 * 점에서 그 자체로도 가치가 있어 남겨두고, 이 클래스는 그와 병행해 "진짜 Config가
 * 실제로 등록·관통되는지"만 별도로 확인한다. {@code InternalCallerAuthorizationConfig}의
 * addPathPatterns 설정이 실수로 지워지거나 컴포넌트 스캔 범위가 바뀌어도, standaloneSetup
 * 방식의 테스트만으로는 이걸 잡을 수 없다 — 이 클래스가 그 공백을 메운다.
 */
@WebMvcTest(InternalProblemController.class)
@AutoConfigureMockMvc(addFilters = false)
class InternalProblemControllerConfigWiringTest {

    private static final String ALLOWED_CALLER = "coaching-service";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProblemInternalService problemInternalService;

    @Nested
    @DisplayName("GET /internal/v1/problems/{problemId}")
    class GetProblem {

        @Test
        @DisplayName("허용된 호출자(coaching-service) 헤더면 실제 Config 배선을 통해 200을 반환한다")
        void returns200ForAllowedCaller() throws Exception {
            UUID problemId = UUID.randomUUID();
            given(problemInternalService.getProblemMetaData(problemId))
                    .willReturn(new InternalProblemResponse(problemId, "설명", List.of("배열")));

            mockMvc.perform(get("/internal/v1/problems/{problemId}", problemId)
                            .header(InternalCallHeaders.SERVICE, ALLOWED_CALLER))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("허용되지 않은 호출자 헤더면 실제 Config 배선을 통해서도 403을 반환한다")
        void returns403WhenCallerNotAllowed() throws Exception {
            UUID problemId = UUID.randomUUID();

            mockMvc.perform(get("/internal/v1/problems/{problemId}", problemId)
                            .header(InternalCallHeaders.SERVICE, "some-other-service"))
                    .andExpect(status().isForbidden());
        }
    }
}