package com.carebridge;

import com.carebridge.auth.AdminBootstrapProperties;
import com.carebridge.security.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({JwtProperties.class, AdminBootstrapProperties.class})
public class CareBridgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(CareBridgeApplication.class, args);
    }
}
