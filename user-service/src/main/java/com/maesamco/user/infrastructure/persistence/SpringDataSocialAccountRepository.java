package com.maesamco.user.infrastructure.persistence;

import com.maesamco.user.domain.entity.SocialAccount;
import com.maesamco.user.domain.entity.SocialProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * SocialAccount용 Spring Data JPA Repository입니다.
 */
public interface SpringDataSocialAccountRepository
        extends JpaRepository<SocialAccount, UUID> {

    Optional<SocialAccount> findByProviderAndProviderUserId(
            SocialProvider provider,
            String providerUserId
    );

    boolean existsByUserIdAndProvider(
            UUID userId,
            SocialProvider provider
    );

    List<SocialAccount> findAllByUserId(
            UUID userId
    );
}
