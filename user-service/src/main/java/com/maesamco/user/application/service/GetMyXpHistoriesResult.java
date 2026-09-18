package com.maesamco.user.application.service;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Objects;

/**
 * 로그인 사용자의 XP 이력 페이지 조회 결과입니다.
 *
 * @param items 현재 페이지 XP 이력
 * @param nextCursor 다음 페이지 cursor
 * @param hasNext 다음 페이지 존재 여부
 */
@Schema(
        name = "GetMyXpHistoriesResult",
        description = "로그인 사용자의 cursor 기반 XP 이력 페이지"
)
public record GetMyXpHistoriesResult(

        @Schema(
                description = "최신순 XP 이력 목록",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        List<GetMyXpHistoryItemResult> items,

        @Schema(
                description = "다음 페이지가 있을 때 사용하는 opaque cursor",
                example = "djF8MjAyNi0wOS0xN1QwMToyMDozMFp8"
                        + "MTExMTExMTEtMTExMS0xMTExLTExMTEt"
                        + "MTExMTExMTExMTEx",
                nullable = true
        )
        String nextCursor,

        @Schema(
                description = "다음 페이지 존재 여부",
                example = "true",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        boolean hasNext
) {

    /**
     * 응답 목록을 외부에서 변경할 수 없도록 복사합니다.
     */
    public GetMyXpHistoriesResult {
        Objects.requireNonNull(
                items,
                "XP 이력 응답 목록은 필수입니다."
        );

        items =
                List.copyOf(
                        items
                );
    }
}
