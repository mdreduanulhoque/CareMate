package com.carebridge.auth;

import com.carebridge.common.PhoneNumberNormalizer;
import com.carebridge.user.Role;
import com.carebridge.user.User;
import com.carebridge.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the protected initial Administrator account from local/environment config.
 * Public endpoints can never register an ADMIN role.
 */
@Component
public class AdminBootstrapSeeder {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapSeeder.class);

    private final AdminBootstrapProperties properties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrapSeeder(
            AdminBootstrapProperties properties,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedAdminIfConfigured() {
        if (!properties.enabled()) {
            return;
        }
        if (properties.mobileNumber() == null || properties.mobileNumber().isBlank()
                || properties.password() == null || properties.password().length() < 8) {
            log.warn("Admin bootstrap is enabled, but mobile number or a valid password (>= 8 chars) is missing.");
            return;
        }

        String normalizedMobile = PhoneNumberNormalizer.normalize(properties.mobileNumber());
        if (userRepository.existsByMobileNumber(normalizedMobile)) {
            return;
        }

        String fullName = (properties.fullName() == null || properties.fullName().isBlank())
                ? "CareBridge Admin"
                : properties.fullName().trim();
        String address = (properties.address() == null || properties.address().isBlank())
                ? "Dhaka, Bangladesh"
                : properties.address().trim();

        User admin = new User(
                fullName,
                normalizedMobile,
                null,
                passwordEncoder.encode(properties.password()),
                address,
                Role.ADMIN
        );
        userRepository.save(admin);
        log.info("Seeded initial protected Administrator account (mobile: {}).", normalizedMobile);
    }
}
