package com.maesamco.content.infrastructure.persistence;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * 형제 항목의 displayOrder를 목록 순서대로 1..N으로 다시 부여합니다(#324).
 *
 * <p>p_units / p_lessons에는 활성 형제끼리 displayOrder가 겹치지 않도록
 * 부분 UNIQUE 인덱스(V21)가 있습니다. PostgreSQL은 DEFERRABLE이 아닌 UNIQUE 인덱스를
 * 행을 바꿀 때마다 즉시 검사하므로, 3 → 2 처럼 바로 옮기면 아직 2를 쓰고 있는 형제와 충돌합니다.</p>
 *
 * <p>그래서 두 단계로 나눠 flush합니다.</p>
 * <ol>
 *     <li>모든 항목을 서로 겹치지 않는 음수(-1..-N)로 옮긴다 — 기존 양수 값과 충돌하지 않는다.</li>
 *     <li>모든 항목을 최종 값(1..N)으로 옮긴다 — 이 시점에 양수 값을 쓰는 형제가 없다.</li>
 * </ol>
 *
 * <p>값이 바뀌지 않는 항목도 모두 두 번 갱신합니다. 부모 잠금 전에 읽어 둔 엔티티의
 * 1차 캐시 값이 DB와 다를 수 있어도, 모든 행에 실제 UPDATE가 나가도록 하기 위해서입니다.
 * 두 단계는 호출자의 트랜잭션 안에서 실행되므로 중간에 실패하면 함께 롤백됩니다.</p>
 */
final class TwoPhaseDisplayOrderUpdater {

    private TwoPhaseDisplayOrderUpdater() {
    }

    static <T> void reassign(
            List<T> itemsInOrder,
            BiConsumer<T, Integer> changeDisplayOrder,
            Runnable flush
    ) {
        if (itemsInOrder.isEmpty()) {
            return;
        }

        for (int index = 0; index < itemsInOrder.size(); index++) {
            changeDisplayOrder.accept(
                    itemsInOrder.get(index),
                    -(index + 1)
            );
        }

        flush.run();

        for (int index = 0; index < itemsInOrder.size(); index++) {
            changeDisplayOrder.accept(
                    itemsInOrder.get(index),
                    index + 1
            );
        }

        flush.run();
    }
}
