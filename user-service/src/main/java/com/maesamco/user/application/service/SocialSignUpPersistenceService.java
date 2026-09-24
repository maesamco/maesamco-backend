package com.maesamco.user.application.service;

import com.maesamco.user.application.port.SocialSignupTicket;
import com.maesamco.user.domain.entity.SocialAccount;
import com.maesamco.user.domain.entity.SocialProvider;
import com.maesamco.user.domain.entity.User;
import com.maesamco.user.domain.repository.SocialAccountRepository;
import com.maesamco.user.domain.repository.UserRepository;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소셜 회원가입 과정의 DB 작업을 담당합니다(#308).
 *
 * <p>User, 초기 게이미피케이션 상태, SocialAccount 연결을 하나의 DB 트랜잭션으로 저장합니다.
 * 어느 하나라도 실패하면 모두 롤백되므로 SocialAccount 없는 소셜 User가 남지 않습니다.
 * JWT 발급과 Redis 인증 세션 저장은 이 트랜잭션에 포함하지 않습니다.</p>
 */
@Service
@RequiredArgsConstructor
public class SocialSignUpPersistenceService {

    private final SignUpPersistenceService signUpPersistenceService;
    private final SocialAccountRepository socialAccountRepository;
    private final UserRepository userRepository;

    /**
     * Token에 귀속된 소셜 계정으로 지금 가입할 수 있는지 확인합니다.
     *
     * <p>Token을 소비하기 전에 호출하는 보조 검사입니다.
     * 동시 가입 경쟁의 최종 정합성은 DB 부분 UNIQUE 인덱스가 보장합니다.</p>
     *
     * @param ticket Token에 귀속된 소셜 인증 정보
     */
    @Transactional(readOnly = true)
    public void validateSignupAvailable(
            SocialSignupTicket ticket
    ) {
        /*
         * 다른 탭·기기에서 같은 Google 계정으로 먼저 가입을 완료한 경우입니다.
         * 이 사용자는 소셜 로그인을 다시 하면 기존 로그인 플로우로 진입합니다.
         */
        if (
                socialAccountRepository
                        .findByProviderAndProviderUserId(
                                ticket.provider(),
                                ticket.providerUserId()
                        )
                        .isPresent()
        ) {
            throw new BusinessException(
                    ErrorCode.SOCIAL_ACCOUNT_ALREADY_LINKED
            );
        }

        /*
         * Token 발급 이후 같은 이메일로 일반 회원가입이 완료된 경우입니다.
         * 소셜 로그인과 동일하게 자동 연결하지 않고 거부합니다.
         */
        if (
                userRepository.existsByEmailLookupHash(
                        ticket.emailLookupHash()
                )
        ) {
            throw new BusinessException(
                    ErrorCode.SOCIAL_SIGNUP_EMAIL_ALREADY_EXISTS
            );
        }
    }

    /**
     * 소셜 사용자와 SocialAccount 연결을 하나의 DB 트랜잭션으로 저장합니다.
     *
     * @param user 저장할 소셜 사용자
     * @param provider 연결할 소셜 인증 제공자
     * @param providerUserId Provider 사용자 고유 ID
     * @return 저장된 사용자
     */
    @Transactional
    public User saveSocialUser(
            User user,
            SocialProvider provider,
            String providerUserId
    ) {
        User savedUser;

        try {
            savedUser =
                    signUpPersistenceService.saveUser(
                            user
                    );
        } catch (BusinessException exception) {
            /*
             * 소셜 가입 경로에서는 이메일 중복을
             * 소셜 로그인과 같은 오류 코드로 응답합니다.
             */
            if (
                    exception.getErrorCode()
                            == ErrorCode.USER_DUPLICATE_EMAIL
            ) {
                throw new BusinessException(
                        ErrorCode.SOCIAL_SIGNUP_EMAIL_ALREADY_EXISTS
                );
            }

            throw exception;
        }

        socialAccountRepository.save(
                SocialAccount.create(
                        savedUser.getId(),
                        provider,
                        providerUserId
                )
        );

        return savedUser;
    }
}
