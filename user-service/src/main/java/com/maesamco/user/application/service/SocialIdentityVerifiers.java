package com.maesamco.user.application.service;

import com.maesamco.user.application.port.SocialIdentityVerifier;
import com.maesamco.user.domain.entity.SocialProvider;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Spring이 주입한 Provider별 {@link SocialIdentityVerifier}를 조회용 Map으로 변환합니다.
 *
 * <p>소셜 로그인과 소셜 재인증(#328)이 같은 규칙으로 Verifier를 찾도록 공유합니다.</p>
 */
final class SocialIdentityVerifiers {

    private SocialIdentityVerifiers() {
    }

    static Map<SocialProvider, SocialIdentityVerifier> toMap(
            List<SocialIdentityVerifier> verifiers
    ) {
        EnumMap<SocialProvider, SocialIdentityVerifier> verifierMap =
                new EnumMap<>(SocialProvider.class);

        for (SocialIdentityVerifier verifier : verifiers) {
            SocialIdentityVerifier previous =
                    verifierMap.put(
                            verifier.provider(),
                            verifier
                    );

            if (previous != null) {
                throw new IllegalStateException(
                        "동일한 Social Provider의 Verifier가 중복 등록되었습니다: "
                                + verifier.provider()
                );
            }
        }

        return Map.copyOf(verifierMap);
    }
}
