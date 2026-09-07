package com.maesamco.coaching.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Gateway의 통합 Swagger UI 드롭다운에서 서비스를 구분하기 위한 제목 설정. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("매삼코 - Coaching Service")
                .version("v1"));
    }
}