package com.maesamco.content.testcase.presentation.dto.request;

import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 테스트케이스 수정 요청 DTO */
@Getter
@NoArgsConstructor
public class TestCaseUpdateRequest {

    private String input;

    private String expectedOutput;

    private Boolean isPublic;

    @Positive
    private Integer testCaseOrder;
}