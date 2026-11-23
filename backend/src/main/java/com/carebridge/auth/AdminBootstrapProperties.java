package com.carebridge.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "carebridge.admin.bootstrap")
public record AdminBootstrapProperties(
        boolean enabled,
        String fullName,
        String mobileNumber,
        String password,
        String address
) {
}
