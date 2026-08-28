package com.maesamco.coaching;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class CoachingServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CoachingServiceApplication.class, args);
    }
}
