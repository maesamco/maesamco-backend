package com.maesamco.content.global.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 복잡한 조회(동적 검색·페이징·다중 조건)에 QueryDSL을 쓰기 위한 공통 Bean
 * (팀 컨벤션 17절 — 단순 CRUD는 Spring Data JPA, 복잡한 조회만 QueryDSL).
 */
@Configuration
public class QuerydslConfig {

    @PersistenceContext
    private EntityManager entityManager;

    @Bean
    public JPAQueryFactory jpaQueryFactory() {
        return new JPAQueryFactory(entityManager);
    }
}
