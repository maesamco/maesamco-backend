package com.maesamco.content.application.persistence_service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SiblingDisplayOrders 단위 테스트 (#324)")
class SiblingDisplayOrdersTest {

    private record Item(UUID id, String name) {
    }

    private static final Function<Item, UUID> ID = Item::id;

    private static final Item A = item("A");
    private static final Item B = item("B");
    private static final Item C = item("C");
    private static final Item D = item("D");
    private static final Item E = item("E");

    private static final List<Item> ABCDE = List.of(A, B, C, D, E);

    @Test
    @DisplayName("앞쪽 자리로 옮기면 그 자리부터 대상 앞까지의 형제가 한 칸씩 밀린다")
    void moveTo_front_pushesSiblingsBack() {
        assertThat(SiblingDisplayOrders.moveTo(ABCDE, ID, E.id(), 2))
                .containsExactly(A, E, B, C, D);
    }

    @Test
    @DisplayName("뒤쪽 자리로 옮기면 대상 뒤부터 그 자리까지의 형제가 한 칸씩 당겨진다")
    void moveTo_back_pullsSiblingsForward() {
        assertThat(SiblingDisplayOrders.moveTo(ABCDE, ID, A.id(), 4))
                .containsExactly(B, C, D, A, E);
    }

    @Test
    @DisplayName("맨 앞과 맨 뒤로 옮길 수 있다")
    void moveTo_edges() {
        assertThat(SiblingDisplayOrders.moveTo(ABCDE, ID, C.id(), 1))
                .containsExactly(C, A, B, D, E);
        assertThat(SiblingDisplayOrders.moveTo(ABCDE, ID, C.id(), 5))
                .containsExactly(A, B, D, E, C);
    }

    @Test
    @DisplayName("현재 자리 그대로면 순서가 바뀌지 않고, 입력 목록은 변경하지 않는다")
    void moveTo_samePosition_keepsOrderAndDoesNotMutateInput() {
        List<Item> input = new java.util.ArrayList<>(ABCDE);

        assertThat(SiblingDisplayOrders.moveTo(input, ID, C.id(), 3))
                .containsExactly(A, B, C, D, E);
        assertThat(input).containsExactly(A, B, C, D, E);
    }

    @ParameterizedTest(name = "position={0}, size={1} → {2}")
    @CsvSource({
            "0, 3, false",
            "1, 3, true",
            "3, 3, true",
            "4, 3, false",
            "-1, 3, false",
            "1, 0, false"
    })
    @DisplayName("자리는 1..형제 수 범위만 허용한다")
    void isInRange(int position, int size, boolean expected) {
        assertThat(SiblingDisplayOrders.isInRange(position, size)).isEqualTo(expected);
    }

    @Test
    @DisplayName("범위를 벗어난 자리나 목록에 없는 대상이면 IllegalArgumentException을 던진다")
    void moveTo_invalidArguments_throws() {
        assertThatThrownBy(() -> SiblingDisplayOrders.moveTo(ABCDE, ID, A.id(), 6))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SiblingDisplayOrders.moveTo(ABCDE, ID, UUID.randomUUID(), 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Item item(String name) {
        return new Item(UUID.randomUUID(), name);
    }
}
