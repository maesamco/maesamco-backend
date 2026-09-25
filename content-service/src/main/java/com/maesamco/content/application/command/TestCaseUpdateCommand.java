package com.maesamco.content.application.command;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 테스트케이스 수정에 필요한 값입니다.
 *
 * <p>PATCH 요청이므로 전달되지 않은 필드는 {@code null}이며 변경하지 않습니다.</p>
 */
@Getter
@AllArgsConstructor
public class TestCaseUpdateCommand {

    private final String input;
    private final String expectedOutput;
    private final Boolean isPublic;
    private final Integer testCaseOrder;
}
