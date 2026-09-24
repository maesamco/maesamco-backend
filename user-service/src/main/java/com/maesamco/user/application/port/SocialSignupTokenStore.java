package com.maesamco.user.application.port;

import java.time.Duration;
import java.util.Optional;

/**
 * 소셜 인증 후 회원가입에 사용하는 일회성 Token 상태를 저장합니다.
 *
 * <p>Token 원문은 저장하지 않고 단방향 해시만 Key로 사용합니다.
 * Value로는 Token에 귀속된 {@link SocialSignupTicket}을 저장합니다(#308).</p>
 */
public interface SocialSignupTokenStore {

    /**
     * 소셜 회원가입 Token과 귀속된 소셜 인증 정보를 저장합니다.
     *
     * @param tokenHash 소셜 회원가입 Token 해시
     * @param ticket Token에 귀속된 소셜 인증 정보
     * @param ttl Token 유효시간
     */
    void save(
            String tokenHash,
            SocialSignupTicket ticket,
            Duration ttl
    );

    /**
     * Token을 소비하지 않고 귀속된 소셜 인증 정보를 조회합니다.
     *
     * <p>닉네임 중복처럼 사용자가 수정 후 재시도할 수 있는 오류 때문에
     * Token이 소모되지 않도록, 최종 소비 전에 사전 검증 용도로만 사용합니다.</p>
     *
     * @param tokenHash 소셜 회원가입 Token 해시
     * @return 유효한 Token이면 귀속된 정보, 없거나 만료됐으면 empty
     */
    Optional<SocialSignupTicket> find(
            String tokenHash
    );

    /**
     * Token을 원자적으로 한 번만 소비하고 귀속된 소셜 인증 정보를 반환합니다.
     *
     * <p>동일 Token으로 동시에 요청해도 하나의 요청만 정보를 받습니다.</p>
     *
     * @param tokenHash 소셜 회원가입 Token 해시
     * @return 소비에 성공하면 귀속된 정보, 이미 사용됐거나 만료됐으면 empty
     */
    Optional<SocialSignupTicket> consume(
            String tokenHash
    );
}
