package com.maesamco.user.application.port;

import java.time.Duration;

/**
 * 이메일 인증 상태를 저장하고 원자적으로 변경하기 위한 Port입니다.
 *
 * <p>Application 계층은 Redis, Lua Script 등의 구현 세부사항을 알지 않습니다.
 * Infrastructure 계층에서 이 계약을 원자적으로 구현합니다.</p>
 */
public interface EmailVerificationStore {

    /**
     * 이메일 인증 challenge를 생성합니다.
     *
     * <p>다음 작업은 하나의 원자 연산으로 처리되어야 합니다.</p>
     *
     * <ul>
     *     <li>재전송 cooldown 확인</li>
     *     <li>요청 횟수 제한 확인 및 증가</li>
     *     <li>인증 코드 해시 저장</li>
     *     <li>challenge TTL 설정</li>
     *     <li>cooldown TTL 설정</li>
     * </ul>
     *
     * @param emailLookupHash 이메일 조회용 해시
     * @param verificationCodeHash 인증 코드 해시
     * @param challengeTtl challenge 만료 시간
     * @param resendCooldown 재전송 제한 시간
     * @param requestLimitWindow 요청 횟수 집계 기간
     * @param maxRequestsPerWindow 집계 기간 내 최대 요청 횟수
     * @return challenge 생성 결과
     */
    ChallengeCreationResult createChallenge(
            String emailLookupHash,
            String verificationCodeHash,
            Duration challengeTtl,
            Duration resendCooldown,
            Duration requestLimitWindow,
            int maxRequestsPerWindow
    );

    /**
     * 인증 코드를 확인하고 성공하면 일회용 회원가입 토큰을 발급 상태로 전환합니다.
     *
     * <p>다음 작업은 하나의 원자 연산으로 처리되어야 합니다.</p>
     *
     * <ul>
     *     <li>challenge 존재 여부 확인</li>
     *     <li>인증 코드 해시 비교</li>
     *     <li>실패 시 시도 횟수 증가</li>
     *     <li>최대 시도 횟수 초과 시 challenge 제거</li>
     *     <li>성공 시 challenge 제거</li>
     *     <li>성공 시 signup token 해시와 이메일 귀속 관계 저장</li>
     * </ul>
     *
     * @param emailLookupHash 이메일 조회용 해시
     * @param verificationCodeHash 사용자가 입력한 인증 코드의 해시
     * @param signupTokenHash 새로 발급할 회원가입 토큰의 해시
     * @param signupTokenTtl 회원가입 토큰 만료 시간
     * @param maxVerificationAttempts 최대 인증 실패 횟수
     * @return 인증 결과
     */
    ConfirmationResult confirmAndIssueSignupToken(
            String emailLookupHash,
            String verificationCodeHash,
            String signupTokenHash,
            Duration signupTokenTtl,
            int maxVerificationAttempts
    );

    /**
     * 회원가입 토큰이 해당 이메일에 발급된 것인지 확인하고 한 번만 소비합니다.
     *
     * <p>비교와 삭제는 반드시 하나의 원자 연산이어야 하며,
     * 두 회원가입 요청이 동시에 같은 토큰을 사용하더라도
     * 하나의 요청만 성공해야 합니다.</p>
     *
     * @param signupTokenHash 회원가입 토큰 해시
     * @param emailLookupHash 회원가입 이메일 조회용 해시
     * @return 토큰 소비 성공 여부
     */
    boolean consumeSignupToken(
            String signupTokenHash,
            String emailLookupHash
    );

    enum ChallengeCreationResult {
        CREATED,
        COOLDOWN_ACTIVE,
        RATE_LIMIT_EXCEEDED
    }

    enum ConfirmationResult {
        VERIFIED,
        INVALID_CODE,
        EXPIRED,
        ATTEMPTS_EXCEEDED
    }
}
