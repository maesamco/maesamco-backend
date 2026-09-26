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
import org.junit.jupiter.api.Nested;
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
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 관리자 콘텐츠 API가 엔드포인트마다 정확히 기대하는 서비스 메서드 하나만 호출하는지 고정합니다(#359, #366 리뷰 P3).
 *
 * <p>각 테스트는 기대 메서드를 verify한 뒤 {@code verifyNoMoreInteractions}로 다른 호출을 막습니다.
 * 그래서 공개가 비공개를 호출하거나, 관리자 조회가 학습자용 ...ForUser를 호출하는 회귀를 잡습니다.</p>
 */
@WebMvcTest(AdminContentController.class)
@Import(TestSecurityConfig.class)
@DisplayName("AdminContentController (#359)")
class AdminContentControllerTest {

    private static final String BASE = "/api/v1/admin/contents";

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

    @Nested
    @DisplayName("Curriculum")
    class CurriculumEndpoints {

        @Test
        @DisplayName("목록 조회는 상태와 관계없는 searchCurriculums만 호출하고 status를 응답한다")
        void getCurriculums_callsAdminSearchOnly() throws Exception {
            // given
            UUID curriculumId = UUID.randomUUID();
            CurriculumResult draft = CurriculumResult.from(curriculum(curriculumId, ContentStatus.DRAFT));
            when(curriculumService.searchCurriculums(any(PageQuery.class)))
                    .thenReturn(new PageResult<>(List.of(draft), 0, 20, 1));

            // when & then
            mockMvc.perform(get(BASE + "/curriculums").with(asAdmin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].id").value(curriculumId.toString()))
                    .andExpect(jsonPath("$.data.content[0].status").value("DRAFT"));

            verify(curriculumService).searchCurriculums(any(PageQuery.class));
            verifyNoMoreServiceCalls();
        }

        @Test
        @DisplayName("단건 조회는 상태와 관계없는 getCurriculum만 호출한다")
        void getCurriculum_callsAdminGetOnly() throws Exception {
            // given
            UUID curriculumId = UUID.randomUUID();
            CurriculumResult draft = CurriculumResult.from(curriculum(curriculumId, ContentStatus.DRAFT));
            when(curriculumService.getCurriculum(curriculumId)).thenReturn(draft);

            // when & then
            mockMvc.perform(get(BASE + "/curriculums/{id}", curriculumId).with(asAdmin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("DRAFT"));

            verify(curriculumService).getCurriculum(curriculumId);
            verifyNoMoreServiceCalls();
        }

        @Test
        @DisplayName("공개는 publishCurriculum만 호출한다")
        void publishCurriculum_callsPublishOnly() throws Exception {
            // given
            UUID curriculumId = UUID.randomUUID();
            CurriculumResult published = CurriculumResult.from(curriculum(curriculumId, ContentStatus.PUBLISHED));
            when(curriculumService.publishCurriculum(curriculumId)).thenReturn(published);

            // when & then
            mockMvc.perform(patch(BASE + "/curriculums/{id}/publish", curriculumId).with(asAdmin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

            verify(curriculumService).publishCurriculum(curriculumId);
            verifyNoMoreServiceCalls();
        }

        @Test
        @DisplayName("비공개는 unpublishCurriculum만 호출한다")
        void unpublishCurriculum_callsUnpublishOnly() throws Exception {
            // given
            UUID curriculumId = UUID.randomUUID();
            CurriculumResult draft = CurriculumResult.from(curriculum(curriculumId, ContentStatus.DRAFT));
            when(curriculumService.unpublishCurriculum(curriculumId)).thenReturn(draft);

            // when & then
            mockMvc.perform(patch(BASE + "/curriculums/{id}/unpublish", curriculumId).with(asAdmin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("DRAFT"));

            verify(curriculumService).unpublishCurriculum(curriculumId);
            verifyNoMoreServiceCalls();
        }
    }

    @Nested
    @DisplayName("Unit")
    class UnitEndpoints {

        @Test
        @DisplayName("목록 조회는 상태와 관계없는 searchUnits만 호출한다")
        void getUnits_callsAdminSearchOnly() throws Exception {
            // given
            UUID curriculumId = UUID.randomUUID();
            UUID unitId = UUID.randomUUID();
            PageResponse<UnitResponse> page = new PageResponse<>(
                    List.of(UnitResponse.from(unit(unitId, ContentStatus.DRAFT))), 0, 20, 1, 1, false);
            when(unitService.searchUnits(eq(curriculumId), any(Pageable.class))).thenReturn(page);

            // when & then
            mockMvc.perform(get(BASE + "/units").param("curriculumId", curriculumId.toString()).with(asAdmin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].status").value("DRAFT"));

            verify(unitService).searchUnits(eq(curriculumId), any(Pageable.class));
            verifyNoMoreServiceCalls();
        }

        @Test
        @DisplayName("단건 조회는 상태와 관계없는 getUnit만 호출한다")
        void getUnit_callsAdminGetOnly() throws Exception {
            // given
            UUID unitId = UUID.randomUUID();
            UnitResponse draft = UnitResponse.from(unit(unitId, ContentStatus.DRAFT));
            when(unitService.getUnit(unitId)).thenReturn(draft);

            // when & then
            mockMvc.perform(get(BASE + "/units/{id}", unitId).with(asAdmin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(unitId.toString()))
                    .andExpect(jsonPath("$.data.status").value("DRAFT"));

            verify(unitService).getUnit(unitId);
            verifyNoMoreServiceCalls();
        }

        @Test
        @DisplayName("공개는 publishUnit만 호출한다")
        void publishUnit_callsPublishOnly() throws Exception {
            // given
            UUID unitId = UUID.randomUUID();
            UnitResponse published = UnitResponse.from(unit(unitId, ContentStatus.PUBLISHED));
            when(unitService.publishUnit(unitId)).thenReturn(published);

            // when & then
            mockMvc.perform(patch(BASE + "/units/{id}/publish", unitId).with(asAdmin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

            verify(unitService).publishUnit(unitId);
            verifyNoMoreServiceCalls();
        }

        @Test
        @DisplayName("비공개는 unpublishUnit만 호출한다")
        void unpublishUnit_callsUnpublishOnly() throws Exception {
            // given
            UUID unitId = UUID.randomUUID();
            UnitResponse draft = UnitResponse.from(unit(unitId, ContentStatus.DRAFT));
            when(unitService.unpublishUnit(unitId)).thenReturn(draft);

            // when & then
            mockMvc.perform(patch(BASE + "/units/{id}/unpublish", unitId).with(asAdmin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("DRAFT"));

            verify(unitService).unpublishUnit(unitId);
            verifyNoMoreServiceCalls();
        }
    }

    @Nested
    @DisplayName("Lesson")
    class LessonEndpoints {

        @Test
        @DisplayName("목록 조회는 상태와 관계없는 searchLessons만 호출한다")
        void getLessons_callsAdminSearchOnly() throws Exception {
            // given
            UUID unitId = UUID.randomUUID();
            UUID lessonId = UUID.randomUUID();
            PageResponse<LessonResponse> page = new PageResponse<>(
                    List.of(LessonResponse.from(lesson(lessonId, unitId, ContentStatus.DRAFT))), 0, 20, 1, 1, false);
            when(lessonService.searchLessons(eq(unitId), any(Pageable.class))).thenReturn(page);

            // when & then
            mockMvc.perform(get(BASE + "/lessons").param("unitId", unitId.toString()).with(asAdmin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].status").value("DRAFT"));

            verify(lessonService).searchLessons(eq(unitId), any(Pageable.class));
            verifyNoMoreServiceCalls();
        }

        @Test
        @DisplayName("단건 조회는 상태와 관계없는 getLesson만 호출한다")
        void getLesson_callsAdminGetOnly() throws Exception {
            // given
            UUID lessonId = UUID.randomUUID();
            LessonResponse draft = LessonResponse.from(lesson(lessonId, UUID.randomUUID(), ContentStatus.DRAFT));
            when(lessonService.getLesson(lessonId)).thenReturn(draft);

            // when & then
            mockMvc.perform(get(BASE + "/lessons/{id}", lessonId).with(asAdmin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("DRAFT"));

            verify(lessonService).getLesson(lessonId);
            verifyNoMoreServiceCalls();
        }

        @Test
        @DisplayName("공개는 publishLesson만 호출한다")
        void publishLesson_callsPublishOnly() throws Exception {
            // given
            UUID lessonId = UUID.randomUUID();
            LessonResponse published = LessonResponse.from(lesson(lessonId, UUID.randomUUID(), ContentStatus.PUBLISHED));
            when(lessonService.publishLesson(lessonId)).thenReturn(published);

            // when & then
            mockMvc.perform(patch(BASE + "/lessons/{id}/publish", lessonId).with(asAdmin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

            verify(lessonService).publishLesson(lessonId);
            verifyNoMoreServiceCalls();
        }

        @Test
        @DisplayName("비공개는 unpublishLesson만 호출한다")
        void unpublishLesson_callsUnpublishOnly() throws Exception {
            // given
            UUID lessonId = UUID.randomUUID();
            LessonResponse draft = LessonResponse.from(lesson(lessonId, UUID.randomUUID(), ContentStatus.DRAFT));
            when(lessonService.unpublishLesson(lessonId)).thenReturn(draft);

            // when & then
            mockMvc.perform(patch(BASE + "/lessons/{id}/unpublish", lessonId).with(asAdmin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("DRAFT"));

            verify(lessonService).unpublishLesson(lessonId);
            verifyNoMoreServiceCalls();
        }
    }

    @Test
    @DisplayName("ADMIN이 아닌 사용자는 공개 상태를 바꿀 수 없고 서비스도 호출되지 않는다")
    void publishLesson_nonAdmin_returns403() throws Exception {
        // when & then
        mockMvc.perform(patch(BASE + "/lessons/{id}/publish", UUID.randomUUID()).with(asUser()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(curriculumService, unitService, lessonService);
    }

    private void verifyNoMoreServiceCalls() {
        verifyNoMoreInteractions(curriculumService, unitService, lessonService);
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

    private RequestPostProcessor asAdmin() {
        return authentication(new UsernamePasswordAuthenticationToken(
                adminId, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    private RequestPostProcessor asUser() {
        return authentication(new UsernamePasswordAuthenticationToken(
                userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }
}
