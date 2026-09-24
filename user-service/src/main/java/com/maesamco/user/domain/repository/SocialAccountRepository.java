package com.maesamco.user.domain.repository;

import com.maesamco.user.domain.entity.SocialAccount;
import com.maesamco.user.domain.entity.SocialProvider;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * SocialAccount 영속성 기능을 정의하는 도메인 Repository입니다.
 */
public interface SocialAccountRepository {

    /**
     * 소셜 계정을 저장합니다.
     */
    SocialAccount save(SocialAccount socialAccount);

    /**
     * Provider와 Provider 사용자 ID로 소셜 계정을 조회합니다.
     */
    Optional<SocialAccount> findByProviderAndProviderUserId(
            SocialProvider provider,
            String providerUserId
    );

    /**
     * 동일 사용자가 해당 Provider 계정을 이미 가지고 있는지 확인합니다.
     */
    boolean existsByUserIdAndProvider(
            UUID userId,
            SocialProvider provider
    );

    /**
     * 사용자에게 연결된 모든 소셜 계정을 논리 삭제합니다(#328 회원 탈퇴).
     *
     * <p>부분 UNIQUE 인덱스가 {@code deleted_at IS NULL}인 행만 대상으로 하므로,
     * 논리 삭제 후에는 같은 Provider 계정으로 다시 가입할 수 있습니다.</p>
     *
     * @param userId 탈퇴하는 사용자 식별자
     * @param deletedBy 삭제 행위자
     * @param deletedAt 삭제 시각
     * @return 논리 삭제한 소셜 계정 수
     */
    int softDeleteAllByUserId(
            UUID userId,
            UUID deletedBy,
            Instant deletedAt
    );
}
