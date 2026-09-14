package com.maesamco.content.testcase.presentation.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 테스트케이스 생성 요청 DTO */
@Getter
@NoArgsConstructor
public class TestCaseCreateRequest {

    @NotNull
    private String input;

    @NotNull
    private String expectedOutput;

    @NotNull
    private Boolean isPublic;

    @NotNull
    @Positive
    private Integer testCaseOrder;
}