package com.maesamco.content.presentation.api_controller;

import com.maesamco.content.application.persistence_service.UnitService;
import com.maesamco.content.domain.entity.ProgrammingLanguage;
import com.maesamco.content.domain.entity.Unit;
import com.maesamco.content.global.response.PageResponse;
import com.maesamco.content.presentation.request.UnitCreateRequest;
import com.maesamco.content.presentation.request.UnitUpdateRequest;
import com.maesamco.content.presentation.response.UnitCreateResponse;
import com.maesamco.content.presentation.response.UnitResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UnitController.class)
@Import(UnitControllerTest.TestSecurityConfig.class)
class UnitControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UnitService unitService;

    private final UUID unitId = UUID.randomUUID();
    private final UUID curriculumId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @TestConfiguration
    @EnableMethodSecurity(proxyTargetClass = true)
    static class TestSecurityConfig {

        @Bean
        SecurityFilterChain testSecurityFilterChain(
                HttpSecurity http
        ) throws Exception {

            http.csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(
                            auth -> auth.anyRequest().permitAll()
                    );

            return http.build();
        }
    }

    private static RequestPostProcessor asAdmin(UUID adminId) {
        return authentication(
                new UsernamePasswordAuthenticationToken(
                        adminId,
                        null,
                        List.of(
                                new SimpleGrantedAuthority("ROLE_ADMIN")
                        )
                )
        );
    }

    private static RequestPostProcessor asUser(UUID userId) {
        return authentication(
                new UsernamePasswordAuthenticationToken(
                        userId,
                        null,
                        List.of(
                                new SimpleGrantedAuthority("ROLE_USER")
                        )
                )
        );
    }

    @Test
    @DisplayName("ADMIN이 유닛을 생성하면 201과 생성 정보를 반환한다")
    void createUnit_admin_returns201() throws Exception {

        // given
        Unit unit = createUnit(
                unitId,
                curriculumId,
                "Java 기본 문법",
                ProgrammingLanguage.JAVA,
                1
        );

        when(
                unitService.createUnit(
                        any(UnitCreateRequest.class)
                )
        ).thenReturn(
                UnitCreateResponse.from(unit)
        );

        String json = """
            {
                "curriculumId": "%s",
                "title": "Java 기본 문법",
                "language": "JAVA",
                "displayOrder": 1
            }
            """.formatted(curriculumId);

        // when & then
        mockMvc.perform(
                        post("/api/v1/contents/units")
                                .with(asAdmin(adminId))
                                .contentType("application/json")
                                .content(json)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(
                        jsonPath("$.data.id")
                                .value(unitId.toString())
                )
                .andExpect(
                        jsonPath("$.data.title")
                                .value("Java 기본 문법")
                );

        ArgumentCaptor<UnitCreateRequest> captor =
                ArgumentCaptor.forClass(
                        UnitCreateRequest.class
                );

        verify(unitService)
                .createUnit(captor.capture());

        assertThat(captor.getValue().getCurriculumId())
                .isEqualTo(curriculumId);

        assertThat(captor.getValue().getTitle())
                .isEqualTo("Java 기본 문법");

        assertThat(captor.getValue().getLanguage())
                .isEqualTo(ProgrammingLanguage.JAVA);

        assertThat(captor.getValue().getDisplayOrder())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("ADMIN이 아닌 사용자가 유닛을 생성하면 403을 반환한다")
    void createUnit_nonAdmin_returns403() throws Exception {

        String json = """
            {
                "curriculumId": "%s",
                "title": "Java 기본 문법",
                "language": "JAVA",
                "displayOrder": 1
            }
            """.formatted(curriculumId);

        mockMvc.perform(
                        post("/api/v1/contents/units")
                                .with(asUser(userId))
                                .contentType("application/json")
                                .content(json)
                )
                .andExpect(status().isForbidden());

        verifyNoInteractions(unitService);
    }

    @Test
    @DisplayName("이슈 #311 — ADMIN이 아닌 사용자가 검증 실패하는(필수 필드 누락) 바디로 요청해도 400이 아니라 403을 반환한다")
    void createUnit_nonAdmin_withInvalidBody_returns403NotBadRequest() throws Exception {

        // given — language 필드가 빠진, 그 자체로 @Valid 검증에 실패하는 바디.
        String invalidJson = """
            {
                "curriculumId": "%s",
                "title": "Java 기본 문법",
                "displayOrder": 1
            }
            """.formatted(curriculumId);

        // when & then — 검증 실패(400)보다 권한 검사(403)가 항상 먼저 응답돼야 한다.
        mockMvc.perform(
                        post("/api/v1/contents/units")
                                .with(asUser(userId))
                                .contentType("application/json")
                                .content(invalidJson)
                )
                .andExpect(status().isForbidden());

        verifyNoInteractions(unitService);
    }

    @Test
    @DisplayName("인증 사용자가 유닛을 단건 조회하면 200을 반환한다")
    void getUnit_authenticated_returns200() throws Exception {

        // given
        Unit unit = createUnit(
                unitId,
                curriculumId,
                "Java 기본 문법",
                ProgrammingLanguage.JAVA,
                1
        );

        when(unitService.getUnit(unitId))
                .thenReturn(
                        UnitResponse.from(unit)
                );

        // when & then
        mockMvc.perform(
                        get(
                                "/api/v1/contents/units/{unitId}",
                                unitId
                        )
                                .with(asUser(userId))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(
                        jsonPath("$.data.id")
                                .value(unitId.toString())
                )
                .andExpect(
                        jsonPath("$.data.curriculumId")
                                .value(curriculumId.toString())
                )
                .andExpect(
                        jsonPath("$.data.title")
                                .value("Java 기본 문법")
                );

        verify(unitService)
                .getUnit(unitId);
    }

    @Test
    @DisplayName("특정 커리큘럼의 유닛 목록을 페이징 조회한다")
    void getUnits_authenticated_returnsPage() throws Exception {

        // given
        Unit unit = createUnit(
                unitId,
                curriculumId,
                "Java 기본 문법",
                ProgrammingLanguage.JAVA,
                1
        );

        PageResponse<UnitResponse> response =
                PageResponse.from(
                        new PageImpl<>(
                                List.of(
                                        UnitResponse.from(unit)
                                ),
                                PageRequest.of(1, 5),
                                6
                        )
                );

        when(
                unitService.searchUnits(
                        eq(curriculumId),
                        any(Pageable.class)
                )
        ).thenReturn(response);

        // when & then
        mockMvc.perform(
                        get("/api/v1/contents/units")
                                .with(asUser(userId))
                                .param(
                                        "curriculumId",
                                        curriculumId.toString()
                                )
                                .param("page", "1")
                                .param("size", "5")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(
                        jsonPath("$.data.content.length()")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.data.page")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.data.size")
                                .value(5)
                )
                .andExpect(
                        jsonPath("$.data.totalElements")
                                .value(6)
                );

        ArgumentCaptor<Pageable> captor =
                ArgumentCaptor.forClass(
                        Pageable.class
                );

        verify(unitService)
                .searchUnits(
                        eq(curriculumId),
                        captor.capture()
                );

        assertThat(captor.getValue().getPageNumber())
                .isEqualTo(1);

        assertThat(captor.getValue().getPageSize())
                .isEqualTo(5);
    }

    @Test
    @DisplayName("ADMIN이 유닛을 수정하면 200과 수정된 정보를 반환한다")
    void updateUnit_admin_returns200() throws Exception {

        // given
        Unit unit = createUnit(
                unitId,
                curriculumId,
                "Java 객체지향",
                ProgrammingLanguage.JAVA,
                1
        );

        when(
                unitService.updateUnit(
                        eq(unitId),
                        any(UnitUpdateRequest.class)
                )
        ).thenReturn(
                UnitResponse.from(unit)
        );

        String json = """
                {
                    "title": "Java 객체지향"
                }
                """;

        // when & then
        mockMvc.perform(
                        patch(
                                "/api/v1/contents/units/{unitId}",
                                unitId
                        )
                                .with(asAdmin(adminId))
                                .contentType("application/json")
                                .content(json)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(
                        jsonPath("$.data.title")
                                .value("Java 객체지향")
                );

        verify(unitService)
                .updateUnit(
                        eq(unitId),
                        any(UnitUpdateRequest.class)
                );
    }

    @Test
    @DisplayName("ADMIN이 유닛을 삭제하면 200을 반환하고 사용자 ID를 전달한다")
    void deleteUnit_admin_returns200() throws Exception {

        // when & then
        mockMvc.perform(
                        delete(
                                "/api/v1/contents/units/{unitId}",
                                unitId
                        )
                                .with(asAdmin(adminId))
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.success")
                                .value(true)
                );

        verify(unitService)
                .deleteUnit(
                        unitId,
                        adminId
                );
    }

    @Test
    @DisplayName("ADMIN이 아닌 사용자가 유닛을 삭제하면 403을 반환한다")
    void deleteUnit_nonAdmin_returns403() throws Exception {

        mockMvc.perform(
                        delete(
                                "/api/v1/contents/units/{unitId}",
                                unitId
                        )
                                .with(asUser(userId))
                )
                .andExpect(status().isForbidden());

        verifyNoInteractions(unitService);
    }

    private Unit createUnit(
            UUID id,
            UUID curriculumId,
            String title,
            ProgrammingLanguage language,
            Integer displayOrder
    ) {
        Unit unit = Unit.create(
                curriculumId,
                title,
                language,
                displayOrder
        );

        ReflectionTestUtils.setField(
                unit,
                "id",
                id
        );

        return unit;
    }
}