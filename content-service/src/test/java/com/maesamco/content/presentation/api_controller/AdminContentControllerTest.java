package com.maesamco.content.presentation.api_controller;

import com.maesamco.content.application.persistence_service.CurriculumService;
import com.maesamco.content.application.persistence_service.LessonService;
import com.maesamco.content.application.persistence_service.UnitService;
import com.maesamco.content.application.result.CurriculumResult;
import com.maesamco.content.domain.entity.ContentStatus;
import com.maesamco.content.domain.entity.Curriculum;
import com.maesamco.content.domain.entity.Lesson;
import com.maesamco.content.domain.entity.ProgrammingLanguage;
import com.maesamco.content.domain.entity.Unit;
import com.maesamco.content.global.common.pagination.PageQuery;
import com.maesamco.content.global.common.pagination.PageResult;
import com.maesamco.content.global.response.PageResponse;
import com.maesamco.content.presentation.response.LessonResponse;
import com.maesamco.content.presentation.response.UnitResponse;
import com.maesamco.content.support.TestSecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminContentController.class)
@Import(TestSecurityConfig.class)
@DisplayName("AdminContentController (#359)")
class AdminContentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurriculumService curriculumService;

    @MockitoBean
    private UnitService unitService;

    @MockitoBean
    private LessonService lessonService;

    private final UUID adminId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @Test
    @DisplayName("ADMIN은 공개 상태와 관계없이 커리큘럼 목록을 조회하고, 응답에 status가 포함된다")
    void getCurriculums_admin_returnsAllStatusesWithStatusField() throws Exception {
        // given
        UUID curriculumId = UUID.randomUUID();
        CurriculumResult draft = CurriculumResult.from(curriculum(curriculumId, ContentStatus.DRAFT));
        when(curriculumService.searchCurriculums(any(PageQuery.class)))
                .thenReturn(new PageResult<>(List.of(draft), 0, 20, 1));

        // when & then
        mockMvc.perform(get("/api/v1/admin/contents/curriculums").with(asAdmin(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(curriculumId.toString()))
                .andExpect(jsonPath("$.data.content[0].status").value("DRAFT"));

        // 학습자용(...ForUser)이 아니라 전체 조회 메서드를 사용한다
        verify(curriculumService).searchCurriculums(any(PageQuery.class));
    }

    @Test
    @DisplayName("ADMIN은 DRAFT 유닛도 단건 조회할 수 있다")
    void getUnit_admin_returnsDraftUnit() throws Exception {
        // given
        UUID unitId = UUID.randomUUID();
        // 헬퍼 안에서도 when(...)을 쓰므로 스터빙 중첩을 피하려고 응답을 먼저 만든다
        UnitResponse draftUnit = UnitResponse.from(unit(unitId, ContentStatus.DRAFT));
        when(unitService.getUnit(unitId)).thenReturn(draftUnit);

        // when & then
        mockMvc.perform(get("/api/v1/admin/contents/units/{unitId}", unitId).with(asAdmin(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(unitId.toString()))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    @DisplayName("ADMIN은 유닛별 레슨 목록을 공개 상태와 관계없이 조회한다")
    void getLessons_admin_usesAdminSearch() throws Exception {
        // given
        UUID unitId = UUID.randomUUID();
        UUID lessonId = UUID.randomUUID();
        PageResponse<LessonResponse> page = new PageResponse<>(
                List.of(LessonResponse.from(lesson(lessonId, unitId, ContentStatus.DRAFT))), 0, 20, 1, 1, false);
        when(lessonService.searchLessons(eq(unitId), any(Pageable.class))).thenReturn(page);

        // when & then
        mockMvc.perform(get("/api/v1/admin/contents/lessons")
                        .param("unitId", unitId.toString())
                        .with(asAdmin(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].status").value("DRAFT"));

        verify(lessonService).searchLessons(eq(unitId), any(Pageable.class));
    }

    @Test
    @DisplayName("ADMIN이 커리큘럼을 공개하면 PUBLISHED 상태가 응답된다")
    void publishCurriculum_admin_returnsPublished() throws Exception {
        // given
        UUID curriculumId = UUID.randomUUID();
        CurriculumResult published = CurriculumResult.from(curriculum(curriculumId, ContentStatus.PUBLISHED));
        when(curriculumService.publishCurriculum(curriculumId)).thenReturn(published);

        // when & then
        mockMvc.perform(patch("/api/v1/admin/contents/curriculums/{curriculumId}/publish", curriculumId)
                        .with(asAdmin(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        verify(curriculumService).publishCurriculum(curriculumId);
    }

    @Test
    @DisplayName("ADMIN이 유닛·레슨을 비공개하면 DRAFT 상태가 응답된다")
    void unpublishUnitAndLesson_admin_returnsDraft() throws Exception {
        // given
        UUID unitId = UUID.randomUUID();
        UUID lessonId = UUID.randomUUID();
        UnitResponse draftUnit = UnitResponse.from(unit(unitId, ContentStatus.DRAFT));
        LessonResponse draftLesson = LessonResponse.from(lesson(lessonId, unitId, ContentStatus.DRAFT));
        when(unitService.unpublishUnit(unitId)).thenReturn(draftUnit);
        when(lessonService.unpublishLesson(lessonId)).thenReturn(draftLesson);

        // when & then
        mockMvc.perform(patch("/api/v1/admin/contents/units/{unitId}/unpublish", unitId).with(asAdmin(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
        mockMvc.perform(patch("/api/v1/admin/contents/lessons/{lessonId}/unpublish", lessonId).with(asAdmin(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        verify(unitService).unpublishUnit(unitId);
        verify(lessonService).unpublishLesson(lessonId);
    }

    @Test
    @DisplayName("ADMIN이 아닌 사용자는 공개 상태를 바꿀 수 없고 서비스도 호출되지 않는다")
    void publishLesson_nonAdmin_returns403() throws Exception {
        // when & then
        mockMvc.perform(patch("/api/v1/admin/contents/lessons/{lessonId}/publish", UUID.randomUUID())
                        .with(asUser(userId)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(lessonService);
    }

    private static Curriculum curriculum(UUID id, ContentStatus status) {
        Curriculum curriculum = mock(Curriculum.class);
        when(curriculum.getId()).thenReturn(id);
        when(curriculum.getTitle()).thenReturn("Java");
        when(curriculum.getLanguage()).thenReturn(ProgrammingLanguage.JAVA);
        when(curriculum.getStatus()).thenReturn(status);
        return curriculum;
    }

    private static Unit unit(UUID id, ContentStatus status) {
        Unit unit = mock(Unit.class);
        when(unit.getId()).thenReturn(id);
        when(unit.getCurriculumId()).thenReturn(UUID.randomUUID());
        when(unit.getTitle()).thenReturn("기초");
        when(unit.getLanguage()).thenReturn(ProgrammingLanguage.JAVA);
        when(unit.getDisplayOrder()).thenReturn(1);
        when(unit.getStatus()).thenReturn(status);
        return unit;
    }

    private static Lesson lesson(UUID id, UUID unitId, ContentStatus status) {
        Lesson lesson = mock(Lesson.class);
        when(lesson.getId()).thenReturn(id);
        when(lesson.getUnitId()).thenReturn(unitId);
        when(lesson.getTitle()).thenReturn("변수");
        when(lesson.getLanguage()).thenReturn(ProgrammingLanguage.JAVA);
        when(lesson.getDisplayOrder()).thenReturn(1);
        when(lesson.getStatus()).thenReturn(status);
        return lesson;
    }

    private static RequestPostProcessor asAdmin(UUID adminId) {
        return authentication(
                new UsernamePasswordAuthenticationToken(
                        adminId,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                )
        );
    }

    private static RequestPostProcessor asUser(UUID userId) {
        return authentication(
                new UsernamePasswordAuthenticationToken(
                        userId,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                )
        );
    }
}
