package com.maesamco.content.domain.entity;

/**
 * 학습 콘텐츠(Curriculum / Unit / Lesson)의 공개 상태입니다(#344 A안: 검수 후 공개).
 *
 * <p>새로 만든 콘텐츠는 {@link #DRAFT}로 시작하고, 관리자가 공개해야 학습자 조회에 포함됩니다.
 * 상위 항목이 비공개이면 하위 항목의 상태값과 관계없이 학습자에게 숨기며,
 * 이 판단은 하위 상태값을 바꾸지 않고 조회 시점에 계산합니다.</p>
 */
public enum ContentStatus {

    /** 작성 중. 관리자만 조회할 수 있습니다. */
    DRAFT,

    /** 공개. 상위 항목도 모두 공개일 때 학습자가 조회할 수 있습니다. */
    PUBLISHED
}
