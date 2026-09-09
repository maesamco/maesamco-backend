package com.maesamco.content.problem.presentation.controller;

import com.maesamco.content.global.config.SecurityConfig;
import com.maesamco.content.problem.application.service.ProblemTagService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProblemTagController.class)
@Import(SecurityConfig.class)
class ProblemTagControllerSecurityTest {

    private static final KeyPair KEY_PAIR =
            generateKeyPair();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProblemTagService problemTagService;

    /**
     * 실제 JWT 인증 필터가 사용할 공개키를
     * 테스트에서 생성한 RSA 공개키로 설정합니다.
     */
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
    @DisplayName(
            "ADMIN 사용자는 문제에 태그를 등록할 수 있다"
    )
    void addTagToProblem_admin_returns201()
            throws Exception {
        // given
        UUID problemId = UUID.randomUUID();
        UUID tagId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        String accessToken =
                createAccessToken(
                        adminId,
                        "ADMIN"
                );

        // when & then
        mockMvc.perform(
                        post(
                                "/api/v1/problems/{problemId}/tags/{tagId}",
                                problemId,
                                tagId
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + accessToken
                                )
                )
                .andExpect(status().isCreated());

        verify(problemTagService)
                .addTagToProblem(
                        problemId,
                        tagId
                );
    }

    @Test
    @DisplayName(
            "USER 사용자가 문제에 태그를 등록하면 403을 반환한다"
    )
    void addTagToProblem_user_returns403()
            throws Exception {
        // given
        UUID problemId = UUID.randomUUID();
        UUID tagId = UUID.randomUUID();

        String accessToken =
                createAccessToken(
                        UUID.randomUUID(),
                        "USER"
                );

        // when & then
        mockMvc.perform(
                        post(
                                "/api/v1/problems/{problemId}/tags/{tagId}",
                                problemId,
                                tagId
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + accessToken
                                )
                )
                .andExpect(status().isForbidden());

        verifyNoInteractions(problemTagService);
    }

    @Test
    @DisplayName(
            "ADMIN 사용자는 문제에서 태그 연결을 제거할 수 있다"
    )
    void removeTagFromProblem_admin_returns200()
            throws Exception {
        // given
        UUID problemId = UUID.randomUUID();
        UUID tagId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        String accessToken =
                createAccessToken(
                        adminId,
                        "ADMIN"
                );

        // when & then
        mockMvc.perform(
                        delete(
                                "/api/v1/problems/{problemId}/tags/{tagId}",
                                problemId,
                                tagId
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + accessToken
                                )
                )
                .andExpect(status().isOk());

        verify(problemTagService)
                .removeTagFromProblem(
                        problemId,
                        tagId
                );
    }

    @Test
    @DisplayName(
            "USER 사용자가 문제에서 태그를 제거하면 403을 반환한다"
    )
    void removeTagFromProblem_user_returns403()
            throws Exception {
        // given
        UUID problemId = UUID.randomUUID();
        UUID tagId = UUID.randomUUID();

        String accessToken =
                createAccessToken(
                        UUID.randomUUID(),
                        "USER"
                );

        // when & then
        mockMvc.perform(
                        delete(
                                "/api/v1/problems/{problemId}/tags/{tagId}",
                                problemId,
                                tagId
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + accessToken
                                )
                )
                .andExpect(status().isForbidden());

        verifyNoInteractions(problemTagService);
    }

    /**
     * 실제 JWT 인증 필터 계약에 맞는 테스트용 Access Token을 생성합니다.
     */
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

        } catch (Exception exception) {
            throw new IllegalStateException(
                    "테스트 RSA 키 생성에 실패했습니다.",
                    exception
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
