package com.maesamco.content.presentation.api_controller;

import com.maesamco.content.application.persistence_service.UnitService;
import com.maesamco.content.global.config.SecurityConfig;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 실제 {@link SecurityConfig}를 그대로 사용해 {@code @RequireAdmin}이 강제되는지 검증한다(이슈 #311).
 *
 * <p>{@code UnitController}는 {@code @PreAuthorize("isAuthenticated()")} 메서드를 갖고 있어
 * 메서드 보안 프록시가 걸리고, {@code UnitApiDocs} 인터페이스를 구현하므로 프록시 방식이
 * JDK 동적 프록시로 결정될 수 있다. 그 경우 구현체 메서드에 붙은 {@code @RequireAdmin}이
 * {@code HandlerMethod}에 보이지 않아 ADMIN 검사가 조용히 무시된다.
 * {@code SecurityConfig}의 {@code @EnableMethodSecurity(proxyTargetClass = true)}가 빠지면
 * 이 테스트가 실패한다.</p>
 */
@WebMvcTest(UnitController.class)
@Import(SecurityConfig.class)
class UnitControllerSecurityTest {

    private static final KeyPair KEY_PAIR = generateKeyPair();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UnitService unitService;

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("jwt.public-key", () -> toPem(KEY_PAIR.getPublic()));
    }

    @Test
    @DisplayName("USER 토큰으로 유닛을 생성하면 403을 반환한다")
    void createUnit_user_returns403() throws Exception {
        String json = """
                {"curriculumId": "%s", "title": "Java", "language": "JAVA", "displayOrder": 1}
                """.formatted(UUID.randomUUID());

        mockMvc.perform(
                        post("/api/v1/contents/units")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("USER"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json)
                )
                .andExpect(status().isForbidden());

        verifyNoInteractions(unitService);
    }

    @Test
    @DisplayName("이슈 #311 — USER 토큰 + 검증 실패 바디여도 400이 아니라 403을 반환한다")
    void createUnit_userWithInvalidBody_returns403() throws Exception {
        String invalidJson = """
                {"title": "Java"}
                """;

        mockMvc.perform(
                        post("/api/v1/contents/units")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("USER"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(invalidJson)
                )
                .andExpect(status().isForbidden());

        verifyNoInteractions(unitService);
    }

    @Test
    @DisplayName("USER 토큰으로 유닛을 삭제하면 403을 반환한다")
    void deleteUnit_user_returns403() throws Exception {
        mockMvc.perform(
                        delete("/api/v1/contents/units/{unitId}", UUID.randomUUID())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("USER"))
                )
                .andExpect(status().isForbidden());

        verifyNoInteractions(unitService);
    }

    @Test
    @DisplayName("ADMIN 토큰이면 검증 실패 바디에 대해 403이 아니라 400을 반환한다(권한은 통과)")
    void createUnit_adminWithInvalidBody_returns400() throws Exception {
        String invalidJson = """
                {"title": "Java"}
                """;

        mockMvc.perform(
                        post("/api/v1/contents/units")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("ADMIN"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(invalidJson)
                )
                .andExpect(status().isBadRequest());
    }

    private static String token(String role) {
        long now = System.currentTimeMillis();

        return Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("role", role)
                .claim("tokenType", "ACCESS")
                .issuedAt(new Date(now))
                .expiration(new Date(now + 60 * 60 * 1000))
                .signWith(KEY_PAIR.getPrivate())
                .compact();
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException("테스트 RSA 키 생성에 실패했습니다.", exception);
        }
    }

    private static String toPem(PublicKey publicKey) {
        String encoded = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8))
                .encodeToString(publicKey.getEncoded());

        return """
                -----BEGIN PUBLIC KEY-----
                %s
                -----END PUBLIC KEY-----
                """.formatted(encoded);
    }
}
