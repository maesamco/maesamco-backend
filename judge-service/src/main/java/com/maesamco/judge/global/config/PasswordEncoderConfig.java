package com.maesamco.judge.global.config;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 비밀번호 해싱은 BCrypt가 아니라 Argon2id를 사용한다 — OWASP Password Storage
 * Cheat Sheet 1순위 권장, Memory-Hard라 GPU/ASIC 병렬 크래킹에 BCrypt보다 강함
 * (게이트웨이 및 인증 보안 설계 2-1절, 팀 컨벤션 15절).
 *
 * 회원가입·로그인 로직을 담당하는 User Service가 실질적으로 이 Bean을 사용하지만,
 * global 템플릿 일부로 4개 서비스에 동일하게 배치해 나중에 다른 서비스가
 * 비밀번호를 다루게 되어도 같은 방식을 그대로 쓸 수 있게 한다.
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }
}
