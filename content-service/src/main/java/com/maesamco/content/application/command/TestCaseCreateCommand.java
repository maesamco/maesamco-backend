package com.maesamco.content.application.command;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 테스트케이스 생성에 필요한 값입니다. Presentation의 요청 DTO와 분리된 Application 전용 타입입니다.
 *
 * <p>순서(testCaseOrder)는 요청 값과 무관하게 생성 시 서비스가 그룹의 마지막 순서로 정하므로 담지 않습니다.</p>
 */
@Getter
@AllArgsConstructor
public class TestCaseCreateCommand {

    private final String input;
    private final String expectedOutput;
    private final Boolean isPublic;
}
