package com.maesamco.judge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class JudgeServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(JudgeServiceApplication.class, args);
    }
}
