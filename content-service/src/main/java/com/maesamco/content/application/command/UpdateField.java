package com.maesamco.content.application.command;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UpdateField<T> {

    private final boolean defined;
    private final T value;

    /** 요청에 필드가 포함되지 않은 경우 */
    public static <T> UpdateField<T> undefined() {
        return new UpdateField<>(
                false,
                null
        );
    }

    /** 요청에 필드가 포함된 경우 - null 포함 */
    public static <T> UpdateField<T> of(T value) {
        return new UpdateField<>(
                true,
                value
        );
    }
}