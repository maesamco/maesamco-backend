package com.maesamco.user.infrastructure.persistence;

import com.maesamco.user.domain.entity.SocialAccount;
import com.maesamco.user.domain.entity.SocialProvider;
import com.maesamco.user.domain.repository.SocialAccountRepository;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import com.maesamco.user.global.util.DataIntegrityViolations;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

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
     * <p>동일 Provider 계정이 동시에 가입되는 경쟁에서는 DB 부분 UNIQUE 인덱스가 최종 방어선이며,
     * 그 위반은 {@link ErrorCode#SOCIAL_ACCOUNT_ALREADY_LINKED}로 변환합니다(#308).</p>
     */
    @Override
    public SocialAccount save(
            SocialAccount socialAccount
    ) {
        try {
            return springDataSocialAccountRepository
                    .saveAndFlush(socialAccount);
        } catch (DataIntegrityViolationException exception) {
            if (DataIntegrityViolations.isUniqueViolation(
                    exception,
                    ACTIVE_PROVIDER_USER_UNIQUE_INDEX
            )) {
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
}
