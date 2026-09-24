package com.maesamco.content.application.persistence_service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * 같은 부모 아래 형제 항목(Unit, Lesson)의 순서 이동을 계산합니다(#324).
 *
 * <p>순서 변경 요청의 displayOrder는 "형제들 사이에서 옮겨 갈 자리(1부터 시작)"로 해석합니다.
 * 대상 항목을 목록에서 빼서 목표 자리에 끼워 넣고, 나머지 형제는 상대 순서를 유지한 채 밀리거나 당겨집니다.</p>
 *
 * <pre>
 * [A B C D E], E를 2번 자리로 → [A E B C D]
 * [A B C D E], A를 4번 자리로 → [B C D A E]
 * </pre>
 *
 * <p>DB 반영(1..N 재부여)은 각 Repository의 reorder가 담당합니다.</p>
 */
final class SiblingDisplayOrders {

    private SiblingDisplayOrders() {
    }

    /**
     * 목표 자리가 형제 수 범위(1..size) 안에 있는지 확인합니다.
     */
    static boolean isInRange(
            int position,
            int siblingCount
    ) {
        return position >= 1 && position <= siblingCount;
    }

    /**
     * 대상 항목을 목표 자리로 옮긴 새 순서를 반환합니다. 입력 목록은 변경하지 않습니다.
     *
     * @param siblingsInOrder 현재 순서대로 정렬된 활성 형제 목록 (대상 포함)
     * @param idOf 항목 식별자 추출 함수
     * @param targetId 옮길 항목 식별자
     * @param position 옮겨 갈 자리 (1..size)
     * @return 옮긴 뒤의 순서대로 정렬된 새 목록
     * @throws IllegalArgumentException 대상이 목록에 없거나 자리가 범위를 벗어난 경우
     */
    static <T> List<T> moveTo(
            List<T> siblingsInOrder,
            Function<T, UUID> idOf,
            UUID targetId,
            int position
    ) {
        Objects.requireNonNull(siblingsInOrder, "형제 목록은 필수입니다.");
        Objects.requireNonNull(idOf, "식별자 추출 함수는 필수입니다.");
        Objects.requireNonNull(targetId, "대상 식별자는 필수입니다.");

        if (!isInRange(position, siblingsInOrder.size())) {
            throw new IllegalArgumentException(
                    "옮겨 갈 자리가 형제 수 범위를 벗어났습니다. position="
                            + position + ", size=" + siblingsInOrder.size()
            );
        }

        List<T> reordered = new ArrayList<>(siblingsInOrder);

        T target = null;

        for (int index = 0; index < reordered.size(); index++) {
            if (targetId.equals(idOf.apply(reordered.get(index)))) {
                target = reordered.remove(index);
                break;
            }
        }

        if (target == null) {
            throw new IllegalArgumentException(
                    "형제 목록에 대상 항목이 없습니다. targetId=" + targetId
            );
        }

        reordered.add(position - 1, target);

        return reordered;
    }
}
