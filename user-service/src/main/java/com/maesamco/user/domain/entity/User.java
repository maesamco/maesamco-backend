package com.maesamco.user.domain.entity;

import com.maesamco.user.global.common.BaseEntity;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 사용자 정보를 관리하는 User 도메인의 Aggregate Root입니다.
 *
 * <p>이메일 원문은 암호화하여 저장하고, 이메일 중복 확인과 로그인 조회에는
 * 별도로 생성한 HMAC-SHA256 조회 해시를 사용합니다.</p>
 *
 * <p>생성·수정·삭제 감사 정보와 논리 삭제 기능은 {@link BaseEntity}에서 관리합니다.</p>
 */
@Getter
@Entity
@Table(name = "p_users", schema = "user_schema")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    /**
     * HMAC-SHA256 해시를 16진수 문자열로 표현했을 때의 길이입니다.
     */
    private static final int EMAIL_LOOKUP_HASH_LENGTH = 64;

    /**
     * 사용자 식별자입니다.
     */
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * 암호화된 이메일입니다.
     *
     * <p>개인정보 보호를 위해 이메일 원문을 직접 저장하지 않습니다.</p>
     */
    @Column(name = "email", nullable = false, length = 500)
    private String encryptedEmail;

    /**
     * 이메일 검색과 중복 확인에 사용하는 HMAC-SHA256 해시입니다.
     */
    @Column(
            name = "email_lookup_hash",
            nullable = false,
            columnDefinition = "CHAR(64)"
    )
    private String emailLookupHash;

    /**
     * 단방향 해시 처리된 비밀번호입니다.
     */
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    /**
     * 서비스에서 사용하는 사용자 닉네임입니다.
     */
    @Column(name = "nickname", nullable = false, length = 50)
    private String nickname;

    /**
     * 사용자의 서비스 권한입니다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 10)
    private UserRole role;

    /**
     * 사용자 계정의 현재 이용 상태입니다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status;

    /**
     * 사용자가 입력한 Java 학습 또는 사용 경험 개월 수입니다.
     */
    @Column(name = "java_experience_months", nullable = false)
    private int javaExperienceMonths;

    /**
     * 사용자의 현재 Java 학습 수준입니다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "learning_level", nullable = false, length = 20)
    private LearningLevel learningLevel;

    /**
     * 검증이 완료된 값으로 User 객체를 생성합니다.
     */
    private User(
            UUID id,
            String encryptedEmail,
            String emailLookupHash,
            String passwordHash,
            String nickname,
            UserRole role,
            UserStatus status,
            int javaExperienceMonths,
            LearningLevel learningLevel
    ) {
        this.id = requireNonNull(id, "사용자 ID는 필수입니다.");
        this.encryptedEmail = requireText(encryptedEmail, "암호화 이메일은 필수입니다.");
        this.emailLookupHash = validateEmailLookupHash(emailLookupHash);
        this.passwordHash = requireText(passwordHash, "비밀번호 해시는 필수입니다.");
        this.nickname = requireText(nickname, "닉네임은 필수입니다.");
        this.role = requireNonNull(role, "사용자 권한은 필수입니다.");
        this.status = requireNonNull(status, "사용자 상태는 필수입니다.");
        this.javaExperienceMonths =
                validateJavaExperienceMonths(javaExperienceMonths);
        this.learningLevel =
                requireNonNull(learningLevel, "학습 수준은 필수입니다.");
    }

    /**
     * 일반 사용자를 생성합니다.
     *
     * <p>신규 사용자의 기본 권한은 {@link UserRole#USER},
     * 기본 상태는 {@link UserStatus#ACTIVE}입니다.</p>
     *
     * @param encryptedEmail 암호화된 이메일
     * @param emailLookupHash 이메일 조회용 HMAC-SHA256 해시
     * @param passwordHash 단방향 해시 처리된 비밀번호
     * @param nickname 사용자 닉네임
     * @param javaExperienceMonths Java 경험 개월 수
     * @param learningLevel Java 학습 수준
     * @return 생성된 사용자
     */
    public static User create(
            String encryptedEmail,
            String emailLookupHash,
            String passwordHash,
            String nickname,
            int javaExperienceMonths,
            LearningLevel learningLevel
    ) {
        return new User(
                UUID.randomUUID(),
                encryptedEmail,
                emailLookupHash,
                passwordHash,
                nickname,
                UserRole.USER,
                UserStatus.ACTIVE,
                javaExperienceMonths,
                learningLevel
        );
    }

    /**
     * 사용자의 학습 프로필을 수정합니다.
     *
     * @param nickname 변경할 닉네임
     * @param javaExperienceMonths 변경할 Java 경험 개월 수
     * @param learningLevel 변경할 학습 수준
     */
    public void updateProfile(
            String nickname,
            int javaExperienceMonths,
            LearningLevel learningLevel
    ) {
        this.nickname = requireText(nickname, "닉네임은 필수입니다.");
        this.javaExperienceMonths =
                validateJavaExperienceMonths(javaExperienceMonths);
        this.learningLevel =
                requireNonNull(learningLevel, "학습 수준은 필수입니다.");
    }

    /**
     * 사용자의 비밀번호 해시를 변경합니다.
     *
     * @param passwordHash 새롭게 생성된 비밀번호 해시
     */
    public void changePasswordHash(String passwordHash) {
        this.passwordHash =
                requireText(passwordHash, "비밀번호 해시는 필수입니다.");
    }

    /**
     * 사용자 계정을 이용 정지 상태로 변경합니다.
     */
    public void suspend() {
        this.status = UserStatus.SUSPENDED;
    }

    /**
     * 이용 정지된 계정을 정상 상태로 변경합니다.
     */
    public void activate() {
        this.status = UserStatus.ACTIVE;
    }

    /**
     * 이메일 조회 해시가 HMAC-SHA256 문자열 길이와 일치하는지 검증합니다.
     */
    private static String validateEmailLookupHash(String emailLookupHash) {
        String value =
                requireText(emailLookupHash, "이메일 조회 해시는 필수입니다.");

        if (value.length() != EMAIL_LOOKUP_HASH_LENGTH) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "이메일 조회 해시는 64자여야 합니다."
            );
        }

        return value;
    }

    /**
     * Java 경험 개월 수가 음수가 아닌지 검증합니다.
     */
    private static int validateJavaExperienceMonths(
            int javaExperienceMonths
    ) {
        if (javaExperienceMonths < 0) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "Java 학습 개월 수는 0 이상이어야 합니다."
            );
        }

        return javaExperienceMonths;
    }

    /**
     * 필수 객체가 null인지 검증합니다.
     *
     * @param value 검증할 객체
     * @param message null인 경우 사용할 오류 메시지
     * @return 검증이 완료된 객체
     * @param <T> 객체 타입
     */
    private static <T> T requireNonNull(T value, String message) {
        if (value == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    message
            );
        }

        return value;
    }

    /**
     * 필수 문자열이 null 또는 공백인지 검증합니다.
     */
    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    message
            );
        }

        return value;
    }
}