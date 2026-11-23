package com.carebridge.security;

import com.carebridge.common.PhoneNumberNormalizer;
import com.carebridge.user.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Replaces Spring Boot's default in-memory UserDetailsService so no default user/password is ever generated.
 */
@Service
public class CareBridgeUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CareBridgeUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String mobileNumber) throws UsernameNotFoundException {
        String normalized;
        try {
            normalized = PhoneNumberNormalizer.normalize(mobileNumber);
        } catch (RuntimeException ex) {
            throw new UsernameNotFoundException("User not found.");
        }
        return userRepository.findByMobileNumber(normalized)
                .map(CareBridgeUserPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("User not found."));
    }
}
