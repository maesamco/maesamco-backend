package com.maesamco.content.infrastructure.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TwoPhaseDisplayOrderUpdater 단위 테스트 (#324)")
class TwoPhaseDisplayOrderUpdaterTest {

    @Test
    @DisplayName("모든 항목을 음수(-1..-N)로 옮겨 flush한 뒤, 최종 값(1..N)으로 옮겨 다시 flush한다")
    void reassign_movesThroughNegativeValuesThenFinalValues() {
        // given — 현재 값 [C=3, A=1, B=2] 를 이 순서대로 1..3으로
        Map<String, Integer> orders = new LinkedHashMap<>();
        orders.put("C", 3);
        orders.put("A", 1);
        orders.put("B", 2);

        List<String> log = new ArrayList<>();
        List<Map<String, Integer>> snapshotsAtFlush = new ArrayList<>();

        // when
        TwoPhaseDisplayOrderUpdater.reassign(
                List.of("C", "A", "B"),
                (item, value) -> {
                    orders.put(item, value);
                    log.add(item + "=" + value);
                },
                () -> snapshotsAtFlush.add(new LinkedHashMap<>(orders))
        );

        // then
        assertThat(log).containsExactly(
                "C=-1", "A=-2", "B=-3",
                "C=1", "A=2", "B=3"
        );
        assertThat(snapshotsAtFlush).hasSize(2);
        assertThat(snapshotsAtFlush.get(0)).containsEntry("C", -1).containsEntry("A", -2).containsEntry("B", -3);
        assertThat(snapshotsAtFlush.get(1)).containsEntry("C", 1).containsEntry("A", 2).containsEntry("B", 3);
    }

    @Test
    @DisplayName("각 flush 시점의 값은 서로 겹치지 않는다")
    void reassign_valuesAreDistinctAtEveryFlush() {
        Map<String, Integer> orders = new LinkedHashMap<>();
        List<String> items = List.of("E", "A", "B", "C", "D");
        for (int index = 0; index < items.size(); index++) {
            orders.put(items.get(index), index + 1);
        }

        List<Boolean> distinctAtFlush = new ArrayList<>();

        TwoPhaseDisplayOrderUpdater.reassign(
                List.of("A", "E", "B", "C", "D"),
                orders::put,
                () -> distinctAtFlush.add(
                        orders.values().stream().distinct().count() == orders.size()
                )
        );

        assertThat(distinctAtFlush).containsExactly(true, true);
    }

    @Test
    @DisplayName("빈 목록이면 아무 것도 하지 않고 flush도 호출하지 않는다")
    void reassign_emptyList_doesNothing() {
        List<String> calls = new ArrayList<>();

        TwoPhaseDisplayOrderUpdater.reassign(
                List.<String>of(),
                (item, value) -> calls.add("change"),
                () -> calls.add("flush")
        );

        assertThat(calls).isEmpty();
    }
}
