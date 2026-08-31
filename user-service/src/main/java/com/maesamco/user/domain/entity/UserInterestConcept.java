package com.maesamco.user.domain.entity;

import com.maesamco.user.global.common.BaseEntity;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 사용자가 관심 있는 Java 개념을 관리하는 도메인 엔티티입니다.
 *
 * <p>사용자와 학습 개념 사이의 관심 관계를 별도 테이블로 관리합니다.</p>
 *
 * <p>{@code conceptId}는 Content Service에서 관리하는 개념 식별자입니다.
 * MSA 서비스 경계를 유지하기 위해 Content Service 엔티티와 직접적인
 * JPA 연관관계를 맺지 않고 UUID 값만 저장합니다.</p>
 *
 * <p>생성·수정·삭제 감사 정보와 논리 삭제 기능은
 * {@link BaseEntity}에서 관리합니다.</p>
 */
@Getter
@Entity
@Table(
        name = "p_user_interest_concepts",
        schema = "user_schema"
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserInterestConcept extends BaseEntity {

    /**
     * 사용자 관심 개념 설정 식별자입니다.
     */
    @Id
    @Column(
            name = "id",
            nullable = false,
            updatable = false
    )
    private UUID id;

    /**
     * 관심 개념을 등록한 사용자 식별자입니다.
     *
     * <p>User Service 내부의 사용자 ID를 UUID 값으로 관리합니다.</p>
     */
    @Column(
            name = "user_id",
            nullable = false,
            updatable = false
    )
    private UUID userId;

    /**
     * Content Service에서 관리하는 학습 개념 식별자입니다.
     *
     * <p>다른 서비스의 데이터이므로 물리적인 JPA 연관관계를 설정하지 않습니다.</p>
     */
    @Column(
            name = "concept_id",
            nullable = false,
            updatable = false
    )
    private UUID conceptId;

    /**
     * 검증이 완료된 값으로 사용자 관심 개념 객체를 생성합니다.
     *
     * @param id 사용자 관심 개념 설정 식별자
     * @param userId 사용자 식별자
     * @param conceptId Content Service의 개념 식별자
     */
    private UserInterestConcept(
            UUID id,
            UUID userId,
            UUID conceptId
    ) {
        this.id = requireNonNull(
                id,
                "관심 개념 설정 ID는 필수입니다."
        );
        this.userId = requireNonNull(
                userId,
                "사용자 ID는 필수입니다."
        );
        this.conceptId = requireNonNull(
                conceptId,
                "개념 ID는 필수입니다."
        );
    }

    /**
     * 사용자의 관심 개념을 새롭게 생성합니다.
     *
     * @param userId 사용자 식별자
     * @param conceptId Content Service의 개념 식별자
     * @return 생성된 사용자 관심 개념
     */
    public static UserInterestConcept create(
            UUID userId,
            UUID conceptId
    ) {
        return new UserInterestConcept(
                UUID.randomUUID(),
                userId,
                conceptId
        );
    }

    /**
     * 필수 UUID 값이 null인지 검증합니다.
     *
     * @param value 검증할 UUID 값
     * @param message null인 경우 사용할 오류 메시지
     * @return 검증이 완료된 UUID 값
     */
    private static UUID requireNonNull(
            UUID value,
            String message
    ) {
        if (value == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    message
            );
        }

        return value;
    }
}