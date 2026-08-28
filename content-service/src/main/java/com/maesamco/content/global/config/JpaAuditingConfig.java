package com.maesamco.content.global.config;

import com.maesamco.content.global.common.BaseEntity;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

/**
 * BaseEntity의 createdBy/updatedBy를 SecurityContext의 principal(UUID)로 채운다.
 * 인증된 사용자가 없는 요청(배치, 시스템 잡)에서는 SYSTEM_ACTOR_ID로 대체한다.
 *
 * ⚠️ JwtAuthenticationFilter가 principal 타입을 UUID로 설정해야 이 클래스가 정상 동작한다.
 *    principal 타입을 다른 걸로 바꾸면 이 auditorProvider가 조용히 깨진다(팀 컨벤션 15절 경고).
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<UUID> auditorProvider() {
        return () -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()
                    || !(authentication.getPrincipal() instanceof UUID userId)) {
                return Optional.of(BaseEntity.SYSTEM_ACTOR_ID);
            }
            return Optional.of(userId);
        };
    }
}
