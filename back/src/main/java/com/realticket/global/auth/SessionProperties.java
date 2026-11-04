package com.realticket.global.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.session")
public record SessionProperties(Duration ttl) {
}
