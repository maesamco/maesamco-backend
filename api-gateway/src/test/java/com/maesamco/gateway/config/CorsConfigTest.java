package com.maesamco.gateway.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorsConfigTest {

    @Test
    void parsesMultipleOrigins() {
        List<String> result =
                CorsConfig.parseAllowedOrigins(
                        "http://localhost:3000,https://app.maesamco.com"
                );

        assertEquals(
                List.of(
                        "http://localhost:3000",
                        "https://app.maesamco.com"
                ),
                result
        );
    }

    @Test
    void trimsWhitespaceAroundOrigins() {
        List<String> result =
                CorsConfig.parseAllowedOrigins(
                        " http://localhost:3000 , https://app.maesamco.com "
                );

        assertEquals(
                List.of(
                        "http://localhost:3000",
                        "https://app.maesamco.com"
                ),
                result
        );
    }

    @Test
    void ignoresBlankOriginsBetweenCommas() {
        List<String> result =
                CorsConfig.parseAllowedOrigins(
                        "http://localhost:3000,,https://app.maesamco.com"
                );

        assertEquals(
                List.of(
                        "http://localhost:3000",
                        "https://app.maesamco.com"
                ),
                result
        );
    }

    @Test
    void returnsEmptyListWhenOnlyBlankOriginsAreProvided() {
        List<String> result =
                CorsConfig.parseAllowedOrigins(" , , ");

        assertTrue(result.isEmpty());
    }
}
