package com.maesamco.judge.presentation.internal_controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.maesamco.judge.application.query_service.SubmissionQueryService;
import com.maesamco.judge.global.exception.BusinessException;
import com.maesamco.judge.global.exception.ErrorCode;
import com.maesamco.judge.presentation.response.SubmissionInternalGetResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SubmissionInternalController.class)
class SubmissionInternalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SubmissionQueryService submissionQueryService;

    @Nested
    @DisplayName("GET /internal/v1/submissions/{submissionId}")
    class GetSubmission {

        @Test
        @DisplayName("조회에 성공하면 200과 success/data 포맷으로 응답한다")
        void returns200WithSubmission() throws Exception {
            UUID submissionId = UUID.randomUUID();
            SubmissionInternalGetResponse response = new SubmissionInternalGetResponse(
                    submissionId, UUID.randomUUID(), UUID.randomUUID(), "code",
                    "COMPLETED", "WRONG", null,
                    List.of(new SubmissionInternalGetResponse.FailedTestSummary(true, "WRONG_ANSWER")),
                    3
            );
            given(submissionQueryService.getSubmissionForInternal(any())).willReturn(response);

            mockMvc.perform(get("/internal/v1/submissions/{submissionId}", submissionId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.data.failedTestSummary[0].isPublic").value(true));
        }

        @Test
        @DisplayName("존재하지 않는 제출이면 404를 반환한다")
        void returns404WhenMissing() throws Exception {
            UUID submissionId = UUID.randomUUID();
            given(submissionQueryService.getSubmissionForInternal(any()))
                    .willThrow(new BusinessException(ErrorCode.SUBMISSION_NOT_FOUND));

            mockMvc.perform(get("/internal/v1/submissions/{submissionId}", submissionId))
                    .andExpect(status().isNotFound());
        }
    }
}