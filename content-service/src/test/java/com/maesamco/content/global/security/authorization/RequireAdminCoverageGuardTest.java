package com.maesamco.content.global.security.authorization;

import com.maesamco.content.support.ControllerEndpointScanner;
import com.maesamco.content.support.ControllerEndpointScanner.Endpoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 변경 요청(POST/PATCH/PUT/DELETE)이거나 {@code /admin/} 경로인 핸들러가 {@link RequireAdmin} 없이
 * 열려 있지 않은지 지킨다(이슈 #311).
 *
 * <p>{@code @RequireAdmin}은 붙이지 않으면 기본값이 "허용"이라, 새 관리자 API에서 애노테이션을
 * 빠뜨리면 에러도 테스트 실패도 없이 그대로 열린다. 그래서 스프링 컨텍스트 없이 모든
 * {@code @RestController}를 정적으로 스캔해서, 관리자 전용이 아닌 변경 요청은 아래 허용 목록에
 * 사유와 함께 명시적으로 등록하도록 강제한다.</p>
 */
class RequireAdminCoverageGuardTest {

    /** 일반 로그인 사용자가 쓰는 변경 요청. 새로 추가할 때는 관리자 전용이 아닌 이유를 함께 적는다. */
    private static final Set<String> ALLOWED_NON_ADMIN_MUTATIONS = Set.of(
            // 데일리 퀴즈 문항 제출 — 로그인 사용자 본인의 퀴즈 응답
            "POST /api/v1/daily-quiz/{quizAttemptId}/questions/{questionVersionId}/submit"
    );

    private static final Set<String> READ_ONLY_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    @Test
    @DisplayName("스캔이 실제로 컨트롤러 핸들러와 관리자 전용 핸들러를 찾아낸다(빈 스캔으로 가드가 무의미해지는 것 방지)")
    void scanFindsHandlers() {
        List<Endpoint> endpoints = ControllerEndpointScanner.scan();

        assertThat(endpoints).isNotEmpty();
        assertThat(endpoints)
                .filteredOn(Endpoint::requiresAdmin)
                .extracting(Endpoint::signature)
                .contains(
                        "POST /api/v1/contents/units",
                        "DELETE /api/v1/admin/contents/tags/{tagId}",
                        "POST /api/v1/admin/contents/problems/{problemId}/approve"
                );
    }

    @Test
    @DisplayName("변경 요청이거나 /admin/ 경로인 핸들러는 @RequireAdmin이 있거나 허용 목록에 있어야 한다")
    void sensitiveEndpointsMustRequireAdminOrBeAllowListed() {
        List<String> violations = ControllerEndpointScanner.scan().stream()
                // /internal/**은 HMAC 서명 + @AllowedInternalCallers로 보호한다(관리자 개념이 아님).
                .filter(e -> !e.path().startsWith("/internal/"))
                .filter(e -> !READ_ONLY_METHODS.contains(e.httpMethod()) || e.path().contains("/admin/"))
                .filter(e -> !e.requiresAdmin())
                .filter(e -> !ALLOWED_NON_ADMIN_MUTATIONS.contains(e.signature()))
                .map(Endpoint::toString)
                .toList();

        assertThat(violations)
                .as("@RequireAdmin이 없는 변경/관리자 경로 핸들러 — 관리자 전용이면 @RequireAdmin을 붙이고, "
                        + "일반 사용자용이면 ALLOWED_NON_ADMIN_MUTATIONS에 사유와 함께 등록하세요")
                .isEmpty();
    }

    @Test
    @DisplayName("허용 목록에 이미 없어졌거나 @RequireAdmin이 붙은 낡은 항목이 남아 있지 않다")
    void allowListHasNoStaleEntries() {
        List<Endpoint> endpoints = ControllerEndpointScanner.scan();

        for (String signature : ALLOWED_NON_ADMIN_MUTATIONS) {
            assertThat(endpoints)
                    .as("허용 목록 항목 '%s'에 해당하는, @RequireAdmin이 없는 핸들러가 있어야 한다", signature)
                    .anyMatch(e -> e.signature().equals(signature) && !e.requiresAdmin());
        }
    }
}
