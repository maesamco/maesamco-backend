package com.maesamco.content.presentation.Internal;

import com.maesamco.content.application.persistence_service.ConceptValidationInternalService;
import com.maesamco.content.application.result.ConceptValidationInternalResult;
import com.maesamco.content.global.security.hmac.InternalCallHeaders;
import com.maesamco.content.presentation.internal_controller.InternalConceptController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code InternalConceptController}에 붙은 {@code @AllowedInternalCallers({"user-service"})}가
 * 실제 프로덕션 {@code InternalCallerAuthorizationConfig} 빈(컴포넌트 스캔으로 등록)을 통해
 * 관통되는지 검증한다(이슈 #309, InternalProblemControllerConfigWiringTest와 동일 패턴).
 *
 * <p>{@code InternalConceptControllerAuthorizationTest}(Testcontainers 기반, standaloneSetup으로
 * 인터셉터를 직접 등록)는 HMAC 서명 검증까지 end-to-end로 확인하지만, {@code InternalCallerAuthorizationConfig}의
 * addPathPatterns 설정이 실수로 지워지거나 컴포넌트 스캔 범위가 바뀌는 것은 그 방식만으로는
 * 못 잡는다 — 이 클래스가 그 공백을 메운다.</p>
 */
@WebMvcTest(InternalConceptController.class)
@AutoConfigureMockMvc(addFilters = false)
class InternalConceptControllerConfigWiringTest {

    private static final String URL = "/internal/v1/concepts/validate";
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private static final String ALLOWED_CALLER = "user-service";
    private static final String DISALLOWED_CALLER = "coaching-service";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConceptValidationInternalService conceptValidationInternalService;

    @Test
    @DisplayName("허용된 호출자(user-service) 헤더면 실제 Config 배선을 통해 200을 반환한다")
    void returns200ForAllowedCaller() throws Exception {

        // given
        UUID conceptId = UUID.randomUUID();

        given(conceptValidationInternalService.validate(List.of(conceptId)))
                .willReturn(new ConceptValidationInternalResult(true, List.of(conceptId), List.of()));

        // when & then
        mockMvc.perform(
                        post(URL)
                                .header(InternalCallHeaders.SERVICE, ALLOWED_CALLER)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(JSON_MAPPER.writeValueAsString(Map.of("conceptIds", List.of(conceptId))))
                )
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("허용되지 않은 호출자(coaching-service) 헤더면 실제 Config 배선을 통해 403을 반환한다")
    void returns403ForDisallowedCaller() throws Exception {

        // given
        UUID conceptId = UUID.randomUUID();

        // when & then — InternalProblemController와 달리 coaching-service는 이 API 호출자로 허용되지 않는다.
        mockMvc.perform(
                        post(URL)
                                .header(InternalCallHeaders.SERVICE, DISALLOWED_CALLER)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(JSON_MAPPER.writeValueAsString(Map.of("conceptIds", List.of(conceptId))))
                )
                .andExpect(status().isForbidden());
    }
}
