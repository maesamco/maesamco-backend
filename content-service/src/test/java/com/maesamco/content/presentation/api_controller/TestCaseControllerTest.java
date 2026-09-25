package com.maesamco.content.presentation.api_controller;

import com.maesamco.content.application.persistence_service.TestCaseService;
import com.maesamco.content.domain.entity.TestCase;
import com.maesamco.content.application.command.TestCaseCreateCommand;
import com.maesamco.content.application.command.TestCaseUpdateCommand;
import com.maesamco.content.application.result.TestCaseResult;
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

@WebMvcTest(TestCaseController.class)
@Import(TestSecurityConfig.class)
class TestCaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TestCaseService testCaseService;

    private final UUID problemId = UUID.randomUUID();
    private final UUID testCaseId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

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
    @DisplayName("ADMIN이 테스트케이스를 생성하면 201을 반환한다")
    void createTestCase_admin_returns201() throws Exception {

        // given
        TestCase testCase = createTestCase();

        when(
                testCaseService.createTestCase(
                        eq(problemId),
                        any(TestCaseCreateCommand.class)
                )
        ).thenReturn(
                TestCaseResult.from(testCase)
        );

        String json = """
                {
                    "input": "1 2",
                    "expectedOutput": "3",
                    "isPublic": true,
                    "testCaseOrder": 1
                }
                """;

        // when & then
        mockMvc.perform(
                        post(
                                "/api/v1/contents/problems/{problemId}/test-cases",
                                problemId
                        )
                                .with(asAdmin(adminId))
                                .contentType("application/json")
                                .content(json)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

        ArgumentCaptor<TestCaseCreateCommand> captor =
                ArgumentCaptor.forClass(
                        TestCaseCreateCommand.class
                );

        verify(testCaseService)
                .createTestCase(
                        eq(problemId),
                        captor.capture()
                );

        assertThat(captor.getValue().getInput())
                .isEqualTo("1 2");

        assertThat(captor.getValue().getExpectedOutput())
                .isEqualTo("3");

        assertThat(captor.getValue().getIsPublic())
                .isTrue();
    }

    @Test
    @DisplayName("ADMIN이 아닌 사용자가 테스트케이스를 생성하면 403을 반환한다")
    void createTestCase_nonAdmin_returns403() throws Exception {

        String json = """
                {
                    "input": "1 2",
                    "expectedOutput": "3",
                    "isPublic": true,
                    "testCaseOrder": 1
                }
                """;

        mockMvc.perform(
                        post(
                                "/api/v1/contents/problems/{problemId}/test-cases",
                                problemId
                        )
                                .with(asUser(userId))
                                .contentType("application/json")
                                .content(json)
                )
                .andExpect(status().isForbidden());

        verifyNoInteractions(testCaseService);
    }

    @Test
    @DisplayName("ADMIN이 단건 조회하면 전체 테스트케이스를 조회한다")
    void getTestCase_admin_getsAll() throws Exception {

        // given
        when(testCaseService.getTestCase(testCaseId))
                .thenReturn(
                        TestCaseResult.from(createTestCase())
                );

        // when & then
        mockMvc.perform(
                        get(
                                "/api/v1/contents/test-cases/{testCaseId}",
                                testCaseId
                        )
                                .with(asAdmin(adminId))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(testCaseService)
                .getTestCase(testCaseId);

        verify(testCaseService, never())
                .getPublicTestCase(any());
    }

    @Test
    @DisplayName("일반 사용자가 단건 조회하면 공개 테스트케이스를 조회한다")
    void getTestCase_user_getsPublic() throws Exception {

        // given
        when(testCaseService.getPublicTestCase(testCaseId))
                .thenReturn(
                        TestCaseResult.from(createTestCase())
                );

        // when & then
        mockMvc.perform(
                        get(
                                "/api/v1/contents/test-cases/{testCaseId}",
                                testCaseId
                        )
                                .with(asUser(userId))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(testCaseService)
                .getPublicTestCase(testCaseId);

        verify(testCaseService, never())
                .getTestCase(any());
    }

    @Test
    @DisplayName("ADMIN이 목록 조회하면 전체 테스트케이스를 조회한다")
    void getTestCases_admin_searchesAll() throws Exception {

        // given
        PageResult<TestCaseResult> response =
                createPageResult();

        when(
                testCaseService.searchTestCasesAll(
                        eq(problemId),
                        any(PageQuery.class)
                )
        ).thenReturn(response);

        // when & then
        mockMvc.perform(
                        get(
                                "/api/v1/contents/problems/{problemId}/test-cases",
                                problemId
                        )
                                .with(asAdmin(adminId))
                                .param("page", "0")
                                .param("size", "10")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        ArgumentCaptor<PageQuery> captor =
                ArgumentCaptor.forClass(PageQuery.class);

        verify(testCaseService)
                .searchTestCasesAll(
                        eq(problemId),
                        captor.capture()
                );

        verify(testCaseService, never())
                .searchTestCasesPublic(
                        any(),
                        any()
                );

        assertThat(captor.getValue().page())
                .isEqualTo(0);

        assertThat(captor.getValue().size())
                .isEqualTo(10);
    }

    @Test
    @DisplayName("일반 사용자가 목록 조회하면 공개 테스트케이스만 조회한다")
    void getTestCases_user_searchesPublic() throws Exception {

        // given
        when(
                testCaseService.searchTestCasesPublic(
                        eq(problemId),
                        any(PageQuery.class)
                )
        ).thenReturn(
                createPageResult()
        );

        // when & then
        mockMvc.perform(
                        get(
                                "/api/v1/contents/problems/{problemId}/test-cases",
                                problemId
                        )
                                .with(asUser(userId))
                                .param("page", "0")
                                .param("size", "10")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(testCaseService)
                .searchTestCasesPublic(
                        eq(problemId),
                        any(PageQuery.class)
                );

        verify(testCaseService, never())
                .searchTestCasesAll(
                        any(),
                        any()
                );
    }

    @Test
    @DisplayName("ADMIN이 테스트케이스를 수정하면 200을 반환한다")
    void updateTestCase_admin_returns200() throws Exception {

        // given
        when(
                testCaseService.updateTestCase(
                        eq(testCaseId),
                        any(TestCaseUpdateCommand.class)
                )
        ).thenReturn(
                TestCaseResult.from(createTestCase())
        );

        String json = """
                {
                    "input": "10 20",
                    "expectedOutput": "30",
                    "isPublic": false
                }
                """;

        // when & then
        mockMvc.perform(
                        patch(
                                "/api/v1/contents/test-cases/{testCaseId}",
                                testCaseId
                        )
                                .with(asAdmin(adminId))
                                .contentType("application/json")
                                .content(json)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(testCaseService)
                .updateTestCase(
                        eq(testCaseId),
                        any(TestCaseUpdateCommand.class)
                );
    }

    @Test
    @DisplayName("ADMIN이 테스트케이스를 삭제하면 사용자 ID를 전달하고 200을 반환한다")
    void deleteTestCase_admin_returns200() throws Exception {

        // when & then
        mockMvc.perform(
                        delete(
                                "/api/v1/contents/test-cases/{testCaseId}",
                                testCaseId
                        )
                                .with(asAdmin(adminId))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(testCaseService)
                .deleteTestCase(
                        testCaseId,
                        adminId
                );
    }

    @Test
    @DisplayName("ADMIN이 아닌 사용자가 테스트케이스를 삭제하면 403을 반환한다")
    void deleteTestCase_nonAdmin_returns403() throws Exception {

        mockMvc.perform(
                        delete(
                                "/api/v1/contents/test-cases/{testCaseId}",
                                testCaseId
                        )
                                .with(asUser(userId))
                )
                .andExpect(status().isForbidden());

        verifyNoInteractions(testCaseService);
    }

    private TestCase createTestCase() {

        TestCase testCase =
                TestCase.createByAdmin(
                        problemId,
                        "1 2",
                        "3",
                        true,
                        1
                );

        ReflectionTestUtils.setField(
                testCase,
                "id",
                testCaseId
        );

        return testCase;
    }

    private PageResult<TestCaseResult> createPageResult() {

        return new PageResult<>(
                List.of(
                        TestCaseResult.from(
                                createTestCase()
                        )
                ),
                0,
                10,
                1
        );
    }
}