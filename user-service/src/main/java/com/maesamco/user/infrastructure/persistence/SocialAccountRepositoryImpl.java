package com.maesamco.user.infrastructure.persistence;

import com.maesamco.user.domain.entity.SocialAccount;
import com.maesamco.user.domain.entity.SocialProvider;
import com.maesamco.user.domain.repository.SocialAccountRepository;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import com.maesamco.user.global.util.DataIntegrityViolations;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * SocialAccountRepository의 JPA 구현체입니다.
 */
@Repository
public class SocialAccountRepositoryImpl
        implements SocialAccountRepository {

    private static final String ACTIVE_PROVIDER_USER_UNIQUE_INDEX =
            "uk_p_social_accounts_active_provider_user";

    private static final String ACTIVE_USER_PROVIDER_UNIQUE_INDEX =
            "uk_p_social_accounts_active_user_provider";

    private final SpringDataSocialAccountRepository
            springDataSocialAccountRepository;

    public SocialAccountRepositoryImpl(
            SpringDataSocialAccountRepository
                    springDataSocialAccountRepository
    ) {
        this.springDataSocialAccountRepository =
                springDataSocialAccountRepository;
    }

    /**
     * 소셜 계정을 저장합니다.
     *
     * <p>DB 부분 UNIQUE 인덱스 두 개가 최종 방어선이며, 위반은 모두
     * {@link ErrorCode#SOCIAL_ACCOUNT_ALREADY_LINKED}로 변환합니다(#308, PR #320 리뷰).</p>
     *
     * <ul>
     *     <li>{@code (provider, provider_user_id)}: 같은 Provider 계정을 여러 사용자에게 연결</li>
     *     <li>{@code (user_id, provider)}: 한 사용자에게 같은 Provider 계정을 두 개 연결
     *     (예: 이후 "기존 사용자에게 소셜 계정 추가 연결" 기능에서 재사용할 때)</li>
     * </ul>
     */
    @Override
    public SocialAccount save(
            SocialAccount socialAccount
    ) {
        try {
            return springDataSocialAccountRepository
                    .saveAndFlush(socialAccount);
        } catch (DataIntegrityViolationException exception) {
            if (
                    DataIntegrityViolations.isUniqueViolation(
                            exception,
                            ACTIVE_PROVIDER_USER_UNIQUE_INDEX
                    )
                            || DataIntegrityViolations.isUniqueViolation(
                            exception,
                            ACTIVE_USER_PROVIDER_UNIQUE_INDEX
                    )
            ) {
                throw new BusinessException(
                        ErrorCode.SOCIAL_ACCOUNT_ALREADY_LINKED
                );
            }

            throw exception;
        }
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

    @Override
    public int softDeleteAllByUserId(
            UUID userId,
            UUID deletedBy,
            Instant deletedAt
    ) {
        List<SocialAccount> socialAccounts =
                springDataSocialAccountRepository.findAllByUserId(
                        userId
                );

        socialAccounts.forEach(
                socialAccount -> socialAccount.softDelete(
                        deletedBy,
                        deletedAt
                )
        );

        if (!socialAccounts.isEmpty()) {
            springDataSocialAccountRepository.saveAllAndFlush(
                    socialAccounts
            );
        }

        return socialAccounts.size();
    }
}
