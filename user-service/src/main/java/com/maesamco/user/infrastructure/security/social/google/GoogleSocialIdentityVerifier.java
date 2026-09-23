package com.maesamco.user.infrastructure.security.social.google;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.maesamco.user.application.port.SocialIdentityVerifier;
import com.maesamco.user.application.port.VerifiedSocialIdentity;
import com.maesamco.user.domain.entity.SocialProvider;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Google ID Token을 검증하여 신뢰할 수 있는 Social Identity로 변환합니다.
 *
 * <p>클라이언트가 직접 전달하는 이메일이나 Google 사용자 ID는
 * 신뢰하지 않습니다. 검증이 완료된 ID Token의 Claim만 사용합니다.</p>
 */
@Component
public class GoogleSocialIdentityVerifier
        implements SocialIdentityVerifier {

    private static final JsonFactory JSON_FACTORY =
            GsonFactory.getDefaultInstance();

    private final GoogleIdTokenVerifier verifier;

    public GoogleSocialIdentityVerifier(
            GoogleIdTokenVerifier verifier
    ) {
        this.verifier = verifier;
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.GOOGLE;
    }

    /**
     * Google ID Token을 파싱하고 검증합니다.
     *
     * <p>파싱 실패는 잘못된 Token으로 처리하고,
     * Google 공개키 조회 과정의 I/O 실패는 Provider 장애로 구분합니다.</p>
     */
    @Override
    public VerifiedSocialIdentity verify(
            String credential
    ) {
        if (
                credential == null
                        || credential.isBlank()
        ) {
            throw new BusinessException(
                    ErrorCode.AUTH_INVALID_TOKEN
            );
        }

        GoogleIdToken idToken =
                parseToken(
                        credential
                );

        verifyToken(
                idToken
        );

        GoogleIdToken.Payload payload =
                idToken.getPayload();

        String providerUserId =
                payload.getSubject();

        String email =
                payload.getEmail();

        if (
                providerUserId == null
                        || providerUserId.isBlank()
                        || email == null
                        || email.isBlank()
        ) {
            throw new BusinessException(
                    ErrorCode.AUTH_INVALID_TOKEN
            );
        }

        return new VerifiedSocialIdentity(
                SocialProvider.GOOGLE,
                providerUserId,
                email,
                Boolean.TRUE.equals(
                        payload.getEmailVerified()
                )
        );
    }

    /**
     * 문자열 Token을 Google ID Token 구조로 파싱합니다.
     *
     * <p>이 단계에서는 아직 서명을 신뢰하지 않습니다.</p>
     */
    private GoogleIdToken parseToken(
            String credential
    ) {
        try {
            return GoogleIdToken.parse(
                    JSON_FACTORY,
                    credential
            );
        } catch (
                IOException
                | IllegalArgumentException exception
        ) {
            throw new BusinessException(
                    ErrorCode.AUTH_INVALID_TOKEN
            );
        }
    }

    /**
     * Google 공개키와 Audience를 이용하여 Token을 검증합니다.
     */
    private void verifyToken(
            GoogleIdToken idToken
    ) {
        try {
            boolean verified =
                    verifier.verify(
                            idToken
                    );

            if (!verified) {
                throw new BusinessException(
                        ErrorCode.AUTH_INVALID_TOKEN
                );
            }
        } catch (GeneralSecurityException exception) {
            throw new BusinessException(
                    ErrorCode.AUTH_INVALID_TOKEN
            );
        } catch (IOException exception) {
            /*
             * Google 공개키 다운로드/갱신 과정에서 네트워크 문제가
             * 발생한 경우 사용자 Token 오류로 위장하지 않습니다.
             */
            throw new BusinessException(
                    ErrorCode.SOCIAL_PROVIDER_UNAVAILABLE
            );
        }
    }
}
