package com.maesamco.content.presentation.Internal;

import com.maesamco.content.application.persistence_service.ConceptValidationInternalService;
import com.maesamco.content.application.result.ConceptValidationInternalResult;
import com.maesamco.content.global.security.hmac.InternalCallHeaders;
import com.maesamco.content.presentation.internal_controller.InternalConceptController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * {@link InternalConceptController}의 HTTP 요청/응답을 검증한다(이슈 #309).
 *
 * <p>HMAC 인증/인가 Filter는 {@code InternalConceptControllerAuthorizationTest}와
 * {@code InternalConceptControllerConfigWiringTest}에서 별도로 검증하므로, 이 테스트는
 * Servlet Filter를 비활성화하고 Controller 자체의 책임에 집중한다
 * (InternalProblemControllerTest와 동일한 패턴).</p>
 */
@WebMvcTest(InternalConceptController.class)
@AutoConfigureMockMvc(addFilters = false)
class InternalConceptControllerTest {

    private static final String URL = "/internal/v1/concepts/validate";

    // InternalCallerAuthorizationInterceptor를 통과할 수 있는 허용된 내부 서비스명을 사용한다.
    private static final String ALLOWED_CALLER = "user-service";

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConceptValidationInternalService conceptValidationInternalService;

    @Nested
    @DisplayName("개념 일괄 검증")
    class ValidateConcepts {

        @Test
        @DisplayName("모든 개념이 유효하면 200과 valid=true를 반환한다")
        void validateConcepts_allValid_returns200() throws Exception {

            // given
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();

            ConceptValidationInternalResult result =
                    new ConceptValidationInternalResult(true, List.of(id1, id2), List.of());

            when(conceptValidationInternalService.validate(List.of(id1, id2)))
                    .thenReturn(result);

            // when & then
            mockMvc.perform(internalPost(ids(id1, id2)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.valid").value(true))
                    .andExpect(jsonPath("$.data.validConceptIds", hasSize(2)))
                    .andExpect(jsonPath("$.data.invalidConceptIds", hasSize(0)));

            verify(conceptValidationInternalService).validate(List.of(id1, id2));
        }

        @Test
        @DisplayName("존재하지 않는 개념이 섞여 있으면 200과 valid=false, invalidConceptIds를 반환한다")
        void validateConcepts_someInvalid_returnsValidFalse() throws Exception {

            // given
            UUID validId = UUID.randomUUID();
            UUID invalidId = UUID.randomUUID();

            ConceptValidationInternalResult result =
                    new ConceptValidationInternalResult(
                            false, List.of(validId), List.of(invalidId)
                    );

            when(conceptValidationInternalService.validate(List.of(validId, invalidId)))
                    .thenReturn(result);

            // when & then
            mockMvc.perform(internalPost(ids(validId, invalidId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.valid").value(false))
                    .andExpect(jsonPath("$.data.validConceptIds[0]").value(validId.toString()))
                    .andExpect(jsonPath("$.data.invalidConceptIds[0]").value(invalidId.toString()));
        }

        @Test
        @DisplayName("conceptIds가 빈 배열이면 400을 반환하고 서비스를 호출하지 않는다")
        void validateConcepts_emptyConceptIds_returns400() throws Exception {

            // when & then
            mockMvc.perform(internalPost(ids()))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(conceptValidationInternalService);
        }

        @Test
        @DisplayName("conceptIds 필드 자체가 없으면 400을 반환하고 서비스를 호출하지 않는다")
        void validateConcepts_missingField_returns400() throws Exception {

            // when & then
            mockMvc.perform(internalPost("{}"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(conceptValidationInternalService);
        }

        @Test
        @DisplayName("conceptIds가 상한(100개)이면 정상 처리한다")
        void validateConcepts_atUpperBound_returns200() throws Exception {

            // given
            List<UUID> hundred = IntStream.range(0, 100).mapToObj(i -> UUID.randomUUID()).toList();

            when(conceptValidationInternalService.validate(hundred))
                    .thenReturn(new ConceptValidationInternalResult(true, hundred, List.of()));

            // when & then
            mockMvc.perform(internalPost(ids(hundred.toArray())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.valid").value(true));
        }

        @Test
        @DisplayName("conceptIds가 상한(100개)을 넘으면 400을 반환하고 서비스를 호출하지 않는다")
        void validateConcepts_overUpperBound_returns400() throws Exception {

            // given
            Object[] overLimit = IntStream.range(0, 101).mapToObj(i -> UUID.randomUUID()).toArray();

            // when & then
            mockMvc.perform(internalPost(ids(overLimit)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(conceptValidationInternalService);
        }

        @Test
        @DisplayName("conceptIds 요소가 UUID 형식이 아니면 400을 반환하고 서비스를 호출하지 않는다")
        void validateConcepts_invalidUuidElement_returns400() throws Exception {

            // when & then
            mockMvc.perform(internalPost(ids("not-a-uuid")))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(conceptValidationInternalService);
        }
    }

    private static String ids(Object... conceptIds) {
        return JSON_MAPPER.writeValueAsString(Map.of("conceptIds", List.of(conceptIds)));
    }

    private MockHttpServletRequestBuilder internalPost(String json) {
        return post(URL)
                .header(InternalCallHeaders.SERVICE, ALLOWED_CALLER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
    }
}
