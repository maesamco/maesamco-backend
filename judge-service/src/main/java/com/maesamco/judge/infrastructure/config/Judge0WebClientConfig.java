package com.maesamco.judge.infrastructure.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableConfigurationProperties(Judge0Properties.class)
@RequiredArgsConstructor
public class Judge0WebClientConfig {

    @Bean
    public WebClient judge0WebClient(Judge0Properties properties) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.connectTimeoutMs())
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(properties.responseTimeoutMs(), TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(properties.responseTimeoutMs(), TimeUnit.MILLISECONDS)));

        return WebClient.builder()
                .baseUrl(properties.baseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
