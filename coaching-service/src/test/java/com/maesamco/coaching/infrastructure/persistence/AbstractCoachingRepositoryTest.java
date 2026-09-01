package com.maesamco.coaching.infrastructure.persistence;

import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 팀 컨벤션 18절 — Repository 통합 테스트는 H2가 아니라 Testcontainers 실제 PostgreSQL로 검증한다.
 *
 * 마이그레이션 도구는 Flyway로 확정됐고(팀 컨벤션 16절, 이슈 #10) V1 베이스라인 스크립트도
 * 이미 도입돼 있다(PR #29). Hibernate가 스키마를 직접 만드는 대신 이 실제 마이그레이션
 * 스크립트로 생성된 스키마를 ddl-auto=validate로 검증하도록 해서, 엔티티 매핑이 실제 운영
 * 스키마와 정확히 일치하는지까지 함께 확인한다. @DataJpaTest는 기본적으로 FlywayAutoConfiguration을
 * 포함하지 않아 @ImportAutoConfiguration으로 명시적으로 가져와야 한다.
 *
 * 이 클래스 선언 + Testcontainers 컨테이너가 Repository 통합 테스트 7개에 거의 동일하게
 * 반복되고 있어서(PR #8 리뷰) 공통 부모 클래스로 추출했다.
 *
 * 컨테이너는 @Container/@Testcontainers가 아니라 static 초기화 블록에서 한 번만 start()한다
 * ("싱글톤 컨테이너" 패턴). static 필드는 상속을 통해 서브클래스 7개가 전부 공유하는데,
 * @Container로 관리하면 JUnit5 Testcontainers 확장이 서브클래스 하나가 끝날 때마다 이
 * 공유 필드의 컨테이너를 stop()해버려서, 그다음 서브클래스가 이미 종료된 컨테이너에 연결을
 * 시도하다 HikariPool 커넥션 타임아웃으로 전부 실패하는 걸 실제로 재현해서 확인했다. 여기서는
 * 한 번 start()한 뒤 별도로 stop()하지 않는다 — Testcontainers의 Ryuk 리소스 리퍼가 테스트
 * JVM 종료 시 컨테이너를 정리해준다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@EnableJpaAuditing
abstract class AbstractCoachingRepositoryTest {

    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

    static {
        postgres.start();
    }
}
