package com.maesamco.user.infrastructure.security.social;

import com.maesamco.user.application.port.EmailCipher;
import com.maesamco.user.application.port.EmailLookupHasher;
import com.maesamco.user.application.port.EmailVerificationSecretGenerator;
import com.maesamco.user.application.port.EmailVerificationSecretHasher;
import com.maesamco.user.application.port.SocialSignupTicket;
import com.maesamco.user.application.port.SocialSignupTokenStore;
import com.maesamco.user.application.port.VerifiedSocialIdentity;
import com.maesamco.user.domain.entity.SocialProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Social Signup Token 발급 시 Redis에 저장되는 귀속 정보를 검증합니다(#308).
 */
@ExtendWith(MockitoExtension.class)
class RedisSocialSignupTokenIssuerTest {

    @Mock
    private EmailVerificationSecretGenerator secretGenerator;

    @Mock
    private EmailVerificationSecretHasher secretHasher;

    @Mock
    private EmailLookupHasher emailLookupHasher;

    @Mock
    private EmailCipher emailCipher;

    @Mock
    private SocialSignupTokenStore socialSignupTokenStore;

    @Test
    @DisplayName(
            "원문 Token은 클라이언트에만 반환하고, Redis에는 Token 해시와 "
                    + "Provider·Google sub·이메일 조회 해시·이메일 암호문을 저장한다"
    )
    void issue_storesVerifiedIdentityAsTicket() {
        // given
        Duration ttl = Duration.ofMinutes(10);

        RedisSocialSignupTokenIssuer issuer =
                new RedisSocialSignupTokenIssuer(
                        secretGenerator,
                        secretHasher,
                        emailLookupHasher,
                        emailCipher,
                        socialSignupTokenStore,
                        new SocialSignupProperties(ttl)
                );

        VerifiedSocialIdentity identity =
                new VerifiedSocialIdentity(
                        SocialProvider.GOOGLE,
                        "google-sub-123",
                        "learner@example.com",
                        true
                );

        when(secretGenerator.generateSignupToken()).thenReturn("raw-token");
        when(secretHasher.hashSignupToken("raw-token")).thenReturn("token-hash");
        when(emailLookupHasher.hash("learner@example.com")).thenReturn("e".repeat(64));
        when(emailCipher.encrypt("learner@example.com")).thenReturn("encrypted-email");

        // when
        String token = issuer.issue(identity);

        // then
        assertThat(token).isEqualTo("raw-token");

        ArgumentCaptor<SocialSignupTicket> ticketCaptor =
                ArgumentCaptor.forClass(SocialSignupTicket.class);

        verify(socialSignupTokenStore)
                .save(eq("token-hash"), ticketCaptor.capture(), eq(ttl));

        SocialSignupTicket ticket = ticketCaptor.getValue();

        assertThat(ticket.provider()).isEqualTo(SocialProvider.GOOGLE);
        assertThat(ticket.providerUserId()).isEqualTo("google-sub-123");
        assertThat(ticket.emailLookupHash()).isEqualTo("e".repeat(64));
        assertThat(ticket.encryptedEmail()).isEqualTo("encrypted-email");

        // 이메일 원문이 로그로 새지 않는다.
        assertThat(ticket.toString())
                .doesNotContain("learner@example.com")
                .doesNotContain("google-sub-123");
    }
}
