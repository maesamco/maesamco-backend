package com.maesamco.user.infrastructure.persistence;

import com.maesamco.user.domain.entity.SocialAccount;
import com.maesamco.user.domain.entity.SocialProvider;
import com.maesamco.user.domain.repository.SocialAccountRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * SocialAccountRepository의 JPA 구현체입니다.
 */
@Repository
public class SocialAccountRepositoryImpl
        implements SocialAccountRepository {

    private final SpringDataSocialAccountRepository
            springDataSocialAccountRepository;

    public SocialAccountRepositoryImpl(
            SpringDataSocialAccountRepository
                    springDataSocialAccountRepository
    ) {
        this.springDataSocialAccountRepository =
                springDataSocialAccountRepository;
    }

    @Override
    public SocialAccount save(
            SocialAccount socialAccount
    ) {
        return springDataSocialAccountRepository
                .saveAndFlush(socialAccount);
    }

    @Override
    public Optional<SocialAccount>
    findByProviderAndProviderUserId(
            SocialProvider provider,
            String providerUserId
    ) {
        return springDataSocialAccountRepository
                .findByProviderAndProviderUserId(
                        provider,
                        providerUserId
                );
    }

    @Override
    public boolean existsByUserIdAndProvider(
            UUID userId,
            SocialProvider provider
    ) {
        return springDataSocialAccountRepository
                .existsByUserIdAndProvider(
                        userId,
                        provider
                );
    }
}
