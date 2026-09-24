package com.maesamco.content.application.command;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 테스트케이스 생성에 필요한 값입니다. Presentation의 요청 DTO와 분리된 Application 전용 타입입니다. */
@Getter
@AllArgsConstructor
public class TestCaseCreateCommand {

    private final String input;
    private final String expectedOutput;
    private final Boolean isPublic;
    private final Integer testCaseOrder;
}
