package com.jawn.ragent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "ragent.jwt")
@Data
public class JwtProperties {
    private String secret;
    private long expiration = 86400000L;
}
