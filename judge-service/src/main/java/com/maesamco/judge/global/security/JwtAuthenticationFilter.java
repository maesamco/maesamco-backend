package com.maesamco.judge.global.security;

import com.maesamco.judge.global.exception.BusinessException;
import com.maesamco.judge.global.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.PublicKey;
import java.util.List;
import java.util.UUID;

/**
 * 서비스 자체 JWT 검증 필터.
 *
 * 설계 전제(게이트웨이 및 인증 보안 설계 1절 "JWT 릴레이"):
 *  - Gateway가 이미 서명·만료·즉시무효화(Redis invalidatedAt/세션 블랙리스트)를 확인했고,
 *    원본 JWT를 Authorization 헤더에 그대로 릴레이한다.
 *  - 이 필터는 그 JWT의 서명·만료를 "다시" 확인해 role/sub를 신뢰 가능한 형태로 꺼내는 역할이다.
 *    (서비스 포트가 외부에 노출되지 않는다는 전제 하에, Redis 재조회는 하지 않는다 — 매 요청마다
 *     Redis 왕복을 서비스마다 추가하면 지연만 늘고, 무효화 확인은 이미 Gateway의 책임 영역이다.)
 *  - principal은 커스텀 타입이 아니라 **UUID 그 자체**로 설정한다 — 컨트롤러는
 *    @AuthenticationPrincipal UUID userId로 바로 받고, JpaAuditingConfig의 auditorProvider도
 *    동일한 타입을 기대한다(팀 컨벤션 15절).
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final PublicKey jwtPublicKey;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);

        if (token == null) {
            // 인증 없이도 접근 가능한 경로(공개 API)가 있을 수 있으므로 여기서 막지 않는다.
            // 인증이 필요한 API는 @PreAuthorize("isAuthenticated()") 또는
            // CurrentUser 계열 파라미터가 없으면 400/401로 자연히 막힌다.
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(jwtPublicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            if (!TokenType.ACCESS.name().equals(claims.get("tokenType", String.class))) {
                throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN, "Access Token이 아닙니다.");
            }

            UUID userId = UUID.fromString(claims.getSubject());
            String role = claims.get("role", String.class);

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    userId, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))
            );
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
        } catch (JwtException | IllegalArgumentException | BusinessException e) {
            // 여기서 바로 응답을 끊지 않고 SecurityContext를 비운 채 통과시킨다.
            // 인증이 실제로 필요한 엔드포인트는 이후 인가 단계(@PreAuthorize 등)에서
            // AuthenticationException/AccessDeniedException으로 자연히 401/403 처리된다.
            log.warn("JWT 검증 실패: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            // 스레드 재사용 시 SecurityContext가 다음 요청으로 새는 것을 방지
            SecurityContextHolder.clearContext();
        }
    }

    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (bearer != null && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}
