package com.example.DataPreparationApp.Config;

import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.Key;
import java.util.Base64;

@Configuration
public class JwtConfig {

    @Value("${jwt.secret:QXplcnR5dmNzZHNkZHNmZGZzZGZkc2ZlcnR5dWl1cGlvcGlvcGlvcGl1cGl1cGl1cGl1cGl1cGl1cGl1cA==}")
    private String secret;

    @Value("${jwt.expiration:86400000}") // 24 hours in milliseconds
    private Long expiration;

    @Value("${jwt.refresh-expiration:604800000}") // 7 days in milliseconds
    private Long refreshExpiration;

    @Value("${jwt.issuer:mercure-it}")
    private String issuer;

    /**
     * Creates a secure signing key for JWT tokens
     */
    @Bean
    public Key key() {
        byte[] keyBytes = Base64.getDecoder().decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String getSecret() {
        return secret;
    }

    public Long getExpiration() {
        return expiration;
    }

    public Long getRefreshExpiration() {
        return refreshExpiration;
    }

    public String getIssuer() {
        return issuer;
    }
} 