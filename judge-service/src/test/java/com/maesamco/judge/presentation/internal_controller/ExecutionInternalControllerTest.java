package com.maesamco.judge.presentation.internal_controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maesamco.judge.application.facade.ExecutionValidationFacade;
import com.maesamco.judge.application.result.ExecutionValidationResult;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ExecutionInternalController.class)
@AutoConfigureMockMvc(addFilters = false)
class ExecutionInternalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ExecutionValidationFacade executionValidationFacade;

    @Nested
    @DisplayName("POST /internal/v1/executions")
    class Validate {

        @Test
        @DisplayName("검증에 성공하면 200과 success/data 포맷으로 각 테스트케이스 결과를 반환한다")
        void returns200WithResults() throws Exception {
            String requestBody = """
                    {
                      "code": "public class Main {}",
                      "testCases": [
                        { "input": "3 5", "expectedOutput": "8", "cpuTimeLimitSeconds": 2, "memoryLimitKb": 262144 }
                      ]
                    }
                    """;
            given(executionValidationFacade.validate(any(), anyList()))
                    .willReturn(List.of(new ExecutionValidationResult(0, true, false, "8")));

            mockMvc.perform(post("/internal/v1/executions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.results[0].testCaseIndex").value(0))
                    .andExpect(jsonPath("$.data.results[0].passed").value(true))
                    .andExpect(jsonPath("$.data.results[0].timedOut").value(false))
                    .andExpect(jsonPath("$.data.results[0].stdout").value("8"));
        }

        @Test
        @DisplayName("testCases가 빈 배열이어도 400 없이 200과 빈 결과를 반환한다")
        void returns200WithEmptyResultsWhenTestCasesEmpty() throws Exception {
            String requestBody = """
                    { "code": "public class Main {}", "testCases": [] }
                    """;
            given(executionValidationFacade.validate(any(), anyList())).willReturn(List.of());

            mockMvc.perform(post("/internal/v1/executions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.results").isEmpty());
        }
    }
}