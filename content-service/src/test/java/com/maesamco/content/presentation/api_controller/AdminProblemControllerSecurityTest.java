package com.maesamco.content.presentation.api_controller;

import com.maesamco.content.application.facade.ProblemPublicationFacade;
import com.maesamco.content.global.config.SecurityConfig;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminProblemController.class)
@Import(SecurityConfig.class)
class AdminProblemControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProblemPublicationFacade problemPublicationFacade;

    private static final KeyPair KEY_PAIR = generateKeyPair();

    @DynamicPropertySource
    static void jwtProperties(
            DynamicPropertyRegistry registry
    ) {
        registry.add(
                "jwt.public-key",
                () -> toPem(KEY_PAIR.getPublic())
        );
    }

    @Test
    @DisplayName("유효한 ADMIN Access Token이면 문제 발행 승인에 성공한다")
    void approvePublication_validAdminAccessToken_returns200()
            throws Exception {

        // given
        UUID problemId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        String accessToken = createAccessToken(
                adminId,
                "ADMIN"
        );

        // when & then
        mockMvc.perform(
                        post(
                                "/api/v1/admin/problems/{problemId}/approve",
                                problemId
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + accessToken
                                )
                )
                .andExpect(status().isOk());

        verify(problemPublicationFacade)
                .approvePublication(problemId);
    }

    @Test
    @DisplayName("USER Access Token으로 문제 발행 승인을 요청하면 403을 반환한다")
    void approvePublication_userAccessToken_returns403()
            throws Exception {

        // given
        UUID problemId = UUID.randomUUID();

        String accessToken = createAccessToken(
                UUID.randomUUID(),
                "USER"
        );

        // when & then
        mockMvc.perform(
                        post(
                                "/api/v1/admin/problems/{problemId}/approve",
                                problemId
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + accessToken
                                )
                )
                .andExpect(status().isForbidden());

        verifyNoInteractions(problemPublicationFacade);
    }

    private static String createAccessToken(
            UUID userId,
            String role
    ) {
        long now = System.currentTimeMillis();

        return Jwts.builder()
                .subject(userId.toString())
                .claim("role", role)
                .claim("tokenType", "ACCESS")
                .issuedAt(new Date(now))
                .expiration(
                        new Date(
                                now + 60 * 60 * 1000
                        )
                )
                .signWith(KEY_PAIR.getPrivate())
                .compact();
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator =
                    KeyPairGenerator.getInstance("RSA");

            generator.initialize(2048);

            return generator.generateKeyPair();

        } catch (Exception e) {
            throw new IllegalStateException(
                    "테스트 RSA 키 생성 실패",
                    e
            );
        }
    }

    private static String toPem(
            PublicKey publicKey
    ) {
        String encoded =
                Base64.getMimeEncoder(
                                64,
                                "\n".getBytes(
                                        StandardCharsets.UTF_8
                                )
                        )
                        .encodeToString(
                                publicKey.getEncoded()
                        );

        return """
                -----BEGIN PUBLIC KEY-----
                %s
                -----END PUBLIC KEY-----
                """.formatted(encoded);
    }
}