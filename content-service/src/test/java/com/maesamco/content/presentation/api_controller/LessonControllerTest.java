package com.maesamco.content.presentation.api_controller;

import com.maesamco.content.application.persistence_service.LessonService;
import com.maesamco.content.domain.entity.Lesson;
import com.maesamco.content.domain.entity.ProgrammingLanguage;
import com.maesamco.content.global.response.PageResponse;
import com.maesamco.content.presentation.request.LessonCreateRequest;
import com.maesamco.content.presentation.request.LessonUpdateRequest;
import com.maesamco.content.presentation.response.LessonCreateResponse;
import com.maesamco.content.presentation.response.LessonResponse;
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

@WebMvcTest(LessonController.class)
@Import(LessonControllerTest.TestSecurityConfig.class)
class LessonControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LessonService lessonService;

    private final UUID lessonId = UUID.randomUUID();
    private final UUID unitId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @TestConfiguration
    @EnableMethodSecurity
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
    @DisplayName("ADMIN이 레슨을 생성하면 201과 생성 정보를 반환한다")
    void createLesson_admin_returns201() throws Exception {

        // given
        Lesson lesson = createLesson(
                lessonId,
                unitId,
                "Java 변수",
                ProgrammingLanguage.JAVA,
                "변수에 대해 학습합니다.",
                "변수는 값을 저장하는 공간입니다.",
                1
        );

        when(
                lessonService.createLesson(
                        any(LessonCreateRequest.class)
                )
        ).thenReturn(
                LessonCreateResponse.from(lesson)
        );

        String json = """
                {
                    "unitId": "%s",
                    "title": "Java 변수",
                    "language": "JAVA",
                    "description": "변수에 대해 학습합니다.",
                    "content": "변수는 값을 저장하는 공간입니다.",
                    "displayOrder": 1
                }
                """.formatted(unitId);

        // when & then
        mockMvc.perform(
                        post("/api/v1/contents/lessons")
                                .with(asAdmin(adminId))
                                .contentType("application/json")
                                .content(json)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(
                        jsonPath("$.data.id")
                                .value(lessonId.toString())
                )
                .andExpect(
                        jsonPath("$.data.title")
                                .value("Java 변수")
                );

        ArgumentCaptor<LessonCreateRequest> captor =
                ArgumentCaptor.forClass(
                        LessonCreateRequest.class
                );

        verify(lessonService)
                .createLesson(captor.capture());

        assertThat(captor.getValue().getUnitId())
                .isEqualTo(unitId);

        assertThat(captor.getValue().getTitle())
                .isEqualTo("Java 변수");

        assertThat(captor.getValue().getLanguage())
                .isEqualTo(ProgrammingLanguage.JAVA);
    }

    @Test
    @DisplayName("ADMIN이 아닌 사용자가 레슨을 생성하면 403을 반환한다")
    void createLesson_nonAdmin_returns403() throws Exception {

        String json = """
                {
                    "unitId": "%s",
                    "title": "Java 변수",
                    "language": "JAVA",
                    "description": "변수에 대해 학습합니다.",
                    "content": "변수는 값을 저장하는 공간입니다.",
                    "displayOrder": 1
                }
                """.formatted(unitId);

        mockMvc.perform(
                        post("/api/v1/contents/lessons")
                                .with(asUser(userId))
                                .contentType("application/json")
                                .content(json)
                )
                .andExpect(status().isForbidden());

        verifyNoInteractions(lessonService);
    }

    @Test
    @DisplayName("인증 사용자가 레슨을 단건 조회하면 200을 반환한다")
    void getLesson_authenticated_returns200() throws Exception {

        // given
        Lesson lesson = createLesson(
                lessonId,
                unitId,
                "Java 변수",
                ProgrammingLanguage.JAVA,
                "변수에 대해 학습합니다.",
                "변수는 값을 저장하는 공간입니다.",
                1
        );

        when(lessonService.getLesson(lessonId))
                .thenReturn(
                        LessonResponse.from(lesson)
                );

        // when & then
        mockMvc.perform(
                        get(
                                "/api/v1/contents/lessons/{lessonId}",
                                lessonId
                        )
                                .with(asUser(userId))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(
                        jsonPath("$.data.id")
                                .value(lessonId.toString())
                )
                .andExpect(
                        jsonPath("$.data.unitId")
                                .value(unitId.toString())
                )
                .andExpect(
                        jsonPath("$.data.title")
                                .value("Java 변수")
                );

        verify(lessonService)
                .getLesson(lessonId);
    }

    @Test
    @DisplayName("특정 유닛의 레슨 목록을 페이징 조회한다")
    void getLessons_authenticated_returnsPage() throws Exception {

        // given
        Lesson lesson = createLesson(
                lessonId,
                unitId,
                "Java 변수",
                ProgrammingLanguage.JAVA,
                "변수에 대해 학습합니다.",
                "변수는 값을 저장하는 공간입니다.",
                1
        );

        PageResponse<LessonResponse> response =
                PageResponse.from(
                        new PageImpl<>(
                                List.of(
                                        LessonResponse.from(lesson)
                                ),
                                PageRequest.of(1, 5),
                                6
                        )
                );

        when(
                lessonService.searchLessons(
                        eq(unitId),
                        any(Pageable.class)
                )
        ).thenReturn(response);

        // when & then
        mockMvc.perform(
                        get("/api/v1/contents/lessons")
                                .with(asUser(userId))
                                .param(
                                        "unitId",
                                        unitId.toString()
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

        verify(lessonService)
                .searchLessons(
                        eq(unitId),
                        captor.capture()
                );

        assertThat(captor.getValue().getPageNumber())
                .isEqualTo(1);

        assertThat(captor.getValue().getPageSize())
                .isEqualTo(5);
    }

    @Test
    @DisplayName("ADMIN이 레슨을 수정하면 200과 수정된 정보를 반환한다")
    void updateLesson_admin_returns200() throws Exception {

        // given
        Lesson lesson = createLesson(
                lessonId,
                unitId,
                "Java 조건문",
                ProgrammingLanguage.JAVA,
                "조건문 설명",
                "조건문 학습 내용",
                1
        );

        when(
                lessonService.updateLesson(
                        eq(lessonId),
                        any(LessonUpdateRequest.class)
                )
        ).thenReturn(
                LessonResponse.from(lesson)
        );

        String json = """
                {
                    "title": "Java 조건문",
                    "description": "조건문 설명",
                    "content": "조건문 학습 내용"
                }
                """;

        // when & then
        mockMvc.perform(
                        patch(
                                "/api/v1/contents/lessons/{lessonId}",
                                lessonId
                        )
                                .with(asAdmin(adminId))
                                .contentType("application/json")
                                .content(json)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(
                        jsonPath("$.data.title")
                                .value("Java 조건문")
                );

        verify(lessonService)
                .updateLesson(
                        eq(lessonId),
                        any(LessonUpdateRequest.class)
                );
    }

    @Test
    @DisplayName("ADMIN이 레슨을 삭제하면 200을 반환하고 사용자 ID를 전달한다")
    void deleteLesson_admin_returns200() throws Exception {

        // when & then
        mockMvc.perform(
                        delete(
                                "/api/v1/contents/lessons/{lessonId}",
                                lessonId
                        )
                                .with(asAdmin(adminId))
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.success")
                                .value(true)
                );

        verify(lessonService)
                .deleteLesson(
                        lessonId,
                        adminId
                );
    }

    @Test
    @DisplayName("ADMIN이 아닌 사용자가 레슨을 삭제하면 403을 반환한다")
    void deleteLesson_nonAdmin_returns403() throws Exception {

        mockMvc.perform(
                        delete(
                                "/api/v1/contents/lessons/{lessonId}",
                                lessonId
                        )
                                .with(asUser(userId))
                )
                .andExpect(status().isForbidden());

        verifyNoInteractions(lessonService);
    }

    private Lesson createLesson(
            UUID id,
            UUID unitId,
            String title,
            ProgrammingLanguage language,
            String description,
            String content,
            Integer displayOrder
    ) {
        Lesson lesson = Lesson.create(
                unitId,
                title,
                description,
                content,
                language,
                displayOrder
        );

        ReflectionTestUtils.setField(
                lesson,
                "id",
                id
        );

        return lesson;
    }
}