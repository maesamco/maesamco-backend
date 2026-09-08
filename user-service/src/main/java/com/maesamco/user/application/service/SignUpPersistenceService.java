package com.maesamco.user.application.service;

import com.maesamco.user.domain.entity.User;
import com.maesamco.user.domain.entity.UserGamificationState;
import com.maesamco.user.domain.repository.UserGamificationStateRepository;
import com.maesamco.user.domain.repository.UserRepository;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원가입 과정의 DB 저장 작업을 담당합니다.
 *
 * <p>User와 초기 게이미피케이션 상태 저장을 하나의 DB 트랜잭션으로 묶습니다.
 * 비밀번호 해싱, JWT 발급, Redis 인증 세션 저장과 같은 작업은
 * 이 트랜잭션에 포함하지 않습니다.</p>
 */
@Service
@RequiredArgsConstructor
public class SignUpPersistenceService {

    private final UserRepository userRepository;

    private final UserGamificationStateRepository
            gamificationStateRepository;

    /**
     * 사용자와 초기 게이미피케이션 상태를
     * 하나의 DB 트랜잭션으로 저장합니다.
     *
     * <p>저장 직전 중복 검사는 사용자에게 구체적인 중복 오류를
     * 가능한 한 빠르게 반환하기 위한 보조 검사입니다. 동시에 들어온
     * 회원가입 요청의 최종적인 중복 방어는 DB의 부분 UNIQUE 인덱스와
     * Repository의 무결성 예외 변환이 담당합니다.</p>
     *
     * @param user 저장할 사용자
     * @return 저장된 사용자
     */
    @Transactional
    public User saveUser(User user) {
        validateNotDuplicated(
                user.getEmailLookupHash(),
                user.getNickname()
        );

        User savedUser =
                userRepository.save(user);

        UserGamificationState gamificationState =
                UserGamificationState.create(
                        savedUser.getId()
                );

        gamificationStateRepository.save(
                gamificationState
        );

        return savedUser;
    }

    /**
     * 이메일 또는 닉네임이
     * 미삭제 사용자와 중복되는지 확인합니다.
     *
     * <p>회원가입 흐름에서는 이메일 암호화와 비밀번호 해싱 전에
     * 이 메서드를 호출하여 명백한 중복 요청에 불필요한 고비용 작업이
     * 수행되지 않도록 합니다.</p>
     *
     * <p>{@link #saveUser(User)}에서도 저장 직전에 다시 호출하지만,
     * 동일 클래스 내부 호출이므로 새로운 read-only 트랜잭션을 시작하지 않고
     * {@code saveUser}의 기존 쓰기 트랜잭션에 참여합니다. 이 두 번째 검사는
     * 사용자에게 구체적인 중복 오류를 조기에 반환하기 위한 보조 검사이며,
     * 동시 가입 경쟁의 최종적인 정합성은 DB UNIQUE 인덱스가 보장합니다.</p>
     *
     * @param emailLookupHash 정규화된 이메일의 조회용 hash
     * @param nickname 중복 여부를 확인할 닉네임
     * @throws BusinessException 이메일 또는 닉네임이 중복된 경우
     */
    @Transactional(readOnly = true)
    public void validateNotDuplicated(
            String emailLookupHash,
            String nickname
    ) {
        if (userRepository.existsByEmailLookupHash(
                emailLookupHash
        )) {
            throw new BusinessException(
                    ErrorCode.USER_DUPLICATE_EMAIL
            );
        }

        if (nickname != null
                && !nickname.isBlank()
                && userRepository.existsByNicknameIgnoreCase(
                nickname
        )) {
            throw new BusinessException(
                    ErrorCode.USER_DUPLICATE_NICKNAME
            );
        }
    }
}
