package com.maesamco.content.presentation.api_controller;

import com.maesamco.content.application.persistence_service.CurriculumService;
import com.maesamco.content.domain.entity.Curriculum;
import com.maesamco.content.domain.entity.ProgrammingLanguage;
import com.maesamco.content.application.command.CurriculumCreateCommand;
import com.maesamco.content.application.command.CurriculumUpdateCommand;
import com.maesamco.content.application.result.CurriculumResult;
import com.maesamco.content.global.common.pagination.PageQuery;
import com.maesamco.content.global.common.pagination.PageResult;
import com.maesamco.content.support.TestSecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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

@WebMvcTest(CurriculumController.class)
@Import(TestSecurityConfig.class)
class CurriculumControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurriculumService curriculumService;

    private final UUID curriculumId =
            UUID.randomUUID();

    private final UUID adminId =
            UUID.randomUUID();

    private final UUID userId =
            UUID.randomUUID();

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
    @DisplayName("ADMIN이 커리큘럼을 생성하면 201과 생성 정보를 반환한다")
    void createCurriculum_admin_returns201() throws Exception {

        // given
        Curriculum curriculum =
                createCurriculum(
                        curriculumId,
                        "Java 기초",
                        ProgrammingLanguage.JAVA,
                        1
                );

        when(
                curriculumService.createCurriculum(
                        any(CurriculumCreateCommand.class)
                )
        ).thenReturn(
                CurriculumResult.from(curriculum)
        );

        String json = """
                {
                    "title": "Java 기초",
                    "language": "JAVA"
                }
                """;

        // when & then
        mockMvc.perform(
                        post("/api/v1/contents/curriculums")
                                .with(asAdmin(adminId))
                                .contentType("application/json")
                                .content(json)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(
                        jsonPath("$.data.id")
                                .value(curriculumId.toString())
                )
                .andExpect(
                        jsonPath("$.data.title")
                                .value("Java 기초")
                );

        ArgumentCaptor<CurriculumCreateCommand> captor =
                ArgumentCaptor.forClass(
                        CurriculumCreateCommand.class
                );

        verify(curriculumService)
                .createCurriculum(captor.capture());

        assertThat(captor.getValue().getTitle())
                .isEqualTo("Java 기초");

        assertThat(captor.getValue().getLanguage())
                .isEqualTo(ProgrammingLanguage.JAVA);
    }

    @Test
    @DisplayName("ADMIN이 아닌 사용자가 커리큘럼을 생성하면 403을 반환한다")
    void createCurriculum_nonAdmin_returns403() throws Exception {

        String json = """
                {
                    "title": "Java 기초",
                    "language": "JAVA"
                }
                """;

        mockMvc.perform(
                        post("/api/v1/contents/curriculums")
                                .with(asUser(userId))
                                .contentType("application/json")
                                .content(json)
                )
                .andExpect(status().isForbidden());

        verifyNoInteractions(curriculumService);
    }

    @Test
    @DisplayName("인증 사용자가 커리큘럼을 단건 조회하면 200을 반환한다")
    void getCurriculum_authenticated_returns200() throws Exception {

        // given
        Curriculum curriculum =
                createCurriculum(
                        curriculumId,
                        "Java 기초",
                        ProgrammingLanguage.JAVA,
                        1
                );

        when(curriculumService.getCurriculumForUser(curriculumId))
                .thenReturn(
                        CurriculumResult.from(curriculum)
                );

        // when & then
        mockMvc.perform(
                        get(
                                "/api/v1/contents/curriculums/{curriculumId}",
                                curriculumId
                        )
                                .with(asUser(userId))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(
                        jsonPath("$.data.id")
                                .value(curriculumId.toString())
                )
                .andExpect(
                        jsonPath("$.data.title")
                                .value("Java 기초")
                )
                .andExpect(
                        jsonPath("$.data.language")
                                .value("JAVA")
                );

        verify(curriculumService)
                .getCurriculumForUser(curriculumId);
    }

    @Test
    @DisplayName("인증 사용자가 커리큘럼 목록을 조회하면 페이징 결과를 반환한다")
    void getCurriculums_authenticated_returnsPage() throws Exception {

        // given
        Curriculum curriculum =
                createCurriculum(
                        curriculumId,
                        "Java 기초",
                        ProgrammingLanguage.JAVA,
                        1
                );

        PageResult<CurriculumResult> response =
                new PageResult<>(
                        List.of(
                                CurriculumResult.from(curriculum)
                        ),
                        1,
                        5,
                        6
                );

        when(
                curriculumService.searchCurriculumsForUser(
                        any(PageQuery.class)
                )
        ).thenReturn(response);

        // when & then
        mockMvc.perform(
                        get("/api/v1/contents/curriculums")
                                .with(asUser(userId))
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

        ArgumentCaptor<PageQuery> captor =
                ArgumentCaptor.forClass(PageQuery.class);

        verify(curriculumService)
                .searchCurriculumsForUser(captor.capture());

        assertThat(captor.getValue().page())
                .isEqualTo(1);

        assertThat(captor.getValue().size())
                .isEqualTo(5);
    }

    @Test
    @DisplayName("ADMIN이 커리큘럼을 수정하면 200과 수정된 정보를 반환한다")
    void updateCurriculum_admin_returns200() throws Exception {

        // given
        Curriculum curriculum =
                createCurriculum(
                        curriculumId,
                        "Python 기초",
                        ProgrammingLanguage.PYTHON,
                        1
                );

        when(
                curriculumService.updateCurriculum(
                        eq(curriculumId),
                        any(CurriculumUpdateCommand.class)
                )
        ).thenReturn(
                CurriculumResult.from(curriculum)
        );

        String json = """
                {
                    "title": "Python 기초",
                    "language": "PYTHON"
                }
                """;

        // when & then
        mockMvc.perform(
                        patch(
                                "/api/v1/contents/curriculums/{curriculumId}",
                                curriculumId
                        )
                                .with(asAdmin(adminId))
                                .contentType("application/json")
                                .content(json)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(
                        jsonPath("$.data.title")
                                .value("Python 기초")
                )
                .andExpect(
                        jsonPath("$.data.language")
                                .value("PYTHON")
                );

        verify(curriculumService)
                .updateCurriculum(
                        eq(curriculumId),
                        any(CurriculumUpdateCommand.class)
                );
    }

    @Test
    @DisplayName("ADMIN이 커리큘럼을 삭제하면 200을 반환하고 사용자 ID를 전달한다")
    void deleteCurriculum_admin_returns200() throws Exception {

        // when & then
        mockMvc.perform(
                        delete(
                                "/api/v1/contents/curriculums/{curriculumId}",
                                curriculumId
                        )
                                .with(asAdmin(adminId))
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.success")
                                .value(true)
                );

        verify(curriculumService)
                .deleteCurriculum(
                        curriculumId,
                        adminId
                );
    }

    @Test
    @DisplayName("ADMIN이 아닌 사용자가 커리큘럼을 삭제하면 403을 반환한다")
    void deleteCurriculum_nonAdmin_returns403() throws Exception {

        mockMvc.perform(
                        delete(
                                "/api/v1/contents/curriculums/{curriculumId}",
                                curriculumId
                        )
                                .with(asUser(userId))
                )
                .andExpect(status().isForbidden());

        verifyNoInteractions(curriculumService);
    }

    private Curriculum createCurriculum(
            UUID id,
            String title,
            ProgrammingLanguage language,
            Integer displayOrder
    ) {
        Curriculum curriculum =
                Curriculum.create(
                        title,
                        language
                );

        ReflectionTestUtils.setField(
                curriculum,
                "id",
                id
        );

        return curriculum;
    }
}