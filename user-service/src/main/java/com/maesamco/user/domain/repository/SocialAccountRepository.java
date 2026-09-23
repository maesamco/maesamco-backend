package com.maesamco.user.domain.repository;

import com.maesamco.user.domain.entity.SocialAccount;
import com.maesamco.user.domain.entity.SocialProvider;

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
}
