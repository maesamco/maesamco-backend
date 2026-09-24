package com.maesamco.content.global.security.authorization;

import com.maesamco.content.application.facade.ProblemPublicationFacade;
import com.maesamco.content.application.persistence_service.CurriculumService;
import com.maesamco.content.application.persistence_service.LessonService;
import com.maesamco.content.application.persistence_service.ProblemService;
import com.maesamco.content.application.persistence_service.ProblemVersionService;
import com.maesamco.content.application.persistence_service.ProblemTagService;
import com.maesamco.content.application.persistence_service.TagService;
import com.maesamco.content.application.persistence_service.TestCaseService;
import com.maesamco.content.application.persistence_service.UnitService;
import com.maesamco.content.global.config.SecurityConfig;
import com.maesamco.content.presentation.api_controller.AdminProblemController;
import com.maesamco.content.presentation.api_controller.CurriculumController;
import com.maesamco.content.presentation.api_controller.LessonController;
import com.maesamco.content.presentation.api_controller.ProblemController;
import com.maesamco.content.presentation.api_controller.ProblemTagController;
import com.maesamco.content.presentation.api_controller.TagController;
import com.maesamco.content.presentation.api_controller.TestCaseController;
import com.maesamco.content.presentation.api_controller.UnitController;
import com.maesamco.content.support.ControllerEndpointScanner;
import com.maesamco.content.support.ControllerEndpointScanner.Endpoint;
import com.maesamco.content.support.TestJwt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code @RequireAdmin}이 붙은 모든 관리자 엔드포인트가 실제 {@link SecurityConfig}에서 USER 토큰을
 * 403으로 막는지 전 엔드포인트에 대해 검증한다(이슈 #311).
 *
 * <p>대상 엔드포인트는 {@link ControllerEndpointScanner}가 스캔한 결과에서 뽑으므로 새 관리자 API가
 * 추가되면 자동으로 검증 대상이 된다. 바디는 일부러 비워서({@code {}}) 보내 — 권한 검사가 바디 검증보다
 * 먼저 동작하는지(400이 아니라 403)도 함께 확인한다.</p>
 */
@WebMvcTest(controllers = {
        AdminProblemController.class,
        CurriculumController.class,
        LessonController.class,
        ProblemController.class,
        ProblemTagController.class,
        TagController.class,
        TestCaseController.class,
        UnitController.class
})
@Import(SecurityConfig.class)
class RequireAdminEnforcementTest {

    private static final Set<Class<?>> COVERED_CONTROLLERS = Set.of(
            AdminProblemController.class,
            CurriculumController.class,
            LessonController.class,
            ProblemController.class,
            ProblemTagController.class,
            TagController.class,
            TestCaseController.class,
            UnitController.class
    );

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProblemPublicationFacade problemPublicationFacade;
    @MockitoBean
    private CurriculumService curriculumService;
    @MockitoBean
    private LessonService lessonService;
    @MockitoBean
    private ProblemService problemService;
    @MockitoBean
    private ProblemVersionService problemVersionService;
    @MockitoBean
    private ProblemTagService problemTagService;
    @MockitoBean
    private TagService tagService;
    @MockitoBean
    private TestCaseService testCaseService;
    @MockitoBean
    private UnitService unitService;

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("jwt.public-key", TestJwt::publicKeyPem);
    }

    static Stream<Arguments> adminEndpoints() {
        return ControllerEndpointScanner.scan().stream()
                .filter(Endpoint::requiresAdmin)
                .map(e -> Arguments.of(e.signature(), e));
    }

    @Test
    @DisplayName("@RequireAdmin 엔드포인트를 가진 컨트롤러는 모두 이 테스트의 @WebMvcTest 대상에 포함돼 있다")
    void allAdminControllersAreCovered() {
        Set<Class<?>> adminControllers = ControllerEndpointScanner.scan().stream()
                .filter(Endpoint::requiresAdmin)
                .map(Endpoint::controller)
                .collect(java.util.stream.Collectors.toSet());

        assertThat(COVERED_CONTROLLERS)
                .as("새 관리자 컨트롤러를 추가했다면 이 테스트의 @WebMvcTest(controllers)와 @MockitoBean에 추가하세요")
                .containsAll(adminControllers);
    }

    @ParameterizedTest(name = "USER 토큰 + 빈 바디로 {0} 호출 → 403")
    @MethodSource("adminEndpoints")
    void userToken_isForbidden(String signature, Endpoint endpoint) throws Exception {
        String path = endpoint.path().replaceAll("\\{[^}]+}", UUID.randomUUID().toString());

        mockMvc.perform(
                        request(HttpMethod.valueOf(endpoint.httpMethod()), path)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwt.accessToken("USER"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}")
                )
                .andExpect(status().isForbidden());
    }
}
