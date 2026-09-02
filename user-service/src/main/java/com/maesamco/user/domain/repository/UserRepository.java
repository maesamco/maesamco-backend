package com.maesamco.user.domain.repository;

import com.maesamco.user.domain.entity.User;

import java.util.Optional;
import java.util.UUID;

/**
 * User 도메인의 영속성 기능을 정의하는 Repository 인터페이스입니다.
 *
 * <p>도메인 계층이 Spring Data JPA와 같은 특정 기술에 직접 의존하지 않도록
 * 필요한 저장 및 조회 기능만 추상화합니다.</p>
 */
public interface UserRepository {

    /**
     * 사용자를 저장하거나 변경 내용을 반영합니다.
     *
     * @param user 저장할 사용자
     * @return 저장된 사용자
     */
    User save(User user);

    /**
     * 사용자 식별자로 사용자를 조회합니다.
     *
     * @param userId 사용자 식별자
     * @return 조회된 사용자, 존재하지 않으면 빈 Optional
     */
    Optional<User> findById(UUID userId);

    /**
     * 이메일 조회용 해시로 사용자를 조회합니다.
     *
     * <p>암호화된 이메일은 동일한 평문이라도 암호문이 달라질 수 있으므로
     * 로그인 조회에는 별도의 조회용 해시를 사용합니다.</p>
     *
     * @param emailLookupHash 이메일 조회용 HMAC-SHA256 해시
     * @return 조회된 사용자, 존재하지 않으면 빈 Optional
     */
    Optional<User> findByEmailLookupHash(String emailLookupHash);

    /**
     * 동일한 이메일 조회용 해시를 사용하는 사용자가 존재하는지 확인합니다.
     *
     * @param emailLookupHash 이메일 조회용 HMAC-SHA256 해시
     * @return 존재하면 true
     */
    boolean existsByEmailLookupHash(String emailLookupHash);

    /**
     * 동일한 닉네임을 사용하는 사용자가 존재하는지 확인합니다.
     *
     * @param nickname 확인할 닉네임
     * @return 존재하면 true
     */
    boolean existsByNickname(String nickname);
}