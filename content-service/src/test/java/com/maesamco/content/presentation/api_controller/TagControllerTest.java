package com.maesamco.content.presentation.api_controller;

import com.maesamco.content.application.persistence_service.TagService;
import com.maesamco.content.domain.entity.Tag;
import com.maesamco.content.domain.entity.TagAttribute;
import com.maesamco.content.application.command.TagCreateCommand;
import com.maesamco.content.application.command.TagUpdateCommand;
import com.maesamco.content.application.result.TagResult;
import com.maesamco.content.global.common.pagination.PageQuery;
import com.maesamco.content.global.common.pagination.PageResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
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

@WebMvcTest(TagController.class)
@Import(TagControllerTest.TestSecurityConfig.class)
class TagControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TagService tagService;

    private final UUID tagId = UUID.randomUUID();
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
    @DisplayName("ADMIN이 태그를 생성하면 201과 생성 정보를 반환한다")
    void createTag_admin_returns201() throws Exception {

        // given
        Tag tag = createTag(
                tagId,
                "반복문",
                TagAttribute.CONCEPT
        );

        when(
                tagService.createTag(
                        any(TagCreateCommand.class)
                )
        ).thenReturn(
                TagResult.from(tag)
        );

        String json = """
                {
                    "name": "반복문",
                    "attribute": "CONCEPT"
                }
                """;

        // when & then
        mockMvc.perform(
                        post("/api/v1/admin/contents/tags")
                                .with(asAdmin(adminId))
                                .contentType("application/json")
                                .content(json)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(
                        jsonPath("$.data.id")
                                .value(tagId.toString())
                );

        ArgumentCaptor<TagCreateCommand> captor =
                ArgumentCaptor.forClass(
                        TagCreateCommand.class
                );

        verify(tagService)
                .createTag(captor.capture());

        assertThat(captor.getValue().getName())
                .isEqualTo("반복문");

        assertThat(captor.getValue().getAttribute())
                .isEqualTo(TagAttribute.CONCEPT);
    }

    @Test
    @DisplayName("ADMIN이 아닌 사용자가 태그를 생성하면 403을 반환한다")
    void createTag_nonAdmin_returns403() throws Exception {

        String json = """
                {
                    "name": "반복문",
                    "attribute": "CONCEPT"
                }
                """;

        mockMvc.perform(
                        post("/api/v1/admin/contents/tags")
                                .with(asUser(userId))
                                .contentType("application/json")
                                .content(json)
                )
                .andExpect(status().isForbidden());

        verifyNoInteractions(tagService);
    }

    @Test
    @DisplayName("attribute 없이 태그 목록을 조회하면 전체 태그를 조회한다")
    void getTags_withoutAttribute_searchesAll() throws Exception {

        // given
        Tag tag = createTag(
                tagId,
                "반복문",
                TagAttribute.CONCEPT
        );

        PageResult<TagResult> response =
                new PageResult<>(
                        List.of(
                                        TagResult.from(tag)
                                ),
                        0,
                        10,
                        1
                );

        when(
                tagService.searchTags(
                        any(PageQuery.class)
                )
        ).thenReturn(response);

        // when & then
        mockMvc.perform(
                        get("/api/v1/contents/tags")
                                .param("page", "0")
                                .param("size", "10")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(
                        jsonPath("$.data.content.length()")
                                .value(1)
                );

        ArgumentCaptor<PageQuery> captor =
                ArgumentCaptor.forClass(PageQuery.class);

        verify(tagService)
                .searchTags(captor.capture());

        verify(tagService, never())
                .searchTagsByAttribute(
                        any(TagAttribute.class),
                        any(PageQuery.class)
                );

        assertThat(captor.getValue().page())
                .isEqualTo(0);

        assertThat(captor.getValue().size())
                .isEqualTo(10);
    }

    @Test
    @DisplayName("attribute가 있으면 해당 속성의 태그 목록을 조회한다")
    void getTags_withAttribute_searchesByAttribute() throws Exception {

        // given
        PageResult<TagResult> response =
                new PageResult<>(
                        List.of(),
                        0,
                        10,
                        0
                );

        when(
                tagService.searchTagsByAttribute(
                        eq(TagAttribute.ALGORITHM),
                        any(PageQuery.class)
                )
        ).thenReturn(response);

        // when & then
        mockMvc.perform(
                        get("/api/v1/contents/tags")
                                .param("attribute", "ALGORITHM")
                                .param("page", "0")
                                .param("size", "10")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(tagService)
                .searchTagsByAttribute(
                        eq(TagAttribute.ALGORITHM),
                        any(PageQuery.class)
                );

        verify(tagService, never())
                .searchTags(any(PageQuery.class));
    }

    @Test
    @DisplayName("ADMIN이 태그를 수정하면 200을 반환한다")
    void updateTag_admin_returns200() throws Exception {

        // given
        String json = """
                {
                    "name": "조건문",
                    "attribute": "CONCEPT"
                }
                """;

        // when & then
        mockMvc.perform(
                        patch(
                                "/api/v1/admin/contents/tags/{tagId}",
                                tagId
                        )
                                .with(asAdmin(adminId))
                                .contentType("application/json")
                                .content(json)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(tagService)
                .updateTag(
                        eq(tagId),
                        any(TagUpdateCommand.class)
                );
    }

    @Test
    @DisplayName("ADMIN이 태그를 삭제하면 200을 반환하고 사용자 ID를 전달한다")
    void deleteTag_admin_returns200() throws Exception {

        // when & then
        mockMvc.perform(
                        delete(
                                "/api/v1/admin/contents/tags/{tagId}",
                                tagId
                        )
                                .with(asAdmin(adminId))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(tagService)
                .deleteTag(
                        tagId,
                        adminId
                );
    }

    @Test
    @DisplayName("ADMIN이 아닌 사용자가 태그를 삭제하면 403을 반환한다")
    void deleteTag_nonAdmin_returns403() throws Exception {

        mockMvc.perform(
                        delete(
                                "/api/v1/admin/contents/tags/{tagId}",
                                tagId
                        )
                                .with(asUser(userId))
                )
                .andExpect(status().isForbidden());

        verifyNoInteractions(tagService);
    }

    private Tag createTag(
            UUID id,
            String name,
            TagAttribute attribute
    ) {
        Tag tag = Tag.create(
                name,
                attribute
        );

        ReflectionTestUtils.setField(
                tag,
                "id",
                id
        );

        return tag;
    }
}