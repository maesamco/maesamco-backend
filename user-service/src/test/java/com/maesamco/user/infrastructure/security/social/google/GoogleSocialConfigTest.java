package com.maesamco.user.infrastructure.security.social.google;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertIterableEquals;

class GoogleSocialConfigTest {

    @Test
    void googleIdTokenVerifier_usesConfiguredClientIdAsAudience()
            throws Exception {

        String clientId =
                "test-client-id.apps.googleusercontent.com";

        GoogleSocialProperties properties =
                new GoogleSocialProperties(
                        clientId
                );

        GoogleSocialConfig config =
                new GoogleSocialConfig();

        GoogleIdTokenVerifier verifier =
                config.googleIdTokenVerifier(
                        properties
                );

        assertIterableEquals(
                List.of(clientId),
                verifier.getAudience()
        );
    }
}
