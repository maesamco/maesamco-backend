package com.maesamco.content.presentation.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import com.maesamco.content.application.command.TestCaseCreateCommand;
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

    /** 기존 API 계약 유지를 위해 받지만 생성 시에는 사용하지 않습니다(서버가 그룹의 마지막 순서로 정합니다). */
    @NotNull
    @Positive
    private Integer testCaseOrder;

    public TestCaseCreateCommand toCommand() {
        return new TestCaseCreateCommand(input, expectedOutput, isPublic);
    }
}
