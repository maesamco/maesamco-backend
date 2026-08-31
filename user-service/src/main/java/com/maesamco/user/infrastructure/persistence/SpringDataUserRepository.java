package com.maesamco.user.infrastructure.persistence;

import com.maesamco.user.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA를 이용해 User 엔티티에 접근하는 내부 Repository입니다.
 *
 * <p>이 인터페이스는 인프라 계층 내부에서만 사용하며,
 * 애플리케이션 계층에서는 도메인의 UserRepository를 사용합니다.</p>
 */
public interface SpringDataUserRepository
        extends JpaRepository<User, UUID> {

    /**
     * 이메일 조회용 해시로 사용자를 조회합니다.
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