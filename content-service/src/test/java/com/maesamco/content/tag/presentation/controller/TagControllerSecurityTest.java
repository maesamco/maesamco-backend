package com.maesamco.content.tag.presentation.cotroller;

import com.maesamco.content.global.config.SecurityConfig;
import com.maesamco.content.tag.application.service.TagService;
import com.maesamco.content.tag.presentation.controller.TagController;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TagController.class)
@Import(SecurityConfig.class)
class TagControllerSecurityTest {

    private static final KeyPair KEY_PAIR =
            generateKeyPair();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TagService tagService;

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
            "ADMIN이 태그를 삭제하면 인증 사용자 ID를 서비스에 전달한다"
    )
    void deleteTag_adminPassesAuthenticatedUserId()
            throws Exception {
        // given
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
                                "/api/v1/admin/tags/{tagId}",
                                tagId
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + accessToken
                                )
                )
                .andExpect(status().isOk());

        verify(tagService)
                .deleteTag(
                        tagId,
                        adminId
                );
    }

    @Test
    @DisplayName(
            "USER가 태그를 삭제하면 403을 반환한다"
    )
    void deleteTag_userReturns403()
            throws Exception {
        // given
        UUID tagId = UUID.randomUUID();

        String accessToken =
                createAccessToken(
                        UUID.randomUUID(),
                        "USER"
                );

        // when & then
        mockMvc.perform(
                        delete(
                                "/api/v1/admin/tags/{tagId}",
                                tagId
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + accessToken
                                )
                )
                .andExpect(status().isForbidden());

        verifyNoInteractions(tagService);
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
